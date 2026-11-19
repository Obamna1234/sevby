#!/usr/bin/env bash
# Key rotation for SEVBY's Android signing key (Nov 2026).
#
# Re-signs the release APKs in <dir> so that:
#   - Android 7-8 still see the original SEVBY key (APK Signature Scheme v2), and
#   - Android 9+ see the NEW key together with a proof-of-rotation signed by the original key (v3),
#     so the update installs over the old app normally, and from then on an APK signed only with the
#     original key can no longer update the app (--set-installed-data false).
#
# Needs: OLD_KEYSTORE / OLD_KEY_ALIAS / OLD_KEYSTORE_PASSWORD / OLD_KEY_PASSWORD
#        NEW_KEYSTORE / NEW_KEY_ALIAS / NEW_KEYSTORE_PASSWORD (the new key uses the same password for its key)
# If no new key is set, the APKs are left exactly as Gradle signed them.
# The new key lives only in the protected "release" environment (main + release tags), since 19 Nov 2026.
set -euo pipefail
dir=${1:?usage: sign-rotated.sh <apk folder>}
if [ -z "${NEW_KEYSTORE:-}" ] || [ ! -s "${NEW_KEYSTORE}" ] || [ -z "${OLD_KEYSTORE:-}" ] || [ ! -s "${OLD_KEYSTORE}" ]; then
  echo "Key rotation: no new key set - APKs keep the original key only."
  exit 0
fi
export NEW_KEY_PASSWORD="${NEW_KEY_PASSWORD:-$NEW_KEYSTORE_PASSWORD}"
bt=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
signer="$bt/apksigner"
lineage="${RUNNER_TEMP:-/tmp}/signing.lineage"
old=(--ks "$OLD_KEYSTORE" --ks-key-alias "$OLD_KEY_ALIAS" --ks-pass env:OLD_KEYSTORE_PASSWORD --key-pass env:OLD_KEY_PASSWORD)
new=(--ks "$NEW_KEYSTORE" --ks-key-alias "$NEW_KEY_ALIAS" --ks-pass env:NEW_KEYSTORE_PASSWORD --key-pass env:NEW_KEY_PASSWORD)
"$signer" rotate --out "$lineage" --old-signer "${old[@]}" --set-installed-data false --new-signer "${new[@]}"
shopt -s nullglob
apks=("$dir"/*.apk)
[ ${#apks[@]} -gt 0 ] || { echo "::error::Key rotation: no APKs in $dir"; exit 1; }
for apk in "${apks[@]}"; do
  out="${apk%.apk}.rotated.apk"
  "$signer" sign "${old[@]}" --next-signer "${new[@]}" --lineage "$lineage" \
    --rotation-min-sdk-version 28 --out "$out" "$apk"
  "$signer" verify --min-sdk-version 24 --print-certs "$out" | grep -i "certificate SHA-256" | sort -u
  mv "$out" "$apk"
done
rm -f "$apk.idsig" "$dir"/*.idsig 2>/dev/null || true
# Double-check: Android 9+ must see the new key, Android 7-8 the original one; otherwise fail the build.
cert_sha() { keytool -exportcert -keystore "$1" -alias "$2" -storepass "$3" | sha256sum | cut -d' ' -f1; }
want_new=$(cert_sha "$NEW_KEYSTORE" "$NEW_KEY_ALIAS" "$NEW_KEYSTORE_PASSWORD")
want_old=$(cert_sha "$OLD_KEYSTORE" "$OLD_KEY_ALIAS" "$OLD_KEYSTORE_PASSWORD")
for apk in "${apks[@]}"; do
  "$signer" verify --print-certs --min-sdk-version 28 "$apk" | grep -qi "$want_new" \
    || { echo "::error::$apk: Android 9+ would not see the new key"; exit 1; }
  "$signer" verify --print-certs --min-sdk-version 24 --max-sdk-version 27 "$apk" | grep -qi "$want_old" \
    || { echo "::error::$apk: Android 7-8 would not see the original key"; exit 1; }
done
echo "Key rotation checked: new key for Android 9+, original key for Android 7-8."
echo "Key rotation: ${#apks[@]} APK(s) signed with the original key (Android 7-8) and the new key (Android 9+)."
