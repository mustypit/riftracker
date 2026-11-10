package io.github.mustypit.rifttracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.mustypit.rifttracker.game.Legends

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Legend and battlefield data from OpenRift, if the app was built with it (scripts/build_legend_assets.py).
        runCatching { assets.open("legends/legends.json").bufferedReader().use { Legends.install(it.readText()) } }
        runCatching { assets.open("cards/cards.json").bufferedReader().use { io.github.mustypit.rifttracker.game.Cards.install(it.readText()) } }
        runCatching { assets.open("battlefields/battlefields.json").bufferedReader().use { io.github.mustypit.rifttracker.game.Battlefields.install(it.readText()) } }
        setContent {
            val mode by vm.settings.theme.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val accent by vm.settings.accent.collectAsState()
            RiftTheme(dark, accent) { AppShell(vm) }
        }
    }
}

@Composable
fun AppShell(vm: MainViewModel) {
    val tab by vm.tab.collectAsState()
    val stack by vm.stack.collectAsState()
    val active by vm.active.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val top = stack.lastOrNull()
    val counting = tab == Tab.Play && top == null && active != null

    BackHandler(enabled = top != null || tab != Tab.Play) { vm.back() }

    // The counter takes the whole screen; everything else has the tab bar.
    if (counting) {
        // Edge to edge: the art fills the whole display, under where the system bars were.
        Box(Modifier.fillMaxSize().background(C.Black)) { CounterScreen(vm, active!!) }
        return
    }
    Scaffold(
        containerColor = C.Paper,
        // Screens handle the status bar themselves (header blocks draw behind it).
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (top == null) NavigationBar(containerColor = C.Card) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { vm.selectTab(t) },
                        icon = {
                            Icon(
                                when (t) { Tab.Play -> RiftIcons.CrossedSwords; Tab.Matches -> Icons.Default.FormatListBulleted; Tab.Cards -> RiftIcons.Cards; Tab.Legends -> Icons.Default.BarChart },
                                null,
                            )
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = C.Accent, selectedIconColor = C.OnAccent, selectedTextColor = C.Ink, unselectedIconColor = C.Muted, unselectedTextColor = C.Muted),
                    )
                }
            }
        },
    ) { padding ->
        // Screens slide in when opened and out when going back; tab switches crossfade.
        androidx.compose.animation.AnimatedContent(
            targetState = Destination(tab, top, stack.size),
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { screenTransition(initialState.depth, targetState.depth) },
            contentKey = { it.tab to it.screen },
            label = "screens",
        ) { d ->
            Box(Modifier.fillMaxSize().background(C.Paper)) {
                when (val s = d.screen) {
                    is Screen.MatchDetail -> MatchDetailScreen(vm, s.id)
                    is Screen.LegendDetail -> LegendDetailScreen(vm, s.legend)
                    Screen.LogMatch -> LogMatchScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    is Screen.CardDetail -> CardDetailScreen(vm, s.slug)
                    null -> when (d.tab) {
                        Tab.Play -> if (loaded) NewMatchScreen(vm)
                        Tab.Matches -> MatchesScreen(vm)
                        Tab.Cards -> CardsScreen(vm)
                        Tab.Legends -> LegendsScreen(vm)
                    }
                }
            }
        }
    }
}

/** What fills the area above the tab bar: a tab's main screen, or a screen pushed on top. */
private data class Destination(val tab: Tab, val screen: Screen?, val depth: Int)
