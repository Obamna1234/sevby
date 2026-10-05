package io.github.obamna1234.sevby

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the screen and the notification show. */
data class RunState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val index: Int = 0,
    val total: Int = 0,
    val song: String = "",
    val percent: Float? = null,
    val lines: List<String> = emptyList(),
    /** Total lines ever added in this run (keeps counting after old lines are dropped). */
    val added: Long = 0,
    /** Changes on every Start, so the screen knows to clear the old log. */
    val runId: Int = 0,
)

/** One run: which songs, where to, from which source. */
data class Job(val songs: List<String>, val folderUri: String, val source: Source)

/**
 * Shared between the screen and the background service. The service does the work;
 * the screen just shows [state] (so it can be closed and reopened at any time).
 */
object Runner {
    private const val MAX_LINES = 3000

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state

    @Volatile private var pending: Job? = null
    @Volatile internal var downloader: Downloader? = null

    /** Start a run in the background. Returns false if one is already running. */
    fun start(context: Context, job: Job): Boolean {
        if (_state.value.running) return false
        pending = job
        _state.value = RunState(running = true, total = job.songs.size, runId = _state.value.runId + 1)
        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        return true
    }

    fun stop() {
        downloader?.stop()
        _state.update { it.copy(paused = false) }
    }

    fun pause() {
        val d = downloader ?: return
        d.pause()
        _state.update { it.copy(paused = true, percent = null) }
        log("Paused")
        onChange?.invoke()
    }

    fun resume() {
        val d = downloader ?: return
        d.resume()
        _state.update { it.copy(paused = false) }
        log("Resumed")
        onChange?.invoke()
    }

    /** The service listens here to refresh its notification when Pause/Resume is pressed in the app. */
    @Volatile internal var onChange: (() -> Unit)? = null

    internal fun takePending(): Job? = pending.also { pending = null }

    fun log(line: String) = _state.update {
        val lines = if (it.lines.size >= MAX_LINES) it.lines.drop(it.lines.size - MAX_LINES + 1) else it.lines
        it.copy(lines = lines + line, added = it.added + 1)
    }

    fun progress(index: Int, total: Int, song: String, percent: Float?) =
        _state.update { it.copy(index = index, total = total, song = song, percent = percent) }

    internal fun finished() = _state.update { it.copy(running = false, paused = false, percent = null) }
}
