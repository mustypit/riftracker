package io.github.mustypit.rifttracker.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/** A printing's Cardmarket price: the lowest listing, in euro cents. */
data class CardPrice(val cents: Int) {
    val eur: Double get() = cents / 100.0
}

/**
 * Cardmarket prices by OpenRift printing id (`GET /api/v1/prices`, updated once a day; loaded and
 * refreshed by CardUpdates). Empty until loaded: screens show nothing for an unpriced printing.
 */
object Prices {
    @Volatile var byPrinting: Map<String, CardPrice> = emptyMap()

    /** The price of [card] as [printing] (or its shown printing). */
    fun of(card: Card, printing: Printing? = null): CardPrice? =
        (printing ?: card.printings.firstOrNull())?.id?.let { byPrinting[it] }

    /** Deck value: each card at its chosen printing, legend, sideboard and battlefields included. Null if nothing is priced. */
    fun deckValue(deck: Deck): Pair<Double, List<Triple<Card, Int, CardPrice>>>? {
        val counts = LinkedHashMap<String, Int>()
        fun add(slug: String?, n: Int) { if (slug != null && n > 0) counts[slug] = (counts[slug] ?: 0) + n }
        add(deck.legend, 1)
        deck.main.forEach { (s, n) -> add(s, n) }
        deck.side.forEach { (s, n) -> add(s, n) }
        deck.battlefields.forEach { add(DeckRules.battlefieldCard(it)?.slug, 1) }
        val rows = counts.mapNotNull { (slug, n) -> Cards.bySlug(slug)?.let { c -> of(c, deck.printingOf(c))?.let { Triple(c, n, it) } } }
        if (rows.isEmpty()) return null
        return rows.sumOf { it.second * it.third.eur } to rows.sortedByDescending { it.second * it.third.cents }
    }

    /** Reads `/api/v1/prices`: {prices: {printingId: {cardmarket: cents, …}}}. */
    fun parse(json: String): Map<String, CardPrice> {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return emptyMap()
        val prices = root["prices"] as? JsonObject ?: return emptyMap()
        return prices.mapNotNull { (id, v) ->
            val cents = ((v as? JsonObject)?.get("cardmarket") as? JsonPrimitive)?.content?.toIntOrNull()
            cents?.let { id to CardPrice(it) }
        }.toMap()
    }

    fun format(eur: Double): String = "€" + "%.2f".format(Locale.ENGLISH, eur)
}
