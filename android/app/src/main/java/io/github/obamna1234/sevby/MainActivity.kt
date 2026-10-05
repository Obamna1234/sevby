package io.github.obamna1234.sevby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.obamna1234.sevby.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main screen. Paste or load a song list, choose a save folder and a source, press Start.
 * The downloads run in [DownloadService]; this screen only shows [Runner.state],
 * so it can be closed and reopened while a run continues in the background.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var shownAdded = 0L
    private var shownRun = -1
    private var installedVersion = ""

    private val pickTxt = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadTxt(uri)
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) setFolder(uri)
    }

    /** Asked once, on the first Start: needed to show the progress notification (Android 13+). */
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) log("Notifications are off, so there's no progress bar in the notification shade. Downloads still work.")
        reallyStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        // Song list (remembered between launches)
        b.songs.setText(prefs.draft)
        updateCount()
        b.songs.doAfterTextChanged {
            prefs.draft = it?.toString().orEmpty()
            updateCount()
        }
        b.loadTxt.setOnClickListener { pickTxt.launch(arrayOf("text/*")) }
        b.clearSongs.setOnClickListener {
            b.songs.setText("")
            prefs.listName = ""
        }

        // Folder
        showFolder()
        b.chooseFolder.setOnClickListener { pickFolder.launch(MUSIC_DIR) }

        // Source
        b.sourceGroup.check(
            when (prefs.source) {
                Source.BOTH -> R.id.srcBoth
                Source.BANDCAMP -> R.id.srcBandcamp
                Source.YOUTUBE -> R.id.srcYoutube
            }
        )
        b.sourceGroup.setOnCheckedChangeListener { _, id ->
            prefs.source = when (id) {
                R.id.srcBandcamp -> Source.BANDCAMP
                R.id.srcYoutube -> Source.YOUTUBE
                else -> Source.BOTH
            }
        }

        b.start.setOnClickListener { onStartPressed() }
        b.pause.setOnClickListener { if (Runner.state.value.paused) Runner.resume() else Runner.pause() }
        b.engineCheck.setOnClickListener { startActivity(Intent(this, EngineCheckActivity::class.java)) }
        b.updateNow.setOnClickListener { updateYtDlp() }

        // Show the background run (progress + log), live.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Runner.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Runner.state.value.running) refreshEngineStatus()   // also picks up an update made on the Engine screen
    }

    // ── Engine version + update check ───────────────────────────────────

    /** Warm up the engine, show its version, and (once a day) check whether a newer yt-dlp exists. */
    private fun refreshEngineStatus() {
        lifecycleScope.launch {
            try {
                Engine.init(this@MainActivity)
                val v = Engine.run("--version")
                if (!v.ok) {
                    b.engineStatus.text = "Engine problem – tap Engine for details"
                    return@launch
                }
                installedVersion = v.out.trim()
                b.engineStatus.text = "Engine ready · yt-dlp $installedVersion"
                val now = System.currentTimeMillis()
                if (now - prefs.lastUpdateCheck > DAY_MS) {
                    Engine.latestYtDlp()?.let {
                        prefs.latestYtDlp = it
                        prefs.lastUpdateCheck = now
                    }
                }
                showUpdateRow()
            } catch (e: Exception) {
                b.engineStatus.text = "Engine failed to start – tap Engine for details"
            }
        }
    }

    private fun showUpdateRow() {
        val latest = prefs.latestYtDlp
        val available = latest.isNotEmpty() && installedVersion.isNotEmpty() && Engine.isNewer(latest, installedVersion)
        b.updateRow.visibility = if (available) View.VISIBLE else View.GONE
        if (available) b.updateText.text = "yt-dlp $latest is available"
    }

    private fun updateYtDlp() {
        if (Runner.state.value.running) {
            log("Wait until the downloads finish, then update.")
            return
        }
        b.updateNow.isEnabled = false
        b.updateText.text = "Updating…"
        lifecycleScope.launch {
            val msg = Engine.updateYtDlp(this@MainActivity)
            b.updateNow.isEnabled = true
            log("yt-dlp: $msg")
            refreshEngineStatus()
        }
    }

    // ── Song list ───────────────────────────────────────────────────────

    private fun songs() = SongList.parse(b.songs.text?.toString().orEmpty())

    private fun updateCount() {
        val n = songs().size
        b.songCount.text = when (n) {
            0 -> ""
            1 -> "1 song"
            else -> "$n songs"
        }
    }

    /** A line in the on-screen log that isn't part of a run (hints, errors before Start). */
    private fun log(s: String) {
        Runner.log(s)
    }

    private fun loadTxt(uri: Uri) {
        lifecycleScope.launch {
            val name = displayName(uri) ?: "Song list.txt"
            val text = try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            } catch (e: Exception) {
                log("Could not read $name: ${e.message}")
                return@launch
            }
            b.songs.setText(text.removePrefix("﻿"))
            prefs.listName = SongList.safeFileName(name.substringBeforeLast('.'))
            log("Loaded $name: ${songs().size} songs")
        }
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    // ── Save folder ─────────────────────────────────────────────────────

    private fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            log("Android didn't allow SEVBY to keep access to that folder. Try another one.")
            return
        }
        prefs.folderUri?.let { old ->
            if (old != uri.toString()) runCatching {
                contentResolver.releasePersistableUriPermission(Uri.parse(old), flags)
            }
        }
        prefs.folderUri = uri.toString()
        showFolder()
    }

    /** The chosen folder, if SEVBY still has permission to write there. */
    private fun folder(): DocumentFile? {
        val s = prefs.folderUri ?: return null
        val uri = Uri.parse(s)
        val held = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        if (!held) return null
        return DocumentFile.fromTreeUri(this, uri)?.takeIf { it.canWrite() }
    }

    private fun showFolder() {
        b.folderName.text = folder()?.name ?: "Not chosen – tap Choose"
    }

    // ── Start / Stop ────────────────────────────────────────────────────

    private fun onStartPressed() {
        if (Runner.state.value.running) {
            Runner.stop()
            log("Stopping…")
            return
        }
        if (songs().isEmpty()) { log("Add some songs first (one \"Artist - Title\" per line)."); return }
        if (folder() == null) { log("Choose a save folder first."); return }
        if (!Engine.ready) { log("The engine is still starting – try again in a few seconds."); return }

        val needAsk = Build.VERSION.SDK_INT >= 33 && !prefs.askedNotifications &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needAsk) {
            prefs.askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            reallyStart()
        }
    }

    private fun reallyStart() {
        val list = songs()
        val dir = folder() ?: return
        val src = when (prefs.source) {
            Source.BOTH -> "Bandcamp + YouTube (Bandcamp arrives in a later stage, so YouTube for now)"
            Source.BANDCAMP -> "Bandcamp only (arrives in a later stage)"
            Source.YOUTUBE -> "YouTube only"
        }
        if (!Runner.start(this, Job(list, prefs.folderUri!!, prefs.source))) return
        Runner.log("${list.size} songs → ${dir.name}")
        Runner.log("Source: $src")
        Runner.log("────────────────────────")
    }

    // ── Showing the run ─────────────────────────────────────────────────

    private fun render(s: RunState) {
        // Log: append only the new lines; a new run clears the old log.
        if (s.runId != shownRun) {
            b.log.text = ""
            shownRun = s.runId
            shownAdded = s.added - s.lines.size
        }
        val fresh = (s.added - shownAdded).toInt().coerceIn(0, s.lines.size)
        if (fresh > 0) {
            b.log.append(s.lines.takeLast(fresh).joinToString("\n", postfix = "\n"))
            shownAdded = s.added
        }

        b.start.text = if (s.running) "Stop" else "Start"
        b.pause.visibility = if (s.running) View.VISIBLE else View.GONE
        b.pause.text = if (s.paused) "Resume" else "Pause"
        b.progressBox.visibility = if (s.running) View.VISIBLE else View.GONE
        for (v in listOf(b.songs, b.loadTxt, b.clearSongs, b.chooseFolder, b.srcBoth, b.srcBandcamp, b.srcYoutube)) {
            v.isEnabled = !s.running
        }
        if (s.running && s.paused) {
            b.progressText.text = "Paused · ${s.index} / ${s.total} · ${s.song}"
        } else if (s.running && s.total > 0 && s.index > 0) {
            val pct = s.percent
            val part = if (pct != null && pct > 0) "  ${pct.toInt()}%" else ""
            b.progressText.text = "${s.index} / ${s.total} · ${s.song}$part"
            val within = ((pct ?: 0f).coerceIn(0f, 100f)) / 100f
            b.progressBar.setProgressCompat((((s.index - 1) + within) / s.total * 1000).toInt(), true)
        } else if (s.running) {
            b.progressText.text = "Starting…"
            b.progressBar.setProgressCompat(0, false)
        }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        /** Where the folder picker opens: the phone's Music folder. */
        private val MUSIC_DIR: Uri =
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic")
    }
}
