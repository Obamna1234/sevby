package io.github.obamna1234.sevby

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.obamna1234.sevby.databinding.ActivityEngineCheckBinding
import kotlinx.coroutines.launch

/**
 * Engine check: proves the bundled engine (Python + yt-dlp + FFmpeg) starts on this phone,
 * shows what it can do (e.g. browser impersonation for Bandcamp), and runs one network test.
 */
class EngineCheckActivity : AppCompatActivity() {

    private lateinit var b: ActivityEngineCheckBinding
    private val report = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEngineCheckBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.testInput.setText(YT_EXAMPLE)
        b.presetYoutube.setOnClickListener { b.testInput.setText(YT_EXAMPLE) }
        b.presetBandcamp.setOnClickListener { b.testInput.setText(BC_EXAMPLE) }
        b.runTest.setOnClickListener { runTest() }
        b.copyReport.setOnClickListener { copyReport() }
        b.updateYtdlp.setOnClickListener { updateYtdlp() }
        b.back.setOnClickListener { finish() }

        lifecycleScope.launch { checkEngine() }
    }

    private fun line(s: String = "") {
        report.append(s).append('\n')
        b.report.text = report.toString()
    }

    private suspend fun checkEngine() {
        val pi = packageManager.getPackageInfo(packageName, 0)
        line("SEVBY ${pi.versionName}")
        line("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        line("CPU: ${Build.SUPPORTED_ABIS.joinToString()}")
        line()
        line("Starting engine (first launch unpacks files, can take ~10 s)...")

        val t0 = SystemClock.elapsedRealtime()
        try {
            Engine.init(this)
        } catch (e: Exception) {
            line("ENGINE FAILED TO START: ${e.message}")
            e.cause?.let { line("  cause: ${it.message}") }
            return
        }
        line("Engine started in ${(SystemClock.elapsedRealtime() - t0) / 1000.0} s")

        val ver = Engine.run("--version")
        line("yt-dlp: " + if (ver.ok) ver.out.trim() else "FAILED: ${lastLine(ver.err)}")
        Engine.ageDays(ver.out)?.let { if (it > 60) line("  ($it days old - tap Check for yt-dlp update below)") }

        // '-v' with no link prints yt-dlp's debug header (Python, FFmpeg, optional libraries)
        // and then complains about the missing link, which is expected.
        val dbg = Engine.run("-v")
        val keys = listOf("Python", "exe versions", "Optional libraries", "JS runtimes")
        (dbg.err + "\n" + dbg.out).lines()
            .filter { l -> l.startsWith("[debug]") && keys.any { l.contains(it) } }
            .forEach { line(it.removePrefix("[debug] ")) }

        val imp = Engine.run("--list-impersonate-targets")
        val rows = (imp.out + "\n" + imp.err).lines().filter { it.contains("curl_cffi") }
        val usable = rows.count { !it.contains("unavailable", ignoreCase = true) }
        line()
        line(
            if (usable > 0) "Browser impersonation: YES ($usable targets) - Bandcamp should work"
            else "Browser impersonation: NO (curl_cffi not in this engine) - " +
                "Bandcamp may refuse on some connections (the Bandcamp stage adds a workaround)"
        )
        line()
        line("Engine OK. Try the network test below.")
        b.runTest.isEnabled = true
        b.updateYtdlp.isEnabled = true
    }

    private fun updateYtdlp() {
        b.updateYtdlp.isEnabled = false
        lifecycleScope.launch {
            line()
            line("Checking for a newer yt-dlp...")
            b.updateYtdlp.text = "Checking…"
            val msg = Engine.updateYtDlp(this@EngineCheckActivity)
            line(msg)
            b.updateYtdlp.text = when {
                msg.startsWith("Already") -> "yt-dlp is up to date ✓"
                msg.startsWith("Updated") -> "Updated ✓"
                else -> "Check for yt-dlp update"
            }
            b.updateYtdlp.isEnabled = true
        }
    }

    private fun runTest() {
        val input = b.testInput.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) return
        b.runTest.isEnabled = false
        lifecycleScope.launch {
            line()
            line("TEST: $input")
            val t0 = SystemClock.elapsedRealtime()
            val r = if (input.startsWith("http")) {
                Engine.run(
                    "--simulate", "--no-playlist", "--no-warnings",
                    "--print", "%(title)s | %(artist,uploader)s | %(album|-)s | %(duration>%M:%S|?)s",
                    input,
                )
            } else {
                Engine.run(
                    "--flat-playlist", "--no-warnings",
                    "--print", "%(title)s [%(duration>%M:%S|?)s] - %(channel,uploader)s",
                    "ytsearch3:$input",
                )
            }
            val secs = (SystemClock.elapsedRealtime() - t0) / 1000.0
            if (r.ok && r.out.isNotBlank()) {
                r.out.trim().lines().forEach { line("  $it") }
                line("  OK in $secs s")
            } else {
                val err = lastLine(r.err)
                line("  FAILED in $secs s: $err")
                if (err.contains("tralbum", ignoreCase = true)) {
                    line("  -> Bandcamp blocked the page (bot check). This connection blocks yt-dlp on Bandcamp.")
                    line("     (The Bandcamp stage adds a workaround.)")
                }
            }
            b.runTest.isEnabled = true
        }
    }

    private fun copyReport() {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("SEVBY report", report.toString()))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    private fun lastLine(s: String) =
        s.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: "unknown error"

    companion object {
        const val YT_EXAMPLE = "Perturbator - Future Club"
        const val BC_EXAMPLE = "https://noisia.bandcamp.com/track/found"
    }
}
