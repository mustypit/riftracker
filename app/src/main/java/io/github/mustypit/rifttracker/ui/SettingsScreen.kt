package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.Image
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
fun SettingsScreen(vm: MainViewModel) {
    val darkness by vm.settings.darkness.collectAsState()
    val context = LocalContext.current
    // Preview art: the last legend you played, else the first legend that has art.
    val history by vm.history.collectAsState()
    val previewArt = remember(history) {
        (history.map { it.myLegend } + Legends.all.map { it.name }).asSequence()
            .mapNotNull { n -> Legends.byName(n)?.let { LegendArt.get(context, it.id) } }.firstOrNull()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderBlock(onBack = { vm.back() }) {
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
        val version = remember { AppInfo.version(context) }
        Text(
            "RiftTracker $version\nFan app, not affiliated with Riot Games · cards: OpenRift",
            color = C.Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
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

/** Hue slider, presets, a live preview and a readability check (owner's pick, concept "D"). */
@Composable
private fun AccentPicker(accent: Color, onPick: (Color) -> Unit, modifier: Modifier = Modifier) {
    val hues = remember { (0..12).map { Accents.fromHue(it * 30f) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxWidth().height(40.dp)
                .semantics {
                    contentDescription = "Accent hue"
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(Accents.hueOf(accent) / 360f, 0f..1f)
                    setProgress { v -> onPick(Accents.fromHue(v * 360f)); true }
                },
        ) {
            val w = constraints.maxWidth.toFloat()
            fun pick(x: Float) = onPick(Accents.fromHue((x / w).coerceIn(0f, 1f) * 360f))
            Box(
                Modifier.fillMaxWidth().height(22.dp).align(Alignment.Center).clip(RoundedCornerShape(11.dp))
                    .background(Brush.horizontalGradient(hues))
                    .pointerInput(w) { detectTapGestures { pick(it.x) } }
                    .pointerInput(w) { detectHorizontalDragGestures { change, _ -> change.consume(); pick(change.position.x) } },
            )
            val grey = (maxOf(accent.red, accent.green, accent.blue) - minOf(accent.red, accent.green, accent.blue)) < 0.08f
            if (!grey) {
                val x = with(androidx.compose.ui.platform.LocalDensity.current) { (Accents.hueOf(accent) / 360f * w).toDp() }
                Box(
                    Modifier.offset(x = x - 16.dp).align(Alignment.CenterStart).size(32.dp).clip(CircleShape)
                        .background(Color.White).padding(3.dp).clip(CircleShape).background(accent),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Accents.presets.forEach { (name, c) ->
                val on = c == accent
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(c)
                        .border(if (on) 3.dp else 1.dp, if (on) C.Ink else C.Line, CircleShape)
                        .clickable { onPick(c) }.semantics { contentDescription = "Accent $name" + if (on) " on" else "" },
                )
            }
        }
        val problem = Accents.problem(accent)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (problem == null) "✓" else "!", color = if (problem == null) C.Green else C.Red, fontWeight = FontWeight.Black)
            Text(problem ?: "Readable on light and dark", color = C.Ink, fontSize = 14.sp)
        }
        // Live preview: a header, two filter tokens and the tab bar's pill in the accent.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(8.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Paper)) {
                Row(Modifier.fillMaxWidth().background(C.Black).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("10", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(" matches", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("+", color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Might ≥ 4", "Shield").forEach {
                        Text(it, color = Accents.inkOn(accent), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(accent).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().background(C.Card).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    repeat(4) { i -> Box(Modifier.size(if (i == 0) 44.dp else 22.dp, if (i == 0) 20.dp else 8.dp).clip(RoundedCornerShape(10.dp)).background(if (i == 0) accent else C.Line)) }
                }
            }
        }
    }
}
