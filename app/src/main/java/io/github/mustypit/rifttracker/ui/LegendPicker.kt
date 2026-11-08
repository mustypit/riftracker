package io.github.mustypit.rifttracker.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Legend
import io.github.mustypit.rifttracker.game.Legends
import kotlin.math.absoluteValue

private val tilePalette = listOf(
    Color(0xFFFF6B4A) to Color(0xFFFFA24A), Color(0xFF21C4A7) to Color(0xFF3B8BEB), Color(0xFF7C5CFF) to Color(0xFFE85CC8),
    Color(0xFFFFC94A) to Color(0xFFFF6B4A), Color(0xFF3B8BEB) to Color(0xFF21C4A7), Color(0xFF2E2E2E) to Color(0xFF6B6B6B),
    Color(0xFFE85CC8) to Color(0xFFFF6B4A), Color(0xFF8BD450) to Color(0xFF21C4A7),
)

/** Art from an assets folder, loaded once per id; null when there's none. */
internal open class AssetArt(private val folder: String) {
    private val cache = HashMap<String, ImageBitmap?>()
    private var files: Set<String>? = null

    fun get(context: android.content.Context, id: String): ImageBitmap? = cache.getOrPut(id) {
        val names = files ?: (context.assets.list(folder)?.toSet() ?: emptySet()).also { files = it }
        val file = listOf("webp", "png", "jpg").map { "$id.$it" }.firstOrNull { it in names } ?: return@getOrPut null
        context.assets.open("$folder/$file").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }
}

internal object LegendArt : AssetArt("legends")
internal object BattlefieldArt : AssetArt("battlefields")

/** A legend's picture: real art when the app has it, otherwise a monogram tile. */
@Composable
fun LegendAvatar(name: String, size: Dp, modifier: Modifier = Modifier, corner: Dp = size / 4) {
    val context = LocalContext.current
    val legend = Legends.byName(name)
    val art = remember(legend?.id) { legend?.let { LegendArt.get(context, it.id) } }
    val shape = RoundedCornerShape(corner)
    if (art != null) {
        Image(art, name, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape))
    } else {
        val (a, b) = tilePalette[(legend?.id ?: name).hashCode().absoluteValue % tilePalette.size]
        Box(modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
            Text(Legends.initials(name), color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.38f).sp, letterSpacing = (-1).sp)
        }
    }
}

/** The "Your legend" / "Their legend" row on the setup screens: opens the picker. */
@Composable
fun LegendSlot(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.5.dp, if (value.isBlank()) C.Line else C.Ink, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(10.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (value.isBlank()) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEFEBE5)), contentAlignment = Alignment.Center) {
                Text("?", fontWeight = FontWeight.Black, fontSize = 22.sp, color = C.Muted)
            }
        } else LegendAvatar(value, 48.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(label.uppercase(), style = Caps.copy(fontSize = 11.sp), color = C.Muted)
            Text(value.ifBlank { "Choose a legend" }, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = if (value.isBlank()) C.Muted else C.Ink)
        }
        Icon(Icons.Default.ChevronRight, null, tint = C.Muted)
    }
}

/**
 * Full-screen legend grid over the setup screen (an overlay, so the setup form keeps its state).
 * Recently played legends first, then every set, newest first. A search box narrows the grid.
 */
@Composable
fun LegendPicker(title: String, recentNames: List<String>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var query by rememberSaveable { mutableStateOf("") }
    val groups = Legends.grouped(query)
    val recent = if (query.isBlank()) Legends.recent(recentNames) else emptyList()

    Column(Modifier.fillMaxSize().background(C.Paper)) {
        Column(Modifier.fillMaxWidth().background(C.Ink).statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            }
            RiftTextField("Search legends", query, { query = it }, Modifier.padding(start = 8.dp, top = 6.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("RECENT", style = Caps, color = C.Muted) }
                items(recent, key = { "r" + it.id }) { l -> LegendTile(l, l.name == selected) { onPick(l.name) } }
            }
            groups.forEach { (set, legends) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h$set") { Text(set.uppercase(), style = Caps, color = C.Muted, modifier = Modifier.padding(top = 6.dp)) }
                items(legends, key = { it.id }) { l -> LegendTile(l, l.name == selected) { onPick(l.name) } }
            }
            if (groups.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("No legend called “$query”.", color = C.Muted) }
            }
        }
    }
}

@Composable
private fun LegendTile(l: Legend, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(2.dp, if (selected) C.Ink else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(6.dp)
            .semantics { contentDescription = "Legend ${l.name}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) { LegendAvatar(l.name, maxWidth, corner = 12.dp) }
        Text(l.name, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
    }
}
