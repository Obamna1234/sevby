package io.github.obamna1234.sevby

import org.json.JSONObject

/** One Bandcamp search result (a track). */
data class BcHit(
    val name: String,
    val band: String,
    val url: String,
    val album: String = "",
)

/** Everything SEVBY needs from a Bandcamp track page. */
data class BcTrack(
    val pageUrl: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: String,
    val trackNumber: Int,
    val seconds: Int,
    /** Bandcamp's own 128 kbps MP3 stream; empty if the track can't be streamed. */
    val mp3Url: String,
    val coverUrl: String,
)

/**
 * Bandcamp logic that needs no network or Android (so it can be tested anywhere):
 * picking the right search result and reading a track page.
 */
object BandcampData {

    private val COMPILATION = Regex(
        """(?i)\b(compilation|vol\.?|volume|various|sampler|presents|best of|collection|anthology|\d{2,3})\b"""
    )
    private val VARIANTS = listOf("remix", "live", "instrumental", "acoustic", "cover", "edit", "mix", "version", "karaoke")

    private fun artists(artist: String): List<String> =
        artist.split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bx\b|\band\b)\s*""", RegexOption.IGNORE_CASE))
            .map { Matching.norm(it) }.filter { it.isNotEmpty() }

    private fun variants(title: String) =
        VARIANTS.filter { Matching.words(Matching.cleanTitle(title)).contains(it) }.toSet()

    /** True if [gotTitle] by [gotArtist] is the song asked for (same rules as the iTunes lookup). */
    fun isSameSong(song: String, gotTitle: String, gotArtist: String): Boolean {
        val (artist, title) = SongList.split(song)
        val want = Matching.norm(Matching.cleanTitle(title))
        val got = Matching.norm(Matching.cleanTitle(gotTitle))
        if (want.isEmpty() || got.isEmpty()) return false
        val wantArtists = artists(artist)
        if (wantArtists.isEmpty()) return false
        val gotA = Matching.norm(gotArtist)
        val artistOk = gotA.isNotEmpty() && wantArtists.any { it in gotA || gotA in it }
        return artistOk && got == want && variants(title) == variants(gotTitle)
    }

    /** The subdomain of a Bandcamp page: "https://pylot.bandcamp.com/track/x" → "pylot". */
    private fun host(url: String): String =
        Regex("""https?://([^/.]+)\.""").find(url)?.groupValues?.get(1)?.lowercase().orEmpty()

    /**
     * Matching results, best first: the artist's own Bandcamp page over a label's, real albums
     * over compilations, albums over singles.
     */
    fun choose(hits: List<BcHit>, song: String): List<BcHit> {
        val (artist, _) = SongList.split(song)
        val wantArtists = artists(artist)
        return hits
            .filter { it.url.contains("/track/") && isSameSong(song, it.name, it.band) }
            .sortedByDescending { h ->
                var s = 0
                val sub = Matching.norm(host(h.url))
                if (sub.isNotEmpty() && wantArtists.any { it == sub || it in sub || sub in it }) s += 10
                if (h.album.isNotEmpty() && COMPILATION.containsMatchIn(h.album)) s -= 5
                if (h.album.isNotEmpty() && !COMPILATION.containsMatchIn(h.album)) s += 2
                s
            }
            .distinctBy { it.url }
    }

    /** Results from Bandcamp's search API (both of its reply styles). */
    fun parseSearch(json: String): List<BcHit> {
        val root = JSONObject(json)
        val arr = root.optJSONObject("auto")?.optJSONArray("results") ?: root.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            if (r.optString("type") != "t") return@mapNotNull null
            val url = r.optString("item_url_path").ifEmpty { r.optString("item_url_root") }.ifEmpty { r.optString("url") }
            if (url.isEmpty()) return@mapNotNull null
            BcHit(
                name = r.optString("name"),
                band = r.optString("band_name"),
                url = fixUrl(url),
                album = r.optString("album_name"),
            )
        }
    }

    /** Some Bandcamp links come doubled ("https://x.bandcamp.comhttps://...") – keep the last one. */
    fun fixUrl(url: String): String {
        val i = url.lastIndexOf("https://")
        return if (i > 0) url.substring(i) else url
    }

    /** Pull the data-tralbum / data-embed attributes and og:image out of a page's HTML. */
    fun parseHtml(html: String, pageUrl: String): BcTrack? {
        fun attr(name: String): String? =
            Regex("""data-$name\s*=\s*(["'])(\{.+?\})\1""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?.groupValues?.get(2)?.let { unescape(it) }
        val tralbum = attr("tralbum") ?: return null
        val og = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
        return parsePage(tralbum, attr("embed"), og, pageUrl)
    }

    /** Read a track page's data (the JSON behind data-tralbum, plus data-embed for the album name). */
    fun parsePage(tralbumJson: String, embedJson: String?, ogImage: String?, pageUrl: String): BcTrack? = runCatching {
        val t = JSONObject(tralbumJson)
        val embed = embedJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val current = t.optJSONObject("current") ?: JSONObject()
        val info = t.optJSONArray("trackinfo")?.optJSONObject(0)
        val mp3 = info?.optJSONObject("file")?.optString("mp3-128").orEmpty()
        val title = info?.optString("title").orEmpty().ifEmpty { current.optString("title") }
        val artist = embed?.optString("artist").orEmpty()
            .ifEmpty { current.optString("artist") }.ifEmpty { t.optString("artist") }
        val dates = listOf(current.optString("release_date"), t.optString("album_release_date"), current.optString("publish_date"))
        val year = dates.firstNotNullOfOrNull { Regex("""\b(19|20)\d{2}\b""").find(it)?.value }.orEmpty()
        val artId = t.optLong("art_id", 0L)
        val cover = when {
            artId > 0 -> "https://f4.bcbits.com/img/a${artId}_10.jpg"
            !ogImage.isNullOrEmpty() -> ogImage
            else -> ""
        }
        BcTrack(
            pageUrl = pageUrl,
            title = title,
            artist = artist,
            album = embed?.optString("album_title").orEmpty(),
            year = year,
            trackNumber = info?.optInt("track_num", 0) ?: 0,
            seconds = (info?.optDouble("duration", 0.0) ?: 0.0).toInt(),
            mp3Url = if (mp3.startsWith("//")) "https:$mp3" else mp3,
            coverUrl = cover,
        )
    }.getOrNull()

    /** HTML attribute text → plain text ("&quot;" → '"', "&#39;" → "'", "&amp;" → "&" ...). */
    fun unescape(s: String): String =
        Regex("""&(#x[0-9a-fA-F]+|#\d+|quot|amp|lt|gt|apos);""").replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "quot" -> "\""
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "apos" -> "'"
                else -> if (e.startsWith("#x")) String(Character.toChars(e.substring(2).toInt(16)))
                        else String(Character.toChars(e.substring(1).toInt()))
            }
        }
}
