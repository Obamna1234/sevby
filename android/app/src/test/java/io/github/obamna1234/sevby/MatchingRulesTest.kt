package io.github.obamna1234.sevby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Song-matching rules shared with the desktop app (wrong-version bug of October 2026):
 * strict Bandcamp band names, no remakes/tributes/dubs/live albums, no "first hit" YouTube guesses.
 * Run in Android Studio: right-click this file → Run.
 */
class MatchingRulesTest {

    private fun hit(name: String, band: String, sub: String, album: String) =
        BcHit(name, band, "https://$sub.bandcamp.com/track/${name.lowercase().replace(' ', '-')}", album)

    // ── Bandcamp: wrong versions are rejected ───────────────────────────

    @Test fun queenTributeBandRejected_realQueenKept() {
        val tribute = hit("Bohemian Rhapsody", "Queen Mary Band", "queenmaryband", "Live at Southern Tier Brewing")
        val real = hit("Bohemian Rhapsody", "Queen", "queen", "A Night at the Opera")
        assertEquals(listOf(real), BandcampData.choose(listOf(tribute, real), "Queen - Bohemian Rhapsody"))
        assertTrue(BandcampData.choose(listOf(tribute), "Queen - Bohemian Rhapsody").isEmpty())
    }

    @Test fun weekndStudioRemakeRejected() {
        val remake = hit("Blinding Lights", "The Weeknd", "afterhourstildawn", "After Hours Til Dawn (studio remake)")
        assertTrue(BandcampData.choose(listOf(remake), "The Weeknd - Blinding Lights").isEmpty())
        val real = hit("Blinding Lights", "The Weeknd", "theweeknd", "After Hours")
        assertEquals(listOf(real), BandcampData.choose(listOf(remake, real), "The Weeknd - Blinding Lights"))
    }

    @Test fun daftPunkDubsRejected() {
        val dubs = hit("Around the World", "Daft Punk", "eudubs", "EU Dubs")
        assertTrue(BandcampData.choose(listOf(dubs), "Daft Punk - Around the World").isEmpty())
    }

    @Test fun unrelatedBandRejected() {
        val other = hit("Anti-Hero", "ulker", "ulker", "pop music / HOT TOP 2023")
        assertTrue(BandcampData.choose(listOf(other), "Taylor Swift - Anti-Hero").isEmpty())
    }

    @Test fun nearbyNamesAreNotTheSameBand() {
        assertFalse(BandcampData.bandMatches("Queen", "Queen Mary Band"))
        assertFalse(BandcampData.bandMatches("Queen", "Queen Latifah"))
        assertFalse(BandcampData.bandMatches("Taylor Swift", "ulker"))
    }

    // ── Bandcamp: artist-page rule (own page first, other accounts only with the iTunes album) ──

    private fun page(name: String, band: String, url: String, album: String) = BcHit(name, band, url, album)

    @Test fun otherAccountsCreditingTheArtistAreRejected() {
        val cases = listOf(
            Triple("AC/DC - Highway to Hell",
                page("Highway to Hell", "AC/DC", "https://cherrybox.bandcamp.com/track/highway-to-hell", "Full Black (Chronicles of the Abyss) Vol. 3"),
                "Highway to Hell"),
            Triple("Taylor Swift - Anti-Hero",
                page("Anti-Hero", "Taylor Swift", "https://ulker.bandcamp.com/track/anti-hero", "pop music / HOT TOP 2023"),
                "Midnights"),
            Triple("Queen - Bohemian Rhapsody",
                page("Bohemian Rhapsody", "Queen", "https://djrocco.bandcamp.com/track/bohemian-rhapsody", "Club Edits 2024"),
                "A Night at the Opera"),
            Triple("Daft Punk - Around the World",
                page("Around the World", "Daft Punk", "https://steppersclub.bandcamp.com/track/around-the-world", "EU Dubs"),
                "Homework"),
        )
        for ((song, h, itunesAlbum) in cases) {
            assertTrue(song, BandcampData.choose(listOf(h), song, itunesAlbum).isEmpty())
            assertTrue(song, BandcampData.choose(listOf(h), song, null).isEmpty())        // no iTunes album → also rejected
        }
    }

    @Test fun ownArtistPagesAreAccepted() {
        val cases = listOf(
            "Perturbator - Future Club" to page("Future Club", "Perturbator", "https://perturbator.bandcamp.com/track/future-club", "Dangerous Days"),
            "Carpenter Brut - Turbo Killer" to page("Turbo Killer", "Carpenter Brut", "https://carpenterbrut.bandcamp.com/track/turbo-killer", "Turbo Killer"),
            "Mitski - Nobody" to page("Nobody", "Mitski", "https://mitski.bandcamp.com/track/nobody", "Be the Cowboy"),
            "Kevin MacLeod - Monkeys Spinning Monkeys" to page("Monkeys Spinning Monkeys", "Kevin MacLeod", "https://kevinmacleod.bandcamp.com/track/monkeys-spinning-monkeys", ""),
            "Broke For Free - Night Owl" to page("Night Owl", "Broke For Free", "https://brokeforfree.bandcamp.com/track/night-owl", "Directionless EP"),
            "Jahzzar - Siesta" to page("Siesta", "Jahzzar", "https://jahzzar.bandcamp.com/track/siesta", "Traveller's Guide"),
            "Sigur Ros - Hoppipolla" to page("Hoppípolla", "Sigur Rós", "https://store.sigurros.com/track/hopp-polla", "Takk..."),
        )
        for ((song, h) in cases) {
            assertEquals(song, listOf(h), BandcampData.choose(listOf(h), song, null))     // no iTunes needed for own pages
            assertFalse(song, BandcampData.needsItunesAlbum(listOf(h), song))
        }
    }

    @Test fun labelPageAcceptedOnlyWhenAlbumMatchesItunes() {
        val label = page("Asteroid Rain", "Wice", "https://newretrowave.bandcamp.com/track/asteroid-rain", "Asteroid Rain")
        assertTrue(BandcampData.needsItunesAlbum(listOf(label), "Wice - Asteroid Rain"))
        assertEquals(listOf(label), BandcampData.choose(listOf(label), "Wice - Asteroid Rain", "Asteroid Rain"))
        assertTrue(BandcampData.choose(listOf(label), "Wice - Asteroid Rain", "Magnatron 2.0").isEmpty())
        assertTrue(BandcampData.choose(listOf(label), "Wice - Asteroid Rain", null).isEmpty())
        val noAlbum = page("Asteroid Rain", "Wice", "https://newretrowave.bandcamp.com/track/asteroid-rain", "")
        assertTrue(BandcampData.choose(listOf(noAlbum), "Wice - Asteroid Rain", "Asteroid Rain").isEmpty())
    }

    @Test fun ownPageWinsOverLabelPage() {
        val label = page("Future Club", "Perturbator", "https://bloodmusic.bandcamp.com/track/future-club", "Dangerous Days")
        val own = page("Future Club", "Perturbator", "https://perturbator.bandcamp.com/track/future-club", "Dangerous Days")
        assertEquals(listOf(own), BandcampData.choose(listOf(label, own), "Perturbator - Future Club", "Dangerous Days"))
    }

    @Test fun pageAccountReadsSubdomainsAndCustomDomains() {
        assertEquals("perturbator", BandcampData.pageAccount("https://perturbator.bandcamp.com/track/future-club"))
        assertEquals("sigurros", BandcampData.pageAccount("https://store.sigurros.com/track/hopp-polla"))
        assertEquals("example", BandcampData.pageAccount("https://music.example.co.uk/track/x"))
    }

    // ── Bandcamp: real artist pages still match ─────────────────────────

    @Test fun ownPagesStillMatch() {
        val cases = listOf(
            "Carpenter Brut - Turbo Killer" to hit("Turbo Killer", "Carpenter Brut", "carpenterbrut", "Turbo Killer"),
            "Perturbator - Future Club" to hit("Future Club", "Perturbator", "perturbator", "Dangerous Days"),
            "Mitski - Nobody" to hit("Nobody", "Mitski", "mitski", "Be the Cowboy"),
            "Sigur Ros - Hoppipolla" to hit("Hoppípolla", "Sigur Rós", "sigurros", "Takk..."),
            "Kevin MacLeod - Monkeys Spinning Monkeys" to hit("Monkeys Spinning Monkeys", "Kevin MacLeod", "kevinmacleod", "Monkeys Spinning Monkeys"),
            "Jahzzar - Siesta" to hit("Siesta", "Jahzzar", "jahzzar", "Traveller's Guide"),
            "Simon & Garfunkel - The Sound of Silence" to hit("The Sound of Silence", "Simon & Garfunkel", "simongarfunkel", "Wednesday Morning, 3 A.M."),
        )
        for ((song, h) in cases) assertEquals(song, listOf(h), BandcampData.choose(listOf(h), song))
    }

    @Test fun collaborationsAndShortNamesStillMatch() {
        assertTrue(BandcampData.bandMatches("Power Glove,PYLOT", "Power Glove"))
        assertTrue(BandcampData.bandMatches("M.A.D.E.S,Wice", "M.A.D.E.S"))
        assertTrue(BandcampData.bandMatches("Simon & Garfunkel", "Simon and Garfunkel"))
        assertTrue(BandcampData.bandMatches("LXST CXNTURY,Kingpin Skinny Pimp", "LXST CXNTURY"))
    }

    @Test fun liveAndVersionAllowedWhenTheListAsksForThem() {
        assertNotNull(BandcampData.notOriginal("Kataklysm - The Black Sheep", "The Black Sheep", "Live in Deutschland"))
        assertNull(BandcampData.notOriginal("Kataklysm - The Black Sheep (Live)", "The Black Sheep (Live)", "Live in Deutschland"))
        assertNull(BandcampData.notOriginal("VHS Dreams - Vice Point - 2022 Remastered Version", "Vice Point - 2022 Remastered Version", "Vice Point"))
        assertNotNull(BandcampData.notOriginal("Artist - Song", "Song (Piano Version)", "Lullaby Renditions"))
    }

    // ── YouTube: no "first hit" guesses ─────────────────────────────────

    @Test fun madeUpSongMatchesNoVideo() {
        assertEquals(0.0, Matching.titleShare("Notarealsong", "Relaxing Rain Sounds for Sleep 10 Hours"), 0.0)
        assertTrue(Matching.titleShare("Notarealsong", "Zzzz - Fake Video") < 0.5)
    }

    @Test fun realVideosStillPassTheTitleCheck() {
        assertTrue(Matching.titleShare("Bohemian Rhapsody", "Queen – Bohemian Rhapsody (Official Video Remastered)") >= 0.5)
        assertEquals(1.0, Matching.titleShare("In Shadows and Dust", "In Shadows & Dust (Remastered)"), 0.0)
        assertTrue(Matching.titleShare(Matching.cleanTitle("I Like You (feat. DRAM & 6LACK)"), "Childish Major ft. DRAM & 6LACK - I Like You") >= 0.5)
        assertTrue(Matching.titleShare("Sweet Chid O' Mine", "Guns N' Roses - Sweet Child O' Mine (Official Music Video)") >= 0.5)
    }

    @Test fun unrelatedYouTubeResultsGiveNoCandidates() {
        // Nothing left to download means the song fails – there is no "ytsearch1" first-hit fallback any more.
        val unrelated = listOf(
            YtEntry("a", "a", "Relaxing Rain Sounds for Sleep 10 Hours", "Calm", 36000.0),
            YtEntry("b", "b", "Top 10 Funny Cat Videos", "Cats", 600.0),
            YtEntry("c", "c", "Fake Plastic Trees", "Radiohead", 290.0),
        )
        val ranked = Matching.rank(unrelated, "Zzzz Fakeartist - Notarealsong")
        assertTrue(Matching.candidates(ranked, "Zzzz Fakeartist - Notarealsong").isEmpty())

        val real = listOf(YtEntry("q", "q", "Queen – Bohemian Rhapsody (Official Video Remastered)", "Queen Official", 359.0))
        assertEquals(1, Matching.candidates(Matching.rank(real + unrelated, "Queen - Bohemian Rhapsody"), "Queen - Bohemian Rhapsody").size)
    }

    // ── Length check after a Bandcamp download ──────────────────────────

    @Test fun bandcampCopyWithWrongLengthIsRejected() {
        assertTrue(Matching.lengthMismatch(4 * 60.0, 5 * 60 + 55))        // 4:00 file, real song 5:55 → remix, use YouTube
        assertFalse(Matching.lengthMismatch(5 * 60 + 57.0, 5 * 60 + 55))  // 5:57 file → keep
        assertFalse(Matching.lengthMismatch(4 * 60.0, 0))                 // real length unknown → keep
        assertFalse(Matching.lengthMismatch(0.0, 355))                    // file length unknown → keep
        assertTrue(Matching.lengthMismatch(100.0, 120))                   // short songs: more than 12 s off → reject
        assertFalse(Matching.lengthMismatch(110.0, 120))                  // 10 s off → keep
    }

    @Test fun accentsDoNotMatter() {
        assertEquals(Matching.norm("Sigur Ros"), Matching.norm("Sigur Rós"))
    }
}
