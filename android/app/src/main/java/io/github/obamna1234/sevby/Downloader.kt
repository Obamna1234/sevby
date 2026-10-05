package io.github.obamna1234.sevby

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class RunSummary(val ok: Int, val skipped: Int, val failed: List<String>, val stopped: Boolean)

/**
 * Runs one song list, one song at a time, into the chosen folder.
 * YouTube for now; Bandcamp is added in the next stage.
 */
class Downloader(
    private val context: Context,
    private val folder: DocumentFile,
    private val log: (String) -> Unit,
    private val status: (index: Int, total: Int, song: String, percent: Float?) -> Unit,
) {
    @Volatile private var stopped = false
    @Volatile private var paused = false
    @Volatile private var currentId: String? = null

    fun stop() {
        stopped = true
        currentId?.let { Engine.stop(it) }
    }

    /** Pause right away: the current download is cancelled and that song starts again on resume. */
    fun pause() {
        if (paused || stopped) return
        paused = true
        currentId?.let { Engine.stop(it) }
    }

    fun resume() {
        paused = false
    }

    val isPaused: Boolean get() = paused

    private suspend fun waitWhilePaused() {
        while (paused && !stopped) delay(300)
    }

    suspend fun run(songs: List<String>, source: Source): RunSummary {
        var ok = 0
        var skipped = 0
        val failed = mutableListOf<String>()
        // Files already in the folder (name → size), so finished songs are skipped.
        val existing = withContext(Dispatchers.IO) {
            folder.listFiles().mapNotNull { f -> f.name?.lowercase()?.let { it to f } }.toMap().toMutableMap()
        }
        val savedNow = mutableSetOf<String>()   // saved during this run (e.g. a song listed twice)
        val work = File(context.cacheDir, "work").apply { mkdirs() }

        songLoop@ for ((i, song) in songs.withIndex()) {
            waitWhilePaused()
            if (stopped) break
            val name = SongList.safeFileName(song) + ".mp3"
            status(i + 1, songs.size, song, null)
            log("[${i + 1}/${songs.size}] $song")

            if (name.lowercase() in savedNow) {
                log("  SKIP (already in folder)")
                skipped++
                continue
            }
            val old = existing[name.lowercase()]
            if (old != null && old.isFile()) {
                val size = withContext(Dispatchers.IO) { old.length() }
                if (size >= MIN_MP3_BYTES) {
                    log("  SKIP (already in folder)")
                    skipped++
                    continue
                }
                // An empty / cut-off file from an interrupted run: replace it.
                log("  Found an incomplete copy – downloading again")
                withContext(Dispatchers.IO) { runCatching { old.delete() } }
                existing.remove(name.lowercase())
            }
            if (source == Source.BANDCAMP) {
                log("  Bandcamp downloads arrive in the next stage – skipped for now")
                failed += song
                continue
            }

            val ctl = RunControl(
                log = log,
                onStart = { currentId = it },
                onProgress = { p -> status(i + 1, songs.size, song, p) },
                isStopped = { stopped || paused },
            )
            // Album details + real cover from iTunes (also tells us the true length for matching).
            val album = withContext(Dispatchers.IO) { Itunes.lookup(song) }
            if (album != null) {
                val bits = listOfNotNull(album.year.ifEmpty { null }, album.track.takeIf { it > 0 }?.let { "track $it" })
                log("  Album: ${album.album}" + (if (bits.isNotEmpty()) " (${bits.joinToString(", ")})" else "") + " — iTunes")
            } else {
                log("  No album info found on iTunes")
            }

            // Download; if Pause interrupts it, wait and start this song again.
            var got: Fetched?
            while (true) {
                waitWhilePaused()
                if (stopped) break@songLoop
                got = YouTube.fetch(song, work, ctl, album?.seconds ?: 0)
                if (stopped) break@songLoop
                if (paused) {
                    log("  Paused – this song will start again when you resume")
                    status(i + 1, songs.size, song, null)
                    continue
                }
                break
            }
            if (got == null) {
                failed += song
                continue
            }

            val saved = withContext(Dispatchers.IO) { save(got, song, name, album, work) }
            if (saved) {
                savedNow += name.lowercase()
                ok++
                log("  OK → $name")
            } else {
                failed += song
            }
            work.listFiles()?.forEach { it.deleteRecursively() }
        }
        work.listFiles()?.forEach { it.deleteRecursively() }
        return RunSummary(ok, skipped, failed, stopped)
    }

    /** Tag the MP3 and copy it into the user's folder. */
    private fun save(got: Fetched, song: String, name: String, album: AlbumInfo?, work: File): Boolean {
        val tags = tagsFor(song, got.info, album)
        // Real album cover first; the video thumbnail only if there is none.
        val cover = album?.coverUrl?.takeIf { it.isNotEmpty() }?.let { Cover.fromUrl(it, File(work, "cover.download")) }
            ?: got.thumbnail?.let { Cover.squareJpeg(it) }
        val doc = folder.createFile("audio/mpeg", name) ?: run {
            log("  Couldn't create $name in the folder")
            return false
        }
        return try {
            context.contentResolver.openOutputStream(doc.uri)!!.use { out ->
                Id3.writeTagged(got.mp3, tags, cover, out)
            }
            if (cover == null) log("  Note: no cover art could be found for this track.")
            MediaLibrary.announce(context, folder.uri, name) { listed ->
                if (!listed) log("  Note: Android's music library didn't list $name – music apps that scan folders (like VLC) will still find it.")
            }
            true
        } catch (e: Exception) {
            log("  Couldn't save $name: ${e.message}")
            runCatching { doc.delete() }
            false
        }
    }

    companion object {
        /** Anything smaller than this can't be a whole song (128 kbps ≈ 16 KB per second). */
        private const val MIN_MP3_BYTES = 100_000L

        /**
         * Title and artist come from the song list (what the user asked for). Album, year and
         * track number come from iTunes when it found the song, else from YouTube when it knows
         * them (official "Topic" uploads).
         */
        fun tagsFor(song: String, info: JSONObject?, itunes: AlbumInfo? = null): Tags {
            val (listArtist, title) = SongList.split(song)
            val ytArtist = info?.optString("artist").orEmpty()
            val artist = tidyArtists(listArtist.ifEmpty { ytArtist })
            if (itunes != null && itunes.album.isNotEmpty()) {
                val track = when {
                    itunes.track > 0 && itunes.trackCount > 0 -> "${itunes.track}/${itunes.trackCount}"
                    itunes.track > 0 -> "${itunes.track}"
                    else -> ""
                }
                return Tags(title = title, artist = artist, albumArtist = artist, album = itunes.album, year = itunes.year, track = track)
            }
            val album = info?.optString("album").orEmpty()
            var year = ""
            var track = ""
            if (album.isNotEmpty() && info != null) {
                year = info.optInt("release_year", 0).takeIf { it > 0 }?.toString()
                    ?: info.optString("release_date").take(4).takeIf { it.length == 4 }.orEmpty()
                track = info.optInt("track_number", 0).takeIf { it > 0 }?.toString().orEmpty()
            }
            return Tags(title = title, artist = artist, albumArtist = artist, album = album, year = year, track = track)
        }

        /** "Power Glove,PYLOT" → "Power Glove, PYLOT" (Spotify exports leave out the space). */
        fun tidyArtists(s: String): String =
            s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    }
}
