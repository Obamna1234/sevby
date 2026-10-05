package io.github.obamna1234.sevby

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
 * Main screen. Paste or load a song list (or queue several), choose a folder and a source,
 * press Start. The downloads run in [DownloadService]; this screen only shows [Runner.state],
 * so it can be closed and reopened while a run continues in the background.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var installedVersion = ""

    // What the log already shows (so only new lines are added).
    private var shownRun = -1
    private var shownSongs = 0L
    private var shownDetails = 0L
    private var detailsOpen = false
    private var wasRunning = false
    private var lastItemIndex = -1
    private val songText = SpannableStringBuilder()

    private val pickTxt = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadTxt(uri)
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) setFolder(uri)
    }

    /** Asked once, on the first Start: needed to show the progress notification (Android 13+). */
    private var afterPermission: (() -> Unit)? = null
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("Notifications are off, so there's no progress in the notification shade. Downloads still work.")
        afterPermission?.invoke()
        afterPermission = null
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
        b.loadTxt.setOnClickListener {
            pickTxt.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
        }
        b.chosic.setOnClickListener { openChosic() }
        b.clearSongs.setOnClickListener {
            b.songs.setText("")
            prefs.listName = ""
        }
        b.addToQueue.setOnClickListener { addToQueue() }
        b.clearQueue.setOnClickListener {
            prefs.queue = emptyList()
            refreshQueue()
        }

        // Folder
        showFolder()
        b.chooseFolder.setOnClickListener { pickFolder.launch(null) }

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
        b.retryFailed.setOnClickListener { retryFailed() }
        b.about.setOnClickListener { startActivity(Intent(this, EngineCheckActivity::class.java)) }
        b.updateNow.setOnClickListener { updateYtDlp() }

        // Log box: fold away / details / copy
        setLogCollapsed(prefs.logCollapsed)
        b.logHeader.setOnClickListener { setLogCollapsed(!prefs.logCollapsed) }
        b.toggleDetails.setOnClickListener { setDetailsOpen(!detailsOpen) }
        b.copyLog.setOnClickListener { copyLog() }

        refreshQueue()
        refreshRetry()

        // Show the background run (progress + log), live.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Runner.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Runner.state.value.running) refreshEngineStatus()   // also picks up an update made on the About screen
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ── Engine version + update check ───────────────────────────────────

    /** Warm up the engine, show its version, and (once a day) check whether a newer yt-dlp exists. */
    private fun refreshEngineStatus() {
        lifecycleScope.launch {
            try {
                Engine.init(this@MainActivity)
                val v = Engine.run("--version")
                if (!v.ok) {
                    b.engineStatus.text = "Engine problem – tap About & updates for details"
                    return@launch
                }
                installedVersion = v.out.trim()
                b.engineStatus.text = "Ready · yt-dlp $installedVersion"
                val now = System.currentTimeMillis()
                if (now - prefs.lastUpdateCheck > DAY_MS) {
                    Engine.latestYtDlp()?.let {
                        prefs.latestYtDlp = it
                        prefs.lastUpdateCheck = now
                    }
                }
                showUpdateRow()
            } catch (e: Exception) {
                b.engineStatus.text = "Engine failed to start – tap About & updates for details"
            }
        }
    }

    private fun showUpdateRow() {
        val latest = prefs.latestYtDlp
        val available = latest.isNotEmpty() && installedVersion.isNotEmpty() && Engine.isNewer(latest, installedVersion)
        b.updateRow.visibility = if (available) View.VISIBLE else View.GONE
        if (available) b.updateText.text = "A newer yt-dlp ($latest) is available"
    }

    private fun updateYtDlp() {
        if (Runner.state.value.running) {
            toast("Wait until the downloads finish, then update.")
            return
        }
        b.updateNow.isEnabled = false
        b.updateText.text = "Updating…"
        lifecycleScope.launch {
            val msg = Engine.updateYtDlp(this@MainActivity)
            b.updateNow.isEnabled = true
            toast("yt-dlp: $msg")
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

    private fun loadTxt(uri: Uri) {
        lifecycleScope.launch {
            val name = displayName(uri) ?: "Song list.txt"
            val text = try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            } catch (e: Exception) {
                toast("Could not read $name: ${e.message}")
                return@launch
            }
            b.songs.setText(text.removePrefix("﻿"))
            prefs.listName = SongList.safeFileName(name.substringBeforeLast('.'))
            toast("Loaded $name: ${songs().size} songs")
        }
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    // ── Queue ───────────────────────────────────────────────────────────

    /** The current song box as a queue item (named after the loaded .txt, else "Song list N"). */
    private fun boxAsItem(queue: List<QueueItem>): QueueItem? {
        val list = songs()
        if (list.isEmpty()) return null
        var name = prefs.listName.ifEmpty { "Song list ${queue.size + 1}" }
        val base = name
        var n = 2
        while (queue.any { it.name.equals(name, ignoreCase = true) }) name = "$base ($n)".also { n++ }
        return QueueItem(name, list, subfolder = name)
    }

    private fun addToQueue() {
        if (Runner.state.value.running) return
        val q = prefs.queue
        val item = boxAsItem(q) ?: run { toast("Add some songs first."); return }
        prefs.queue = q + item
        b.songs.setText("")
        prefs.listName = ""
        refreshQueue()
        toast("Added \"${item.name}\" (${item.songs.size} songs) to the queue")
    }

    private fun refreshQueue() {
        val q = prefs.queue
        val running = Runner.state.value.running
        b.queueBox.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
        b.queueTitle.text = "Queue (${q.size})"
        b.clearQueue.isEnabled = !running
        b.queueList.removeAllViews()
        q.forEachIndexed { i, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = "${i + 1}. ${item.name} · ${item.songs.size} songs"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = "✕"
                textSize = 18f
                val removable = !running || i > 0      // the list that is downloading stays
                setTextColor(ContextCompat.getColor(this@MainActivity, if (removable) R.color.accent else R.color.muted))
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad / 2, pad / 2)
                isEnabled = removable
                contentDescription = "Remove ${item.name}"
                setOnClickListener {
                    prefs.queue = prefs.queue.filterIndexed { k, _ -> k != i }
                    refreshQueue()
                }
            })
            b.queueList.addView(row)
        }
        b.addToQueue.isEnabled = !running
        if (!running) b.start.text = if (q.isNotEmpty()) "Start queue (${q.size})" else "Start"
    }

    private fun refreshRetry() {
        val n = prefs.failed.sumOf { it.songs.size }
        b.retryFailed.visibility = if (n > 0 && !Runner.state.value.running) View.VISIBLE else View.GONE
        b.retryFailed.text = "Retry failed ($n)"
    }

    // ── Save folder ─────────────────────────────────────────────────────

    private fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            toast("Android didn't allow SEVBY to keep access to that folder. Try another one.")
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

    // ── Start / Stop / Retry ────────────────────────────────────────────

    private fun onStartPressed() {
        if (Runner.state.value.running) {
            Runner.stop()
            toast("Stopping…")
            return
        }
        var queue = prefs.queue
        val items: List<QueueItem>
        if (queue.isNotEmpty()) {
            // Whatever is still in the song box joins the end of the queue.
            boxAsItem(queue)?.let {
                queue = queue + it
                prefs.queue = queue
                b.songs.setText("")
                prefs.listName = ""
                refreshQueue()
            }
            items = queue
            launchRun(items, fromQueue = true)
            return
        } else {
            val list = songs()
            if (list.isEmpty()) { toast("Add some songs first (one \"Artist - Title\" per line)."); return }
            items = listOf(QueueItem(prefs.listName.ifEmpty { "Song list" }, list, subfolder = null))
        }
        launchRun(items)
    }

    private fun retryFailed() {
        val items = prefs.failed
        if (items.isEmpty() || Runner.state.value.running) return
        launchRun(items)
    }

    private fun launchRun(items: List<QueueItem>, fromQueue: Boolean = false) {
        if (folder() == null) { toast("Choose a save folder first."); return }
        if (!Engine.ready) { toast("The engine is still starting – try again in a few seconds."); return }
        val go = {
            val src = when (prefs.source) {
                Source.BOTH -> "Bandcamp + YouTube"
                Source.BANDCAMP -> "Bandcamp only"
                Source.YOUTUBE -> "YouTube only"
            }
            if (Runner.start(this, Job(items, prefs.folderUri!!, prefs.source, fromQueue))) {
                val songsTotal = items.sumOf { it.songs.size }
                Runner.log("$songsTotal songs in ${items.size} list(s) → ${folder()?.name}  ·  $src" +
                    if (prefs.source != Source.YOUTUBE && prefs.pretendBandcampBlocked) "  (test: pretending Bandcamp is blocked)" else "")
                Runner.log("────────────────────────")
                if (prefs.logCollapsed) setLogCollapsed(false)
            }
        }
        val needAsk = Build.VERSION.SDK_INT >= 33 && !prefs.askedNotifications &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needAsk) {
            prefs.askedNotifications = true
            afterPermission = go
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            go()
        }
    }

    // ── Log box ─────────────────────────────────────────────────────────

    private fun setLogCollapsed(collapsed: Boolean) {
        prefs.logCollapsed = collapsed
        b.logBody.visibility = if (collapsed) View.GONE else View.VISIBLE
        b.logArrow.text = if (collapsed) "▸" else "▾"
    }

    private fun setDetailsOpen(open: Boolean) {
        detailsOpen = open
        b.details.visibility = if (open) View.VISIBLE else View.GONE
        b.songLog.visibility = if (open) View.GONE else View.VISIBLE
        b.toggleDetails.text = if (open) "Song list" else "Details"
        if (open) {
            val s = Runner.state.value
            b.details.text = s.lines.joinToString("\n")
            shownDetails = s.added
        }
        scrollLogToEnd(force = true)
    }

    private fun copyLog() {
        val s = Runner.state.value
        val text = buildString {
            s.songs.forEach { l ->
                val mark = when (l.mark) { Mark.OK -> "✓"; Mark.SKIPPED -> "↷"; Mark.FAILED -> "✗"; Mark.HEADER -> "▸" }
                append("$mark ${l.text}").append(if (l.note.isNotEmpty()) "  ·  ${l.note}" else "").append('\n')
            }
            if (s.summary.isNotEmpty()) append(s.summary).append('\n')
            append("\n── Details ──\n")
            s.lines.forEach { append(it).append('\n') }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SEVBY log", text))
        toast("Log copied")
    }

    /**
     * Keep the newest line in view, scrolling only the log box (never the whole page), and only
     * if the reader was already at the bottom – scrolling up to read stays put.
     */
    private fun scrollLogToEnd(force: Boolean = false) {
        val sv = b.logScroll
        val child = sv.getChildAt(0) ?: return
        val wasAtBottom = sv.scrollY + sv.height >= child.height - BOTTOM_SLACK_PX
        if (force || wasAtBottom) sv.post { sv.scrollTo(0, child.height) }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    /** One tidy line per song: mark, name, and a short note underneath. */
    private fun appendSong(l: SongLine) {
        val start = songText.length
        when (l.mark) {
            Mark.HEADER -> {
                if (songText.isNotEmpty()) songText.append('\n')
                songText.append("▸ ${l.text}\n")
                songText.setSpan(ForegroundColorSpan(color(R.color.accent)), start, songText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                songText.setSpan(StyleSpan(Typeface.BOLD), start, songText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return
            }
            Mark.OK -> songText.append("✓ ")
            Mark.SKIPPED -> songText.append("↷ ")
            Mark.FAILED -> songText.append("✗ ")
        }
        val markColor = when (l.mark) {
            Mark.OK -> color(R.color.accent)
            Mark.FAILED -> ERROR_RED
            else -> color(R.color.muted)
        }
        songText.setSpan(ForegroundColorSpan(markColor), start, start + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val nameStart = songText.length
        songText.append(l.text)
        songText.setSpan(
            ForegroundColorSpan(if (l.mark == Mark.SKIPPED) color(R.color.muted) else color(R.color.text)),
            nameStart, songText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        if (l.note.isNotEmpty()) {
            val noteStart = songText.length
            songText.append("\n    ${l.note}")
            songText.setSpan(ForegroundColorSpan(if (l.mark == Mark.FAILED) ERROR_RED else color(R.color.muted)),
                noteStart, songText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            songText.setSpan(RelativeSizeSpan(0.85f), noteStart, songText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        songText.append('\n')
    }

    // ── Showing the run ─────────────────────────────────────────────────

    /** No Spotify login on Android: Chosic turns a playlist into "Artist - Title" text to paste or load. */
    private fun openChosic() {
        val ok = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHOSIC_URL)))
        }.isSuccess
        if (!ok) { Toast.makeText(this, "No browser found to open Chosic", Toast.LENGTH_LONG).show(); return }
        Toast.makeText(
            this,
            "In Chosic: paste the Spotify playlist link, then copy or download the song list and paste or load it here",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun render(s: RunState) {
        val logAtBottom = b.logScroll.getChildAt(0)?.let { b.logScroll.scrollY + b.logScroll.height >= it.height - BOTTOM_SLACK_PX } ?: true
        // New run: clear the old log.
        if (s.runId != shownRun) {
            shownRun = s.runId
            songText.clear()
            shownSongs = s.songsAdded - s.songs.size
            shownDetails = s.added - s.lines.size
            b.details.text = ""
            if (detailsOpen) b.details.text = s.lines.joinToString("\n").also { shownDetails = s.added }
        }

        // Tidy song list: add only the new lines.
        var grew = false
        val freshSongs = (s.songsAdded - shownSongs).toInt().coerceIn(0, s.songs.size)
        if (freshSongs > 0) {
            s.songs.takeLast(freshSongs).forEach { appendSong(it) }
            shownSongs = s.songsAdded
            grew = true
        }
        if (songText.isEmpty()) {
            b.songLog.text = if (s.running) "" else "Downloaded songs show up here."
            b.songLog.setTextColor(color(R.color.muted))
        } else if (grew) {
            b.songLog.setText(songText, TextView.BufferType.SPANNABLE)
        }

        // Technical details (only while open).
        if (detailsOpen) {
            val freshLines = (s.added - shownDetails).toInt().coerceIn(0, s.lines.size)
            if (freshLines > 0) {
                b.details.append(s.lines.takeLast(freshLines).joinToString("\n", prefix = if (b.details.text.isEmpty()) "" else "\n"))
                shownDetails = s.added
                grew = true
            }
        }

        // The song being worked on right now.
        val current = if (s.running && s.song.isNotEmpty()) "⋯ ${s.song}" + (if (s.step.isNotEmpty()) "\n    ${s.step}" else "") else ""
        if (b.currentSong.text.toString() != current) {
            b.currentSong.text = current
            b.currentSong.visibility = if (current.isEmpty()) View.GONE else View.VISIBLE
            grew = true
        }
        b.summary.text = s.summary
        b.summary.visibility = if (!s.running && s.summary.isNotEmpty()) View.VISIBLE else View.GONE
        if (grew && logAtBottom) scrollLogToEnd(force = true)

        // Log header: a short status even when the log is folded away.
        b.logTitle.text = when {
            s.running && s.paused -> "Log · paused"
            s.running && s.total > 0 -> "Log · ${s.index} / ${s.total}" + if (s.itemCount > 1) "  (list ${s.itemIndex}/${s.itemCount})" else ""
            s.summary.isNotEmpty() -> "Log · ${s.summary}"
            else -> "Log"
        }

        // Buttons and progress.
        b.pause.visibility = if (s.running) View.VISIBLE else View.GONE
        b.pause.text = if (s.paused) "Resume" else "Pause"
        b.progressBox.visibility = if (s.running) View.VISIBLE else View.GONE
        for (v in listOf(b.songs, b.loadTxt, b.chosic, b.clearSongs, b.chooseFolder, b.srcBoth, b.srcBandcamp, b.srcYoutube)) {
            v.isEnabled = !s.running
        }
        if (s.running) {
            b.start.text = "Stop"
            b.progressList.visibility = if (s.itemCount > 1) View.VISIBLE else View.GONE
            b.progressList.text = "List ${s.itemIndex} of ${s.itemCount} · ${s.itemName}"
            if (s.paused) {
                b.progressText.text = "Paused · ${s.index} / ${s.total} · ${s.song}"
                b.progressStep.text = "Tap Resume to carry on"
            } else if (s.total > 0 && s.index > 0) {
                val pct = s.percent
                b.progressText.text = "${s.index} / ${s.total} · ${s.song}" + if (pct != null && pct > 0) "  ${pct.toInt()}%" else ""
                b.progressStep.text = s.step
                val within = ((pct ?: 0f).coerceIn(0f, 100f)) / 100f
                b.progressBar.setProgressCompat((((s.index - 1) + within) / s.total * 1000).toInt(), true)
            } else {
                b.progressText.text = "Starting…"
                b.progressStep.text = ""
                b.progressBar.setProgressCompat(0, false)
            }
        }
        if (s.itemIndex != lastItemIndex) {
            lastItemIndex = s.itemIndex
            refreshQueue()
        }
        if (wasRunning != s.running) {
            wasRunning = s.running
            refreshQueue()
            refreshRetry()
        }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val BOTTOM_SLACK_PX = 120
        private const val CHOSIC_URL = "https://www.chosic.com/spotify-playlist-exporter/"
        private val ERROR_RED = Color.parseColor("#FF6B6B")
    }
}
