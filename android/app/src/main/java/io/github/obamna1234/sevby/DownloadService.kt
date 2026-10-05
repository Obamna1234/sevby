package io.github.obamna1234.sevby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the downloads in the background with a notification (progress bar + Stop),
 * so they keep going when the app is minimised or the screen is locked.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotify = 0L
    private var lastIndex = -1
    @Volatile private var done = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { Runner.stop(); return START_NOT_STICKY }
            ACTION_PAUSE -> { Runner.pause(); return START_NOT_STICKY }
            ACTION_RESUME -> { Runner.resume(); return START_NOT_STICKY }
        }
        val job = Runner.takePending()
        createChannels()
        goForeground(progressNotification(0, job?.songs?.size ?: 0, "Starting…", null))
        if (job == null) {
            Runner.finished()
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        scope.launch { run(job) }
        return START_NOT_STICKY
    }

    private suspend fun run(job: Job) {
        var summary = "Stopped"
        try {
            Engine.init(this)
            val folder = DocumentFile.fromTreeUri(this, Uri.parse(job.folderUri))
            if (folder == null || !folder.canWrite()) {
                Runner.log("SEVBY can no longer write to the save folder. Choose it again and press Start.")
                summary = "Couldn't write to the save folder"
                return
            }
            val d = Downloader(this, folder, log = { Runner.log(it) }) { i, n, song, pct ->
                Runner.progress(i, n, song, pct)
                updateProgress(i, n, song, pct)
            }
            Runner.downloader = d
            Runner.onChange = { refreshNotification() }
            val r = d.run(job.songs, job.source)
            Runner.log("────────────────────────")
            Runner.log(
                (if (r.stopped) "Stopped. " else "Done. ") +
                    "${r.ok} downloaded, ${r.skipped} already there, ${r.failed.size} failed."
            )
            if (r.failed.isNotEmpty()) {
                Runner.log("Failed:")
                r.failed.forEach { Runner.log("  ✗ $it") }
            }
            summary = (if (r.stopped) "Stopped · " else "") +
                "${r.ok} downloaded" +
                (if (r.skipped > 0) ", ${r.skipped} already there" else "") +
                (if (r.failed.isNotEmpty()) ", ${r.failed.size} failed" else "")
        } catch (e: Exception) {
            Runner.log("Something went wrong: ${e.message}")
            summary = "Stopped after an error"
        } finally {
            done = true
            Runner.onChange = null
            Runner.downloader = null
            Runner.finished()
            showDone(summary)
            stopSelfCleanly()
        }
    }

    // ── Notifications ───────────────────────────────────────────────────

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the song being downloaded"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a song list has finished"
            }
        )
    }

    private fun builder(channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel)
        else @Suppress("DEPRECATION") Notification.Builder(this)

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code,
        Intent(this, DownloadService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(index: Int, total: Int, song: String, pct: Float?): Notification {
        val paused = Runner.state.value.paused
        val count = if (total > 0 && index > 0) "$index / $total" else ""
        val b = builder(CH_PROGRESS)
            .setSmallIcon(if (paused) android.R.drawable.ic_media_pause else android.R.drawable.stat_sys_download)
            .setContentTitle(if (paused) "Paused $count".trim() else if (count.isNotEmpty()) "Downloading $count" else "SEVBY")
            .setContentText(song)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(
                if (paused) Notification.Action.Builder(null, "Resume", serviceIntent(ACTION_RESUME, 3)).build()
                else Notification.Action.Builder(null, "Pause", serviceIntent(ACTION_PAUSE, 2)).build()
            )
            .addAction(Notification.Action.Builder(null, "Stop", serviceIntent(ACTION_STOP, 1)).build())
        // One bar for the whole list (song 12 of 94 ≈ 12%); the current song's % goes beside it.
        if (total > 0) {
            val within = if (paused) 0f else ((pct ?: 0f).coerceIn(0f, 100f) / 100f)
            val overall = (((index - 1).coerceAtLeast(0) + within) / total * 1000).toInt().coerceIn(0, 1000)
            b.setProgress(1000, overall, false)
            if (!paused && pct != null && pct > 0f) b.setSubText("${pct.toInt()}%")
        } else {
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    /** Redraw the notification now (after Pause / Resume). */
    private fun refreshNotification() {
        if (done) return
        val st = Runner.state.value
        lastNotify = SystemClock.elapsedRealtime()
        getSystemService(NotificationManager::class.java)
            .notify(ID_PROGRESS, progressNotification(st.index, st.total, st.song, st.percent))
    }

    private fun updateProgress(index: Int, total: Int, song: String, pct: Float?) {
        // Android drops updates that come too fast, so: at most ~1 per second, but always on a new song.
        if (done) return   // a late progress update must not bring the notification back
        val now = SystemClock.elapsedRealtime()
        if (index == lastIndex && now - lastNotify < 1000) return
        lastIndex = index
        lastNotify = now
        getSystemService(NotificationManager::class.java)
            .notify(ID_PROGRESS, progressNotification(index, total, song, pct))
    }

    private fun showDone(summary: String) {
        val n = builder(CH_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("SEVBY finished")
            .setContentText(summary)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_DONE, n)
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID_PROGRESS, n)
    }

    // ── Keeping the phone awake during a run ────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sevby:download").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)   // safety limit: 6 hours
        }
    }

    private fun stopSelfCleanly() {
        runCatching { wakeLock?.release() }
        wakeLock = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Runner.stop()
        scope.cancel()
        runCatching { wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.github.obamna1234.sevby.STOP"
        const val ACTION_PAUSE = "io.github.obamna1234.sevby.PAUSE"
        const val ACTION_RESUME = "io.github.obamna1234.sevby.RESUME"
        private const val CH_PROGRESS = "progress"
        private const val CH_DONE = "done"
        private const val ID_PROGRESS = 1
        private const val ID_DONE = 2
    }
}
