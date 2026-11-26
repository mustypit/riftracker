package io.github.mustypit.rifttracker.game

import java.util.Locale

/** A printing's Cardmarket price (EUR, the lowest listing) and its product page. */
data class CardPrice(val eur: Double, val url: String? = null)

/**
 * Cardmarket prices by printing code (from OpenRift's price data, once a day). Empty until loaded;
 * screens show nothing for a printing without a price.
 */
object Prices {
    @Volatile var byCode: Map<String, CardPrice> = emptyMap()

    /** The price of [card] as [printing] (or its shown printing). */
    fun of(card: Card, printing: Printing? = null): CardPrice? = byCode[printing?.code ?: card.code]

    /** Deck value: each card at its chosen printing, legend and battlefields included. Null if nothing is priced. */
    fun deckValue(deck: Deck): Pair<Double, List<Triple<Card, Int, CardPrice>>>? {
        val counts = LinkedHashMap<String, Int>()
        fun add(slug: String?, n: Int) { if (slug != null && n > 0) counts[slug] = (counts[slug] ?: 0) + n }
        add(deck.legend, 1)
        deck.main.forEach { (s, n) -> add(s, n) }
        deck.side.forEach { (s, n) -> add(s, n) }
        deck.battlefields.forEach { add(DeckRules.battlefieldCard(it)?.slug, 1) }
        val rows = counts.mapNotNull { (slug, n) -> Cards.bySlug(slug)?.let { c -> of(c, deck.printingOf(c))?.let { Triple(c, n, it) } } }
        if (rows.isEmpty()) return null
        return rows.sumOf { it.second * it.third.eur } to rows.sortedByDescending { it.second * it.third.eur }
    }

    fun format(eur: Double): String = "€" + "%.2f".format(Locale.ENGLISH, eur)
}
