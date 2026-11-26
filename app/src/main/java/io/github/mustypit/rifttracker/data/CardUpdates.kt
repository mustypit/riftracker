package io.github.mustypit.rifttracker.data

import android.content.Context
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.mustypit.rifttracker.game.Battlefields
import io.github.mustypit.rifttracker.game.CatalogImport
import io.github.mustypit.rifttracker.game.Cards
import io.github.mustypit.rifttracker.game.Legends
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.net.HttpURLConnection
import java.net.URL

/**
 * Card data straight from OpenRift's free API (openrift.app/developers), checked inside the app:
 * when the app opens (at most once an hour, as OpenRift asks: responses are cacheable for an hour),
 * with the last ETag, so most checks come back "304 Not Modified" with no body. New data is turned
 * into the app's libraries ([CatalogImport]), saved under files/openrift/ and installed; the
 * bundled assets stay as the fallback. Card pictures the app doesn't ship are fetched one at a time
 * when first shown ([cardImage]); new legends' and battlefields' art is cropped like the importer
 * does. Nothing about the user is sent: the User-Agent names the app and its repo, as OpenRift asks.
 */
object CardUpdates {
    const val CATALOG_URL = "https://openrift.app/api/v1/catalog?langs=EN"
    private const val MEDIA = "https://openrift.app/media/cards/"
    const val USER_AGENT_CONTACT = "+https://github.com/mustypit/riftracker"
    private const val CHECK_EVERY_MS = 60 * 60 * 1000L
    /** Fewer than these means something's off upstream: keep what we have. */
    var minLegends = 20
    var minCards = 500

    // Same artwork boxes as scripts/build_legend_assets.py (fractions of width / height).
    private val ART_BOX = floatArrayOf(0.17f, 0.03f, 0.97f, 0.64f)
    private val BF_ART_BOX = floatArrayOf(0.04f, 0.21f, 0.96f, 0.60f)

    data class Response(val code: Int, val body: ByteArray?, val etag: String?)

    /** The network: GET [url] with these headers. Tests swap in a fake. */
    var fetch: (url: String, headers: Map<String, String>) -> Response = ::httpGet

    data class Status(val updatedAt: Long?, val checking: Boolean = false, val failed: Boolean = false)
    private val _status = MutableStateFlow(Status(null))
    val status: StateFlow<Status> = _status
    /** Bumps when new data is installed, so screens can redraw with it. */
    val version = MutableStateFlow(0)

    private val lock = Mutex()
    /** Pictures OpenRift answered 404 for, this run (not retried until the app restarts). */
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private var userAgent = "RiftTracker ($USER_AGENT_CONTACT)"

    private fun dir(context: Context) = File(context.filesDir, "openrift")
    private fun prefs(context: Context) = context.getSharedPreferences("card_updates", Context.MODE_PRIVATE)

    /**
     * At startup: the bundled libraries, then whatever was downloaded before on top (newer).
     * Call on the main thread before the UI reads the libraries.
     */
    fun installStartup(context: Context) {
        userAgent = "RiftTracker/${versionName(context)} ($USER_AGENT_CONTACT)"
        val saved = dir(context)
        fun read(asset: String, file: String) =
            File(saved, file).takeIf { it.exists() }?.readText() ?: runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }.getOrNull()
        read("legends/legends.json", "legends.json")?.let { runCatching { Legends.install(it) } }
        read("cards/cards.json", "cards.json")?.let { runCatching { Cards.install(it) } }
        read("battlefields/battlefields.json", "battlefields.json")?.let { runCatching { Battlefields.install(it) } }
        val p = prefs(context)
        _status.value = Status(p.getLong("updatedAt", 0L).takeIf { it > 0 })
    }

    /**
     * Checks OpenRift for new card data, unless the last check was under an hour ago ([force] skips
     * that, for "Check now"). Never throws; on failure the current data stays.
     */
    suspend fun check(context: Context, now: Long, force: Boolean = false) = lock.withLock {
        val p = prefs(context)
        val last = p.getLong("checkedAt", -1L)
        if (!force && last >= 0 && now - last < CHECK_EVERY_MS) return@withLock
        _status.value = _status.value.copy(checking = true, failed = false)
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                val headers = buildMap {
                    put("User-Agent", userAgent)
                    // Only send the ETag if the data it describes is still on the phone.
                    if (File(dir(context), "cards.json").exists()) p.getString("etag", null)?.let { put("If-None-Match", it) }
                }
                val r = fetch(CATALOG_URL, headers)
                when (r.code) {
                    304 -> null
                    200 -> r
                    else -> error("HTTP ${r.code}")
                }?.let { r ->
                    val result = CatalogImport.parse(r.body!!.decodeToString())
                    check(result.legendCount >= minLegends && result.cardCount >= minCards) { "too little data" }
                    save(context, result)
                    p.edit().putString("etag", r.etag).putLong("updatedAt", now).apply()
                    result
                }
            }?.let { result ->
                // Install on the caller's (main) thread, like at startup.
                Legends.install(result.legendsJson)
                Cards.install(result.cardsJson)
                Battlefields.install(result.battlefieldsJson)
                version.value++
            }
            p.edit().putLong("checkedAt", now).apply()
        }.isSuccess
        _status.value = Status(p.getLong("updatedAt", 0L).takeIf { it > 0 }, checking = false, failed = !ok)
    }

    /** Writes the libraries to files/openrift/ in one go (a half-written set never replaces a good one). */
    private fun save(context: Context, r: CatalogImport.Result) {
        val target = dir(context)
        val tmp = File(context.filesDir, "openrift.tmp").apply { deleteRecursively(); mkdirs() }
        File(tmp, "cards.json").writeText(r.cardsJson)
        File(tmp, "legends.json").writeText(r.legendsJson)
        File(tmp, "battlefields.json").writeText(r.battlefieldsJson)
        // Keep pictures already downloaded.
        File(target, "images").takeIf { it.exists() }?.renameTo(File(tmp, "images"))
        File(target, "art").takeIf { it.exists() }?.renameTo(File(tmp, "art"))
        // New legends' and battlefields' art (only ones the app has nowhere yet: a handful per set).
        val shipped = { folder: String -> context.assets.list(folder)?.toSet().orEmpty() }
        for ((folder, arts, box, landscape) in listOf(
            Art("legends", r.legendArt, ART_BOX, false), Art("battlefields", r.battlefieldArt, BF_ART_BOX, true),
        )) {
            val inApk = shipped(folder)
            val out = File(tmp, "art/$folder").apply { mkdirs() }
            for ((id, imageId) in arts) {
                if (listOf("webp", "png", "jpg").any { "$id.$it" in inApk } || File(out, "$id.jpg").exists()) continue
                runCatching { cropArt(imageId, box, landscape)?.let { File(out, "$id.jpg").writeBytes(it) } }
            }
        }
        target.deleteRecursively()
        check(tmp.renameTo(target)) { "couldn't save" }
    }

    private data class Art(val folder: String, val arts: Map<String, String>, val box: FloatArray, val landscape: Boolean)

    private fun cropArt(imageId: String, box: FloatArray, landscape: Boolean): ByteArray? {
        val r = fetch("$MEDIA$imageId-full.webp", mapOf("User-Agent" to userAgent))
        val bytes = r.body?.takeIf { r.code == 200 } ?: return null
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if ((src.width > src.height) != landscape) return null
        val x0 = (src.width * box[0]).toInt(); val y0 = (src.height * box[1]).toInt()
        val cut = Bitmap.createBitmap(src, x0, y0, (src.width * box[2]).toInt() - x0, (src.height * box[3]).toInt() - y0)
        val scale = minOf(1f, 720f / maxOf(cut.width, cut.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(cut, (cut.width * scale).toInt(), (cut.height * scale).toInt(), true) else cut
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    }

    /** Art for a new legend or battlefield that the app doesn't ship, if it was downloaded. */
    fun art(context: Context, folder: String, id: String): File? = File(dir(context), "art/$folder/$id.jpg").takeIf { it.exists() }

    /**
     * A card picture the app doesn't ship: from files/openrift/images, or fetched from OpenRift
     * (400 px, one card at a time, only when shown) and kept. Call off the main thread.
     */
    fun cardImage(context: Context, file: String): ByteArray? {
        val local = File(dir(context), "images/$file")
        if (local.exists()) return local.readBytes()
        val imageId = Cards.bySlug(file.substringBeforeLast('.'))?.imageId ?: return null
        // A picture OpenRift doesn't have isn't asked for again every time the card is drawn.
        if (imageId in missing) return null
        val r = runCatching { fetch("$MEDIA$imageId-400w.webp", mapOf("User-Agent" to userAgent)) }.getOrNull() ?: return null
        val bytes = r.body?.takeIf { r.code == 200 && it.isNotEmpty() } ?: run { if (r.code == 404) missing += imageId; return null }
        runCatching { local.parentFile?.mkdirs(); local.writeBytes(bytes) }
        return bytes
    }

    private fun versionName(context: Context) =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    private fun httpGet(url: String, headers: Map<String, String>): Response {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 60_000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            val body = if (code == 200) c.inputStream.use { it.readBytes() } else null
            return Response(code, body, c.getHeaderField("ETag"))
        } finally {
            c.disconnect()
        }
    }

    /** Tests: forget everything (memory only; files live in the test's own app folder). */
    fun resetForTests() {
        fetch = ::httpGet; minLegends = 20; minCards = 500; missing.clear()
        _status.value = Status(null); version.value = 0
    }
}
