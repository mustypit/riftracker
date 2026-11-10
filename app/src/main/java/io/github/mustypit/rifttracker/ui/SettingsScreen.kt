package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.Image
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
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Accent.entries.forEach { a ->
                val on = a == accent
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(a.color)
                        .border(if (on) 3.dp else 0.dp, if (on) C.Ink else Color.Transparent, CircleShape)
                        .clickable { vm.settings.setAccent(a) }
                        .semantics { contentDescription = "Accent ${a.label}" + if (on) " on" else "" },
                    contentAlignment = Alignment.Center,
                ) { if (on) Text("✓", color = a.ink, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            }
        }
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
