package io.github.mustypit.rifttracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Battlefield
import io.github.mustypit.rifttracker.game.Battlefields
import kotlin.math.absoluteValue

/** A battlefield's art (landscape), or a calm gradient with its name when there's no art. */
@Composable
fun BattlefieldThumb(name: String, modifier: Modifier, corner: Int = 10) {
    val context = LocalContext.current
    val bf = Battlefields.byName(name)
    val art = remember(bf?.id) { bf?.let { BattlefieldArt.get(context, it.id) } }
    val shape = RoundedCornerShape(corner.dp)
    if (art != null) {
        Image(art, name, contentScale = ContentScale.Crop, modifier = modifier.clip(shape))
    } else {
        val hues = listOf(Color(0xFF3B5B4F) to Color(0xFF7A9A6B), Color(0xFF4A3B5B) to Color(0xFF8B6BA8), Color(0xFF5B4A3B) to Color(0xFFB08A5C), Color(0xFF3B4A5B) to Color(0xFF6B8BB0))
        val (a, b) = hues[name.hashCode().absoluteValue % hues.size]
        Box(modifier.clip(shape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
            Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
        }
    }
}

/** Compact "Your battlefield: [art] Name" row; tap to pick. */
@Composable
fun BattlefieldSlot(label: String, value: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Soft)
            .clickable(onClick = onClick).padding(8.dp).semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (value != null) BattlefieldThumb(value, Modifier.width(72.dp).aspectRatio(2.2f), corner = 8)
        else Box(Modifier.width(72.dp).aspectRatio(2.2f).clip(RoundedCornerShape(8.dp)).background(C.Track), contentAlignment = Alignment.Center) {
            Text("+", color = C.Muted, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label.uppercase(), style = Caps.copy(fontSize = 10.sp), color = C.Muted)
            Text(value ?: "Choose (optional)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (value == null) C.Muted else C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Full-screen battlefield grid with search; recently used first. [onPick] with null clears it. */
@Composable
fun BattlefieldPicker(title: String, recentNames: List<String?>, selected: String?, onPick: (String?) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var query by rememberSaveable { mutableStateOf("") }
    val groups = Battlefields.grouped(query)
    val recent = if (query.isBlank()) Battlefields.recent(recentNames) else emptyList()
    Column(Modifier.fillMaxSize().background(C.Paper)) {
        Column(Modifier.fillMaxWidth().background(C.Header).statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.weight(1f))
                if (selected != null) Text("Clear", color = C.Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onPick(null) }.padding(8.dp))
            }
            RiftTextField("Search battlefields", query, { query = it }, Modifier.padding(start = 8.dp, top = 6.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("RECENT", style = Caps, color = C.Muted) }
                items(recent, key = { "r" + it.id }) { b -> BattlefieldTile(b, b.name == selected) { onPick(b.name) } }
            }
            groups.forEach { (set, list) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h$set") { Text(set.uppercase(), style = Caps, color = C.Muted, modifier = Modifier.padding(top = 6.dp)) }
                items(list, key = { it.id }) { b -> BattlefieldTile(b, b.name == selected) { onPick(b.name) } }
            }
            if (groups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No battlefield called “$query”.", color = C.Muted) }
        }
    }
}

@Composable
private fun BattlefieldTile(b: Battlefield, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(C.Card)
            .border(2.dp, if (selected) C.Ink else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(6.dp).semantics { contentDescription = "Battlefield ${b.name}" },
    ) {
        BattlefieldThumb(b.name, Modifier.fillMaxWidth().aspectRatio(2.2f))
        Text(b.name, color = C.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}
