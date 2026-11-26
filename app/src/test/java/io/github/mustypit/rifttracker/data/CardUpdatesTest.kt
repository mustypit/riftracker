package io.github.mustypit.rifttracker.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mustypit.rifttracker.game.Battlefields
import io.github.mustypit.rifttracker.game.Cards
import io.github.mustypit.rifttracker.game.Legends
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Card updates from OpenRift with a fake network: checked when the app opens but at most hourly,
 * ETag sent so unchanged data costs a 304, saved and installed, failures keep what's there, card
 * pictures fetched one at a time and kept, new legends' art cropped.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CardUpdatesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sample = javaClass.getResource("/openrift/catalog_sample.json")!!.readBytes()
    private val requests = mutableListOf<Pair<String, Map<String, String>>>()
    private var catalogReply: (Map<String, String>) -> CardUpdates.Response = { CardUpdates.Response(200, sample, "\"e1\"") }

    private fun picture(w: Int, h: Int): ByteArray =
        ByteArrayOutputStream().also { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    @Before
    fun setUp() {
        File(context.filesDir, "openrift").deleteRecursively()
        context.getSharedPreferences("card_updates", Context.MODE_PRIVATE).edit().clear().commit()
        CardUpdates.minLegends = 1; CardUpdates.minCards = 1
        CardUpdates.fetch = { url, headers ->
            requests += url to headers
            when {
                url == CardUpdates.CATALOG_URL -> catalogReply(headers)
                url.endsWith("-full.webp") -> CardUpdates.Response(200, picture(300, 420), null) // a portrait card
                url.endsWith("-400w.webp") -> CardUpdates.Response(200, picture(40, 56), null)
                else -> CardUpdates.Response(404, null, null)
            }
        }
    }

    @After
    fun tearDown() {
        CardUpdates.resetForTests()
        Cards.resetForTests(); Legends.resetForTests(); Battlefields.resetForTests()
    }

    private fun check(now: Long, force: Boolean = false) = runBlocking { CardUpdates.check(context, now, force) }
    private fun catalogRequests() = requests.filter { it.first == CardUpdates.CATALOG_URL }

    @Test
    fun downloadsSavesAndInstallsNewCards() {
        CardUpdates.installStartup(context)
        check(now = 1_000)
        val first = catalogRequests().single()
        assertTrue(first.second.getValue("User-Agent").startsWith("RiftTracker/"))
        assertTrue(first.second.getValue("User-Agent").contains("github.com/mustypit/riftracker"))
        assertFalse("no ETag before there's data", "If-None-Match" in first.second)
        // Installed now, and saved for the next start.
        assertNotNull(Cards.bySlug("chemtech-enforcer"))
        assertNotNull(Legends.byName("Nova"))
        assertEquals(1_000L, CardUpdates.status.value.updatedAt)
        assertEquals(1, CardUpdates.version.value)
        assertTrue(File(context.filesDir, "openrift/cards.json").exists())
        // A brand-new legend's art was cropped from its card and kept (one per new legend, no bulk).
        assertNotNull(CardUpdates.art(context, "legends", "nova"))
        // Next start: the saved data, without the network.
        Cards.resetForTests(); Legends.resetForTests()
        CardUpdates.installStartup(context)
        assertNotNull(Cards.bySlug("chemtech-enforcer"))
        assertEquals(1_000L, CardUpdates.status.value.updatedAt)
    }

    @Test
    fun checksAtMostHourlyAndSendsTheEtag() {
        check(now = 1_000)
        check(now = 1_000 + 10 * 60_000) // opened again 10 minutes later: no request
        assertEquals(1, catalogRequests().size)
        catalogReply = { CardUpdates.Response(304, null, null) }
        check(now = 1_000 + 61 * 60_000) // an hour later: asks, with the ETag
        assertEquals("\"e1\"", catalogRequests().last().second["If-None-Match"])
        assertEquals(2, catalogRequests().size)
        assertEquals(1, CardUpdates.version.value) // nothing new installed
        assertEquals(1_000L, CardUpdates.status.value.updatedAt)
        // "Check now" asks even within the hour.
        check(now = 1_000 + 62 * 60_000, force = true)
        assertEquals(3, catalogRequests().size)
    }

    @Test
    fun failuresKeepTheCurrentData() {
        check(now = 1_000)
        catalogReply = { CardUpdates.Response(503, null, null) }
        check(now = 5_000_000, force = true)
        assertTrue(CardUpdates.status.value.failed)
        assertNotNull(Cards.bySlug("chemtech-enforcer"))
        // Too little data (something's off upstream) is ignored too.
        CardUpdates.minLegends = 20
        catalogReply = { CardUpdates.Response(200, sample, "\"e2\"") }
        check(now = 9_000_000, force = true)
        assertTrue(CardUpdates.status.value.failed)
        assertEquals(1_000L, CardUpdates.status.value.updatedAt)
        // And no network at all.
        CardUpdates.fetch = { _, _ -> throw java.io.IOException("offline") }
        check(now = 13_000_000, force = true)
        assertTrue(CardUpdates.status.value.failed)
        assertNotNull(Cards.bySlug("chemtech-enforcer"))
    }

    @Test
    fun cardPicturesAreFetchedOnceWhenShown() {
        check(now = 1_000)
        requests.clear()
        val bytes = CardUpdates.cardImage(context, "chemtech-enforcer.webp")
        assertNotNull(bytes)
        assertEquals(listOf("https://openrift.app/media/cards/img-unit-400w.webp"), requests.map { it.first })
        // Kept: the second time comes from the phone.
        CardUpdates.cardImage(context, "chemtech-enforcer.webp")
        assertEquals(1, requests.size)
        // A card the app doesn't know: nothing to fetch.
        assertNull(CardUpdates.cardImage(context, "no-such-card.webp"))
    }
}
