package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Match
import io.github.mustypit.rifttracker.game.MatchRules
import io.github.mustypit.rifttracker.game.Outcome

/** Black header block with a big number, used at the top of the list screens. */
@Composable
fun HeaderBlock(onBack: (() -> Unit)? = null, color: Color = C.Ink, ink: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(color).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp).size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ink)
            }
        } else Box(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = Caps, color = C.Muted, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp))

/** A row of equal pill buttons; one selected. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE6E1DA)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp)).background(if (on) C.Ink else Color.Transparent).clickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) { Text(label(o), color = if (on) Color.White else C.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.Bold, fontSize = 13.sp,
        color = if (selected) Color.White else C.Ink,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) C.Ink else Color.White)
            .border(1.5.dp, if (selected) C.Ink else C.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
fun RiftTextField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedBorderColor = C.Ink, unfocusedBorderColor = C.Line, focusedLabelColor = C.Ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** One match in a list: result block, opponent, legends, score. */
@Composable
fun MatchRow(m: Match, onClick: () -> Unit, showMyLegend: Boolean = true) {
    val r = MatchRules.result(m)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(54.dp).height(64.dp).background(C.outcome(r)), contentAlignment = Alignment.Center) {
            Text(when (r) { Outcome.WIN -> "W"; Outcome.LOSS -> "L"; Outcome.DRAW -> "D" }, fontWeight = FontWeight.Black, fontSize = 22.sp, color = C.Ink)
        }
        LegendAvatar(m.oppLegend, 40.dp, Modifier.padding(start = 12.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("vs ${m.oppLegend}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(m.myLegend.takeIf { showMyLegend }, m.oppName.ifBlank { null }, m.format.label).joinToString(" · ")
            Text(sub, color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 14.dp)) {
            Text(MatchRules.scoreLine(m), fontWeight = FontWeight.Black, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
                m.games.forEach { g -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(g.outcome?.let(C::outcome) ?: C.Line)) }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(28.dp, 44.dp).clip(RoundedCornerShape(6.dp)).background(C.Coral))
            Box(Modifier.size(28.dp, 44.dp).clip(RoundedCornerShape(6.dp)).background(C.Teal))
        }
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(top = 14.dp))
        Text(body, color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
