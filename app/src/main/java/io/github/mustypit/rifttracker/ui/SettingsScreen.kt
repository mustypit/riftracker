package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.Image
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
            Text("SETTINGS", style = Caps, color = C.Yellow)
            Text("Make it yours", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        }
        SectionLabel("Scoring screen")
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp)) {
            Text("Background darkness", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(
                "How much the legend art is dimmed behind the score. Even the lightest setting keeps the score easy to read.",
                color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
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
            Row(Modifier.fillMaxWidth()) {
                Text("Lighter", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("${Math.round(darkness * 100)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Darker", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        SectionLabel("About")
        val about = remember { AppInfo.read(context) }
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp)) {
            Text("RiftTracker ${about.versionName} · build ${about.versionCode}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(
                "Signing key " + (about.certSha256?.let { "${it.take(8)}…${it.takeLast(8)}" } ?: "unknown"),
                color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (about.certSha256 == AppInfo.UPDATE_KEY_SHA256) "✓ Updates install over this copy"
                else "⚠ Not the update key: the next build won't install over this copy",
                color = if (about.certSha256 == AppInfo.UPDATE_KEY_SHA256) C.Teal else C.Coral,
                fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
            Text("Unofficial fan app, not affiliated with Riot Games. Card data from Riftcodex.", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
    }
}

/** Version and signing certificate of the installed app (to tell which key a copy has). */
object AppInfo {
    /** SHA-256 of the certificate every CI build must be signed with (checked in build.yml too). */
    const val UPDATE_KEY_SHA256 = "f3a6406a295f2e5fd75a7706f49dbac721719c57164a3c0e5cf00684e4d84307"

    data class Info(val versionName: String, val versionCode: Long, val certSha256: String?)

    fun read(context: android.content.Context): Info {
        val pm = context.packageManager
        val info = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
        }.getOrNull()
        val sig = info?.let {
            if (android.os.Build.VERSION.SDK_INT >= 28) it.signingInfo?.apkContentsSigners?.firstOrNull()
            else @Suppress("DEPRECATION") it.signatures?.firstOrNull()
        }
        val sha = sig?.let { s -> java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
        val code = info?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } ?: 0
        return Info(info?.versionName ?: "?", code, sha)
    }
}
