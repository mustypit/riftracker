package io.github.mustypit.rifttracker.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * OpenRift's catalog (GET /api/v1/catalog) turned into the app's three libraries, the same way
 * scripts/build_legend_assets.py does it for the bundled files: each card's, legend's and
 * battlefield's **original normal printing** (no overnumbered, alternate art, signed, promo-stamped
 * or special-distribution versions; earliest English release; a main set over a supplemental one).
 * The output has the same shape as assets/cards/cards.json, legends/legends.json and
 * battlefields/battlefields.json, plus each entry's `imageId` so the app can fetch pictures it lacks.
 * Keep the two in step.
 */
object CatalogImport {
    /** The three libraries as JSON, ready for [Cards.install], [Legends.install], [Battlefields.install]. */
    data class Result(
        val cardsJson: String, val legendsJson: String, val battlefieldsJson: String,
        val cardCount: Int, val legendCount: Int, val battlefieldCount: Int,
        /** Art to crop for legends and battlefields: file id (e.g. "kaisa") to OpenRift image id. */
        val legendArt: Map<String, String>, val battlefieldArt: Map<String, String>,
    )

    private val DOMAINS = setOf("fury", "calm", "mind", "body", "chaos", "order")

    fun parse(catalogJson: String): Result {
        val root = Json.parseToJsonElement(catalogJson).jsonObject
        val sets = (root["sets"] as? JsonArray).orEmpty().map { it.jsonObject }.associateBy { it.str("id") }
        val cards = asList(root["cards"]).associateBy { it.str("id") }
        val printings = asList(root["printings"])
        val tagMap = (root["tagCategoryMap"] as? JsonObject)?.mapValues { (it.value as? JsonPrimitive)?.contentOrNull } ?: emptyMap()
        val original = rank(sets, newest = false) // legend and battlefield art: the original printing
        val newest = rank(sets, newest = true)    // the library: the newest standard printing

        fun imageId(p: JsonObject) = (p["images"] as? JsonArray)?.firstOrNull()?.jsonObject?.str("imageId")

        // Cards: every card's best printing.
        val best = HashMap<String, JsonObject>()
        for (p in printings) {
            val cid = p.str("cardId") ?: continue
            if (cid !in cards || imageId(p) == null) continue
            val cur = best[cid]
            if (cur == null || newest.compare(p, cur) < 0) best[cid] = p
        }
        // Every English printing with a picture, one per picture, the shown one first.
        val printingsPerCard = printings.filter { it.str("language") == "EN" && imageId(it) != null && it.str("cardId") in cards }
            .groupBy { it.str("cardId")!! }
            .mapValues { (cid, ps) -> ps.sortedWith(newest).distinctBy { imageId(it) }.let { l -> listOfNotNull(best[cid]) + l.filter { imageId(it) != best[cid]?.let(::imageId) } } }
        fun kind(p: JsonObject) = when {
            p.bool("isSigned") -> "signed"
            p.bool("isOvernumbered") -> "overnumbered"
            (p.str("artVariant") ?: "normal") != "normal" -> "altart"
            p.nonEmpty("markers") || p.nonEmpty("distributionChannels") -> "promo"
            else -> "normal"
        }
        val codesPerCard = HashMap<String, MutableSet<String>>()
        for (p in printings) {
            val sc = p.str("shortCode") ?: continue
            val cid = p.str("cardId") ?: continue
            if (cid in cards) codesPerCard.getOrPut(cid) { mutableSetOf() } += sc.replace(Regex("[a-z*]$"), "")
        }
        val library = best.map { (cid, p) ->
            val c = cards.getValue(cid)
            val s = sets[p.str("setId")]
            val code = p.str("shortCode")
            val slug = c.str("slug") ?: cid
            buildJsonObject {
                put("slug", slug)
                put("name", clean(c.str("name") ?: ""))
                put("type", c.strList("types").firstOrNull() ?: "other")
                put("superTypes", c.arr("superTypes"))
                put("domains", c.arr("domains"))
                put("energy", c.int("energy")); put("power", c.int("power")); put("might", c.int("might"))
                put("keywords", c.arr("keywords"))
                putJsonArray("tags") { c.strList("tags").forEach { add(JsonPrimitive(clean(it))) } }
                put("text", p.str("printedRulesText") ?: "")
                put("set", s?.str("name") ?: "")
                put("code", code); put("rarity", p.str("rarity")); put("artist", p.str("artist"))
                put("maxCopies", c.int("maxCopiesOverride"))
                put("banned", c["bans"].let { it != null && it !is JsonNull && !(it is JsonArray && it.isEmpty()) })
                putJsonArray("codes") { (codesPerCard[cid].orEmpty() - setOfNotNull(code)).sorted().forEach { add(JsonPrimitive(it)) } }
                // Downloaded cards have no bundled picture: the app fetches it by this id when shown.
                put("image", "$slug.webp")
                put("imageId", imageId(p))
                putJsonArray("printings") {
                    printingsPerCard[cid].orEmpty().forEach { q ->
                        val code = q.str("shortCode") ?: return@forEach
                        add(buildJsonObject {
                            put("code", code); put("set", sets[q.str("setId")]?.str("name") ?: ""); put("imageId", imageId(q))
                            put("rarity", q.str("rarity")); put("artist", q.str("artist")); put("kind", kind(q))
                        })
                    }
                }
            }
        }.sortedWith(compareBy({ it.str("set") }, { it.str("code") ?: "" }, { it.str("name") }))

        // Legends and battlefields: one printing per champion / battlefield.
        fun pick(kind: String): List<Triple<String, JsonObject, JsonObject>> {
            val byName = sortedMapOf<String, MutableList<Pair<JsonObject, JsonObject>>>()
            for (p in printings) {
                val c = cards[p.str("cardId")] ?: continue
                if (c.strList("types") != listOf(kind) || imageId(p) == null) continue
                val name = if (kind == "legend") champion(c, tagMap) else clean(c.str("name") ?: "")
                byName.getOrPut(name) { mutableListOf() } += c to p
            }
            return byName.map { (name, options) -> options.minWithOrNull { a, b -> original.compare(a.second, b.second) }!!.let { Triple(name, it.first, it.second) } }
        }
        val legends = pick("legend")
        val legendArt = LinkedHashMap<String, String>()
        val legendsJson = buildJsonArray {
            for ((name, c, p) in legends) {
                val id = Legends.idOf(name)
                imageId(p)?.let { legendArt[id] = it }
                add(buildJsonObject {
                    put("name", name); put("title", clean(c.str("name") ?: "")); put("set", sets[p.str("setId")]?.str("name") ?: "")
                    putJsonArray("domains") { c.strList("domains").filter { it in DOMAINS }.forEach { add(JsonPrimitive(it)) } }
                    put("artist", p.str("artist")); put("image", "$id.jpg"); put("imageId", imageId(p))
                })
            }
        }
        val fields = pick("battlefield")
        val fieldArt = LinkedHashMap<String, String>()
        val fieldsJson = buildJsonArray {
            for ((name, _, p) in fields) {
                val id = Legends.idOf(name)
                imageId(p)?.let { fieldArt[id] = it }
                add(buildJsonObject {
                    put("name", name); put("set", sets[p.str("setId")]?.str("name") ?: ""); put("artist", p.str("artist"))
                    put("image", "$id.jpg"); put("imageId", imageId(p))
                })
            }
        }
        return Result(
            JsonArray(library).toString(), legendsJson.toString(), fieldsJson.toString(),
            library.size, legends.size, fields.size, legendArt, fieldArt,
        )
    }

    /**
     * Printing order, best first (same as the importer's `rank`): standard before special versions
     * (overnumbered, alternate art, signed, promo-stamped, special distribution, odd size); English;
     * released in English; then the original (earliest) or the [newest] release; a main set over a
     * supplemental one; normal finish.
     */
    private fun rank(sets: Map<String?, JsonObject>, newest: Boolean): Comparator<JsonObject> {
        fun en(p: JsonObject) = sets[p.str("setId")]?.obj("releases")?.obj("EN")?.str("releasedAt")
        val byRelease = compareBy<JsonObject> {
            listOf(
                it.bool("isOvernumbered"), (it.str("artVariant") ?: "normal") != "normal", it.bool("isSigned"),
                it.nonEmpty("markers"), it.nonEmpty("distributionChannels"), (it.str("size") ?: "standard") != "standard",
            ).count { b -> b }
        }.thenBy { it.str("language") != "EN" }.thenBy { en(it) == null }
        val dated = if (newest) byRelease.thenByDescending { en(it) ?: "" } else byRelease.thenBy { en(it) ?: "" }
        return dated.thenBy { sets[it.str("setId")]?.str("setType") != "main" }.thenBy { it.str("finish") != "normal" }
            .thenBy { it.int("canonicalRank") ?: 0 }.thenBy { it.str("shortCode") ?: "" }
    }

    /** The champion a legend card is for: its tag whose category is "legend". */
    private fun champion(card: JsonObject, tagMap: Map<String, String?>): String {
        val tags = card.strList("tags")
        return clean(tags.firstOrNull { tagMap[it] == "legend" } ?: tags.firstOrNull() ?: card.str("name") ?: "")
    }

    /** Straight apostrophes, as the app's names use. */
    private fun clean(s: String) = s.replace('’', '\'').trim()

    /** cards/printings come as {id: obj} or a list; give each obj its id. */
    private fun asList(e: JsonElement?): List<JsonObject> = when (e) {
        is JsonObject -> e.map { (k, v) -> JsonObject(v.jsonObject + ("id" to (v.jsonObject["id"] ?: JsonPrimitive(k)))) }
        is JsonArray -> e.map { it.jsonObject }
        else -> emptyList()
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull == true
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.strList(k: String) = (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    private fun JsonObject.nonEmpty(k: String) = when (val v = this[k]) { null, JsonNull -> false; is JsonArray -> v.isNotEmpty(); is JsonObject -> v.isNotEmpty(); else -> true }
}
