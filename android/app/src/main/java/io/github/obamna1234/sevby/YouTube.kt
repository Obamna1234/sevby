package io.github.obamna1234.sevby

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

/** Files yt-dlp left in the work folder after a successful download. */
data class Fetched(val mp3: File, val thumbnail: File?, val info: JSONObject?)

/** Hooks the downloader passes in, so Stop can kill the running yt-dlp and progress can be shown. */
class RunControl(
    val log: (String) -> Unit,
    val onStart: (String) -> Unit,
    val onProgress: (Float) -> Unit,
    val isStopped: () -> Boolean,
)

/** YouTube part of SEVBY (port of download_youtube_mp3 in sevby_app.py). */
object YouTube {

    /** Connection dropped / no internet / DNS failure: worth waiting and trying again. */
    fun isNetworkError(err: String): Boolean {
        val e = err.lowercase()
        return listOf(
            "errno 7", "errno -2", "errno -3", "errno 101", "errno 104", "errno 110", "errno 111",
            "name resolution", "no address associated", "network is unreachable", "connection reset",
            "connection refused", "connection aborted", "timed out", "read timeout", "remote end closed",
            "transporterror", "temporarily unavailable",
        ).any { it in e }
    }

    /** Seconds to wait before network retry 1, 2, ... */
    private val NET_WAITS = listOf(5, 15)

    /** Top YouTube results (title, length, channel) without downloading anything. */
    suspend fun search(song: String, ctl: RunControl): List<YtEntry> {
        repeat(1 + NET_WAITS.size) { attempt ->
            if (ctl.isStopped()) return emptyList()
            val r = Engine.run(
                listOf("-J", "--flat-playlist", "--no-warnings", "ytsearch8:$song"),
                onStart = ctl.onStart,
            )
            if (r.ok) {
                val entries = runCatching { JSONObject(r.out).optJSONArray("entries") }.getOrNull()
                    ?: return emptyList()
                return (0 until entries.length()).mapNotNull { i ->
                    val e = entries.optJSONObject(i) ?: return@mapNotNull null
                    val id = e.optString("id")
                    if (id.isEmpty() && e.optString("url").isEmpty()) return@mapNotNull null
                    YtEntry(
                        id = id,
                        url = e.optString("url"),
                        title = e.optString("title"),
                        channel = e.optString("channel").ifEmpty { e.optString("uploader") },
                        duration = if (e.has("duration") && !e.isNull("duration")) e.optDouble("duration") else null,
                    )
                }
            }
            if (r.stopped) return emptyList()
            if (attempt == NET_WAITS.size) return emptyList()
            if (isNetworkError(r.err)) {
                val wait = NET_WAITS[attempt]
                ctl.log("  Network problem – retrying in $wait s…")
                delay(wait * 1000L)
            } else if (attempt == 0) {
                delay(2000)
            } else {
                return emptyList()
            }
        }
        return emptyList()
    }

    /**
     * Find the best match for [song] and download it as a 128 kbps MP3 into [work].
     * Returns null on failure (the reason is already logged).
     */
    suspend fun fetch(song: String, work: File, ctl: RunControl, targetSeconds: Int = 0): Fetched? {
        ctl.log("  Searching YouTube…")
        val ranked = Matching.rank(search(song, ctl), song, targetSeconds)
        if (ctl.isStopped()) return null

        val targets = mutableListOf<String>()
        ranked.take(2).forEachIndexed { i, (score, e) ->
            if (i == 0) {
                val by = if (e.channel.isNotEmpty()) " — ${e.channel}" else ""
                ctl.log("  Match: ${e.title} [${Matching.fmtLen(e.duration)}]$by" + if (score < 20) "  (best guess)" else "")
            }
            targets += e.watchUrl
        }
        targets += "ytsearch1:$song"   // last resort, like the desktop app

        var lastErr = "no result"
        for (target in targets) {
            if (ctl.isStopped()) return null
            var r = download(target, work, ctl)
            if (!r.ok && !r.stopped && ("403" in r.err || "forbidden" in r.err.lowercase())) {
                delay(3000)                     // YouTube sometimes refuses one request; try once more
                r = download(target, work, ctl)
            }
            for (wait in NET_WAITS) {
                if (r.ok || r.stopped || ctl.isStopped() || !isNetworkError(r.err)) break
                ctl.log("  Network problem – retrying in $wait s…")
                delay(wait * 1000L)
                r = download(target, work, ctl)
            }
            if (r.stopped) return null
            if (r.ok) {
                val mp3 = File(work, "sevby.mp3")
                if (mp3.isFile && mp3.length() > 0) {
                    val thumb = listOf("jpg", "webp", "png", "jpeg").map { File(work, "sevby.$it") }.firstOrNull { it.isFile }
                    val info = File(work, "sevby.info.json").takeIf { it.isFile }
                        ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
                    return Fetched(mp3, thumb, info)
                }
                lastErr = "yt-dlp finished but no MP3 was made"
            } else {
                lastErr = r.errorLine
            }
        }
        ctl.log("  FAIL: $lastErr")
        if (isNetworkError(lastErr)) ctl.log("  Tip: the internet connection dropped. Run the list again later to fetch the missing songs.")
        if ("403" in lastErr || "forbidden" in lastErr.lowercase() || "sign in" in lastErr.lowercase()) {
            ctl.log("  Tip: YouTube changes often — tap Engine → Update yt-dlp, then retry.")
        }
        return null
    }

    private suspend fun download(target: String, work: File, ctl: RunControl): RunResult {
        work.listFiles()?.forEach { it.deleteRecursively() }
        return Engine.run(
            listOf(
                "--no-playlist", "--no-warnings", "--no-mtime", "--newline",
                "-f", "bestaudio/best",
                "-x", "--audio-format", "mp3", "--audio-quality", "128K",
                "--write-thumbnail", "--convert-thumbnails", "jpg",
                "--write-info-json", "--no-write-playlist-metafiles",
                "-o", File(work, "sevby.%(ext)s").absolutePath,
                target,
            ),
            onStart = ctl.onStart,
            onProgress = ctl.onProgress,
        )
    }
}
