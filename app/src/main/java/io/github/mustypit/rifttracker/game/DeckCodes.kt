package io.github.mustypit.rifttracker.game

/**
 * Riftbound deck codes: the shareable strings Piltover Archive, Riftmana, Riftbound Zone and the
 * companion apps import and export (e.g. "CIAAAAAAAAAQCAAAA4AACAIAABMQ…").
 *
 * A port of Piltover Archive's reference implementation (github.com/Piltover-Archive/RiftboundDeckCodes,
 * Apache License 2.0, itself adapted from Riot's LoRDeckCodes), format 1, versions 1–6: varints
 * (LEB128) grouped by copy count, then by set and variant, base32-encoded. The main deck holds the
 * legend, main cards (champion included), rune cards and battlefields; then the sideboard, the chosen
 * champion and (v6) additional legends. This file only does the encoding; [DeckCodes.toDeck] and
 * [DeckCodes.fromDeck] map codes to and from our [Deck].
 */
object DeckCodeFormat {
    data class Entry(val cardCode: String, val count: Int)
    data class Decoded(val main: List<Entry>, val sideboard: List<Entry>, val chosenChampion: String? = null, val additionalLegends: List<String>? = null)

    private const val FORMAT = 1
    private const val MAX_VERSION = 6
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val SETS = linkedMapOf("OGN" to 0, "OGS" to 1, "ARC" to 2, "SFD" to 3, "UNL" to 4, "VEN" to 5, "RAD" to 6)
    private val VARIANTS = mapOf("" to 0, "a" to 1, "s" to 2, "*" to 2, "b" to 3)
    private val variantSuffix = mapOf(0 to "", 1 to "a", 2 to "s", 3 to "b")
    private val codeRegex = Regex("""^((?:R|SP)?\d+)([a-z*]?)$""")

    private data class Parsed(val set: String, val number: String, val variant: String)

    /** Whether the format can carry this card code (a known set and variant). */
    fun canEncode(code: String): Boolean = runCatching { parse(code).let { it.set in SETS && it.variant in VARIANTS } }.getOrDefault(false)

    private fun parse(code: String): Parsed {
        val parts = code.split("-")
        require(parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) { "Invalid card code: $code" }
        val m = codeRegex.matchEntire(parts[1]) ?: throw IllegalArgumentException("Invalid card code: $code")
        return Parsed(parts[0], m.groupValues[1], m.groupValues[2])
    }

    // Base32, no padding.
    private fun base32Encode(bytes: List<Int>): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b and 0xff); bits += 8
            while (bits >= 5) { bits -= 5; out.append(ALPHABET[(buffer shr bits) and 0x1f]) }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1f])
        return out.toString()
    }

    private fun base32Decode(s: String): ByteArray {
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bits = 0
        for (ch in s) {
            val v = ALPHABET.indexOf(ch.uppercaseChar())
            require(v >= 0) { "Invalid character in deck code: '$ch'" }
            buffer = (buffer shl 5) or v; bits += 5
            if (bits >= 8) { bits -= 8; out += ((buffer shr bits) and 0xff).toByte() }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return out.toByteArray()
    }

    private fun varint(value: Int): List<Int> {
        if (value == 0) return listOf(0)
        val out = mutableListOf<Int>()
        var v = value
        while (v != 0) {
            var b = v and 0x7f
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out += b
        }
        return out
    }

    private class Reader(val bytes: ByteArray) {
        var i = 0
        fun byte(): Int { require(i < bytes.size) { "Deck code is too short" }; return bytes[i++].toInt() and 0xff }
        fun varint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = byte()
                result = result or ((b and 0x7f) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 35) { "Bad varint" }
            }
        }
    }

    private class Group(val set: Int, val variant: Int, val numbers: MutableList<String>)

    /** Numbers in natural order ("R02" < "R10", "007" < "012"), as localeCompare(numeric) does. */
    private val natural = Comparator<String> { a, b ->
        val ra = Regex("""\d+|\D+""").findAll(a).map { it.value }.toList()
        val rb = Regex("""\d+|\D+""").findAll(b).map { it.value }.toList()
        for (k in 0 until minOf(ra.size, rb.size)) {
            val x = ra[k]; val y = rb[k]
            val c = if (x[0].isDigit() && y[0].isDigit()) x.toBigInteger().compareTo(y.toBigInteger()).takeIf { it != 0 } ?: x.length.compareTo(y.length) else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        ra.size.compareTo(rb.size)
    }

    private fun groups(cards: List<Entry>): List<Group> {
        val map = linkedMapOf<Pair<String, String>, Group>()
        for (c in cards) {
            val p = parse(c.cardCode)
            val g = map.getOrPut(p.set to p.variant) {
                Group(SETS[p.set] ?: throw IllegalArgumentException("Unknown set: ${p.set}"), VARIANTS[p.variant] ?: throw IllegalArgumentException("Unknown variant: ${p.variant}"), mutableListOf())
            }
            g.numbers += p.number
        }
        return map.values.sortedWith(compareBy({ it.set }, { it.variant })).onEach { it.numbers.sortWith(natural) }
    }

    private fun numberBytes(number: String, flagged: Boolean): List<Int> = when {
        !flagged -> varint(number.toInt())
        number.startsWith("SP") -> listOf(0x02) + varint(number.drop(2).toInt())
        number.startsWith("R") -> listOf(0x01) + varint(number.drop(1).toInt())
        else -> listOf(0x00) + varint(number.toInt())
    }

    private fun section(deck: List<Entry>, maxCount: Int, flagged: Boolean): List<Int> {
        val out = mutableListOf<Int>()
        for (count in maxCount downTo 1) {
            val gs = groups(deck.filter { it.count == count })
            out += varint(gs.size)
            for (g in gs) {
                out += varint(g.numbers.size); out += g.set; out += g.variant
                g.numbers.forEach { out += numberBytes(it, flagged) }
            }
        }
        return out
    }

    private fun sparseSection(deck: List<Entry>, flagged: Boolean): List<Int> {
        val out = mutableListOf<Int>()
        val counts = deck.map { it.count }.filter { it >= 1 }.distinct().sortedDescending()
        out += varint(counts.size)
        for (count in counts) {
            out += varint(count)
            val gs = groups(deck.filter { it.count == count })
            out += varint(gs.size)
            for (g in gs) {
                out += varint(g.numbers.size); out += g.set; out += g.variant
                g.numbers.forEach { out += numberBytes(it, flagged) }
            }
        }
        return out
    }

    private fun cardRef(code: String, flagged: Boolean): List<Int> {
        val p = parse(code)
        val set = SETS[p.set] ?: throw IllegalArgumentException("Unknown set: ${p.set}")
        val variant = VARIANTS[p.variant] ?: throw IllegalArgumentException("Unknown variant: ${p.variant}")
        return listOf(set, variant) + numberBytes(p.number, flagged)
    }

    fun encode(main: List<Entry>, sideboard: List<Entry> = emptyList(), chosenChampion: String? = null, additionalLegends: List<String> = emptyList()): String {
        (main + sideboard).forEach { require(it.count >= 1) { "Invalid count for ${it.cardCode}: ${it.count}" } }
        val all = main.map { it.cardCode } + sideboard.map { it.cardCode } + listOfNotNull(chosenChampion) + additionalLegends
        val anyRune = all.any { parse(it).number.startsWith("R") }
        val anySpecial = all.any { parse(it).number.startsWith("SP") }
        val highCounts = (main.maxOfOrNull { it.count } ?: 0) > 12 || (sideboard.maxOfOrNull { it.count } ?: 0) > 3
        val version = when { additionalLegends.isNotEmpty() -> 6; highCounts || anySpecial -> 5; anyRune -> 4; else -> 3 }
        val flagged = anyRune || anySpecial
        val bytes = mutableListOf((FORMAT shl 4) or version)
        if (version >= 5) {
            bytes += if (flagged) 1 else 0
            bytes += sparseSection(main, flagged); bytes += sparseSection(sideboard, flagged)
        } else {
            val f = version >= 4
            bytes += section(main, 12, f); bytes += section(sideboard, 3, f)
        }
        if (chosenChampion != null) { bytes += 0x01; bytes += cardRef(chosenChampion, flagged) } else bytes += 0x00
        if (version >= 6) {
            bytes += varint(additionalLegends.size)
            additionalLegends.forEach { bytes += cardRef(it, flagged) }
        }
        return base32Encode(bytes)
    }

    private fun setName(id: Int) = SETS.entries.firstOrNull { it.value == id }?.key ?: throw IllegalArgumentException("Unknown set code: $id")
    private fun variantName(id: Int) = variantSuffix[id] ?: throw IllegalArgumentException("Unknown variant code: $id")

    private fun readNumber(r: Reader, flagged: Boolean): String {
        if (!flagged) return r.varint().toString().padStart(3, '0')
        val flag = r.byte()
        val n = r.varint()
        return when (flag) {
            0x00 -> n.toString().padStart(3, '0')
            0x01 -> "R" + n.toString().padStart(2, '0')
            0x02 -> "SP$n"
            else -> throw IllegalArgumentException("Unknown number-prefix flag: $flag")
        }
    }

    private fun readGroups(r: Reader, count: Int, flagged: Boolean, out: MutableList<Entry>) {
        repeat(r.varint()) {
            val n = r.varint()
            val set = setName(r.byte())
            val variant = variantName(r.byte())
            repeat(n) { out += Entry("$set-${readNumber(r, flagged)}$variant", count) }
        }
    }

    private fun readSection(r: Reader, maxCount: Int, flagged: Boolean): List<Entry> {
        val out = mutableListOf<Entry>()
        for (count in maxCount downTo 1) readGroups(r, count, flagged, out)
        return out
    }

    private fun readSparse(r: Reader, flagged: Boolean): List<Entry> {
        val out = mutableListOf<Entry>()
        repeat(r.varint()) { readGroups(r, r.varint(), flagged, out) }
        return out
    }

    private fun readRef(r: Reader, flagged: Boolean): String {
        val set = setName(r.byte())
        val variant = variantName(r.byte())
        return "$set-${readNumber(r, flagged)}$variant"
    }

    /** Throws IllegalArgumentException for anything that isn't a deck code we can read. */
    fun decode(code: String): Decoded {
        val r = Reader(base32Decode(code.trim()))
        val fv = r.byte()
        val format = (fv shr 4) and 0x0f
        val version = fv and 0x0f
        require(format == FORMAT) { "Unsupported format: $format" }
        require(version in 1..MAX_VERSION) { "Unsupported version: $version" }
        val flagged = if (version >= 5) r.byte().also { require(it <= 1) { "Bad prefix flag" } } == 1 else version >= 4
        val main: List<Entry>
        var side: List<Entry> = emptyList()
        if (version >= 5) {
            main = readSparse(r, flagged); side = readSparse(r, flagged)
        } else {
            main = readSection(r, 12, flagged)
            if (version >= 2) side = readSection(r, 3, flagged)
        }
        var champion: String? = null
        if (version >= 3 && r.byte() == 0x01) champion = readRef(r, flagged)
        var legends: List<String>? = null
        if (version >= 6) legends = List(r.varint()) { readRef(r, flagged) }
        return Decoded(main, side, champion, legends)
    }

    /** Whether [text] looks like a deck code (one base32 word), not a pasted card list. */
    fun looksLikeCode(text: String): Boolean = text.trim().let { t -> t.length >= 16 && t.all { it.uppercaseChar() in ALPHABET } }
}

/** Deck codes to and from our [Deck], using the card library's card codes. */
object DeckCodes {
    private fun base(code: String) = code.trim().replace(Regex("[a-z*]$"), "")

    /** The card a code names: its main printing, another printing (reprint/alt art), or null. */
    fun cardFor(code: String): Card? = Cards.byCode(base(code))

    fun runeCard(d: Domain): Card? = Cards.all.firstOrNull { it.type == "rune" && it.domainList == listOf(d) && it.code?.contains('-') == true }

    /** The card's code for a deck code: its shown printing's, or another printing's when the format can't carry that one (a newer set). */
    private fun codeOf(c: Card?): String? = c?.let { (listOfNotNull(it.code) + it.codes).firstOrNull(DeckCodeFormat::canEncode) }

    /** A code for [deck]: legend, main deck, runes and battlefields; sideboard; chosen champion. Cards without a code are left out. */
    fun fromDeck(deck: Deck): String {
        // A card's chosen printing goes into the code, so other apps show the same art.
        fun codeOf(c: Card?): String? = c?.let { deck.printingOf(it)?.code?.takeIf(DeckCodeFormat::canEncode) } ?: this.codeOf(c)
        val main = mutableListOf<DeckCodeFormat.Entry>()
        fun add(c: Card?, n: Int) { val code = codeOf(c) ?: return; if (n > 0) main += DeckCodeFormat.Entry(code, n) }
        add(DeckRules.legendOf(deck), 1)
        deck.main.forEach { (slug, n) -> add(Cards.bySlug(slug), n) }
        deck.runes.forEach { (d, n) -> Domain.entries.firstOrNull { it.name == d }?.let { add(runeCard(it), n) } }
        deck.battlefields.forEach { add(DeckRules.battlefieldCard(it), 1) }
        val side = deck.side.mapNotNull { (slug, n) -> codeOf(Cards.bySlug(slug))?.takeIf { n > 0 }?.let { DeckCodeFormat.Entry(it, n) } }
        // Merge duplicates (two slugs can share a code only in broken data, but stay safe).
        fun merge(l: List<DeckCodeFormat.Entry>) = l.groupBy { it.cardCode }.map { (k, v) -> DeckCodeFormat.Entry(k, v.sumOf { it.count }) }
        return DeckCodeFormat.encode(merge(main), merge(side), codeOf(deck.champion?.let(Cards::bySlug)))
    }

    /** Reads a code into a deck named [name]; unknown card codes are returned so the screen can count them. Null deck if it has no legend. */
    fun toDeck(code: String, name: String): Pair<Deck?, List<String>> {
        val decoded = DeckCodeFormat.decode(code)
        val unknown = mutableListOf<String>()
        var legend: Card? = null
        val main = linkedMapOf<String, Int>()
        val runes = linkedMapOf<String, Int>()
        val fields = mutableListOf<String>()
        val arts = linkedMapOf<String, String>()
        // A code naming a special printing of a card (alt art, signed…) keeps it as the deck's art; a plain reprint doesn't.
        fun keepArt(c: Card, cc: String) { if (cc != c.code && c.printing(cc)?.kind.let { it != null && it != "normal" }) arts[c.slug] = cc }
        for ((cc, n) in decoded.main) {
            val c = cardFor(cc)
            if (c != null) keepArt(c, cc)
            when {
                c == null -> unknown += cc
                c.type == "legend" -> if (legend == null) legend = c else unknown += cc
                c.type == "rune" -> c.domainList.firstOrNull()?.let { runes[it.name] = (runes[it.name] ?: 0) + n }
                c.type == "battlefield" -> fields += c.name
                else -> main[c.slug] = (main[c.slug] ?: 0) + n
            }
        }
        val side = linkedMapOf<String, Int>()
        for ((cc, n) in decoded.sideboard) {
            val c = cardFor(cc)
            if (c == null) unknown += cc else { keepArt(c, cc); side[c.slug] = (side[c.slug] ?: 0) + n }
        }
        val leg = legend ?: return null to unknown
        val champ = decoded.chosenChampion?.let(::cardFor) ?: DeckRules.champions(leg).firstOrNull { main.containsKey(it.slug) }
        return Deck(name = name, legend = leg.slug, champion = champ?.slug, main = main, runes = runes, battlefields = fields, side = side, arts = arts) to unknown
    }
}
