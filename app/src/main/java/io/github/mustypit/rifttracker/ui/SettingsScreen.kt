package io.github.mustypit.rifttracker.ui

import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import io.github.mustypit.rifttracker.data.CardUpdates
import androidx.compose.foundation.Image
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Legends
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.style.TextAlign

/** The dark veil over legend art, as drawn on the scoring screen. */
fun Modifier.legendVeil(darkness: Float) = drawBehind {
    val (centre, edge) = Veil.alphas(darkness)
    drawRect(
        Brush.radialGradient(
            0f to Color.Black.copy(alpha = centre), 0.82f to Color.Black.copy(alpha = edge), 1f to Color.Black.copy(alpha = edge),
            center = center, radius = maxOf(size.width * 0.64f, size.height * 0.6f),
        ),
    )
}

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: (() -> Unit)? = null) {
    val darkness by vm.settings.darkness.collectAsState()
    val context = LocalContext.current
    // Preview art: the last legend you played, else the first legend that has art.
    val history by vm.history.collectAsState()
    val previewArt = remember(history) {
        (history.map { it.myLegend } + Legends.all.map { it.name }).asSequence()
            .mapNotNull { n -> Legends.byName(n)?.let { LegendArt.get(context, it.id) } }.firstOrNull()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderBlock(onBack = onBack) {
            Text("Settings", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        }
        SectionLabel("Theme")
        val theme by vm.settings.theme.collectAsState()
        Segmented(ThemeMode.entries, theme, { it.label }, vm.settings::setTheme, Modifier.padding(horizontal = 16.dp))
        SectionLabel("Accent")
        val accent by vm.settings.accent.collectAsState()
        AccentPicker(accent, vm.settings::setAccent, Modifier.padding(horizontal = 16.dp))
        SectionLabel("Score background")
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp)) {
            // Live preview, drawn exactly like a half of the scoring screen.
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0C0C0C)), contentAlignment = Alignment.Center) {
                if (previewArt != null) {
                    Image(previewArt, null, contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.4f), modifier = Modifier.matchParentSize())
                }
                Box(Modifier.matchParentSize().legendVeil(darkness))
                Text("5", style = TextStyle(fontSize = 110.sp, fontWeight = FontWeight.Black, color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 28f)))
            }
            Slider(
                value = darkness,
                onValueChange = vm.settings::setDarkness,
                colors = SliderDefaults.colors(thumbColor = C.Ink, activeTrackColor = C.Ink, inactiveTrackColor = C.Line),
                modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = "Background darkness" },
            )
            Text("${Math.round(darkness * 100)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = C.Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        SectionLabel("Cards")
        CardsRow()
        val version = remember { AppInfo.version(context) }
        Text(
            "RiftTracker $version\nFan app, not affiliated with Riot Games · cards: OpenRift",
            color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** "Updated today · Check now": card data from OpenRift (checked on its own whenever the app opens). */
@Composable
private fun CardsRow() {
    val status by CardUpdates.status.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = AppGraph.clock()
    val label = when {
        status.checking -> "Checking…"
        status.failed -> "Couldn't check"
        status.updatedAt == null -> "Built in"
        else -> when (val days = ((now - status.updatedAt!!) / 86_400_000L).toInt()) {
            0 -> "Updated today"
            1 -> "Updated yesterday"
            else -> "Updated $days days ago"
        }
    }
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f).semantics { contentDescription = "Cards $label" })
        TextButton(onClick = { scope.launch { CardUpdates.check(context.applicationContext, AppGraph.clock(), force = true) } }, enabled = !status.checking) {
            Text("Check now", fontWeight = FontWeight.Bold, color = if (status.checking) C.Muted else C.Ink)
        }
    }
}

/** The installed app's version, e.g. "1.0 · build 57". */
object AppInfo {
    fun version(context: android.content.Context): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return "?"
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return "${info.versionName} · build $code"
    }
}

/**
 * The accent (owner's pick, "1"): a slice of the real app that recolours as you tap, eight tested pastel
 * colours, and "+" for a custom hue (the slider only shows then). No explanatory text.
 */
@Composable
private fun AccentPicker(accent: Color, onPick: (Color) -> Unit, modifier: Modifier = Modifier) {
    val isPreset = Accents.presets.any { it.second == accent }
    var custom by remember { mutableStateOf(!isPreset) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccentSample(accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Accents.presets.forEach { (name, c) ->
                val on = c == accent && !custom
                Box(
                    Modifier.size(32.dp).then(if (on) Modifier.border(2.dp, C.Ink, CircleShape).padding(4.dp) else Modifier)
                        .clip(CircleShape).background(c)
                        .clickable { custom = false; onPick(c) }.semantics { contentDescription = "Accent $name" + if (on) " on" else "" },
                )
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(if (custom) accent else Color.Transparent)
                    .border(if (custom) 2.dp else 1.5.dp, if (custom) C.Ink else C.Line, CircleShape)
                    .clickable { custom = true; if (isPreset) onPick(Accents.fromHue(Accents.hueOf(accent))) }
                    .semantics { contentDescription = "Custom accent" + if (custom) " on" else "" },
                contentAlignment = Alignment.Center,
            ) { if (!custom) Text("+", color = C.Muted, fontWeight = FontWeight.Black, fontSize = 18.sp) }
        }
        if (custom) HueSlider(accent, onPick)
    }
}

/** A real match row, two filter tokens and the tab bar, in [accent]. */
@Composable
private fun AccentSample(accent: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(C.Green), contentAlignment = Alignment.Center) {
                Text("W", color = C.GreenInk, fontWeight = FontWeight.Black)
            }
            LegendAvatar("Ahri", 40.dp)
            Column(Modifier.weight(1f)) {
                Text("vs Irelia", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = C.Ink)
                Text("Ahri · Best of 3", color = C.Muted, fontSize = 12.sp)
            }
            Text("2–0", fontWeight = FontWeight.Black, fontSize = 18.sp, color = C.Ink)
        }
        Row(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Might ≥ 4", "Shield").forEach {
                Text(it, color = Accents.inkOn(accent), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(accent).padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { i -> Box(Modifier.size(if (i == 0) 48.dp else 22.dp, if (i == 0) 22.dp else 8.dp).clip(RoundedCornerShape(11.dp)).background(if (i == 0) accent else C.Line)) }
        }
    }
}

@Composable
private fun HueSlider(accent: Color, onPick: (Color) -> Unit) {
    val hues = remember { (0..12).map { Accents.fromHue(it * 30f) } }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(36.dp).semantics {
            contentDescription = "Accent hue"
            progressBarRangeInfo = ProgressBarRangeInfo(Accents.hueOf(accent) / 360f, 0f..1f)
            setProgress { v -> onPick(Accents.fromHue(v * 360f)); true }
        },
    ) {
        val w = constraints.maxWidth.toFloat()
        fun pick(x: Float) = onPick(Accents.fromHue((x / w).coerceIn(0f, 1f) * 360f))
        Box(
            Modifier.fillMaxWidth().height(20.dp).align(Alignment.Center).clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(hues))
                .pointerInput(w) { detectTapGestures { pick(it.x) } }
                .pointerInput(w) { detectHorizontalDragGestures { change, _ -> change.consume(); pick(change.position.x) } },
        )
        val x = with(LocalDensity.current) { (Accents.hueOf(accent) / 360f * w).toDp() }
        Box(Modifier.offset(x = x - 15.dp).align(Alignment.CenterStart).size(30.dp).clip(CircleShape).background(Color.White).padding(3.dp).clip(CircleShape).background(accent))
    }
}
