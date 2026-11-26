package io.github.mustypit.rifttracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import io.github.mustypit.rifttracker.data.CardUpdates
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.key

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Card, legend and battlefield data: bundled, then anything newer downloaded from OpenRift.
        CardUpdates.installStartup(this)
        setContent {
            val mode by vm.settings.theme.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val accent by vm.settings.accent.collectAsState()
            // New card data from OpenRift redraws the app with it.
            val cards by CardUpdates.version.collectAsState()
            RiftTheme(dark, accent) { key(cards) { AppShell(vm) } }
        }
    }

    override fun onStart() {
        super.onStart()
        // Checks OpenRift for new cards whenever the app opens (at most once an hour).
        lifecycleScope.launch { CardUpdates.check(applicationContext, AppGraph.clock()) }
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
    // The tab bar sits underneath the screens, so opening a screen never resizes the one behind it.
    var barHeight by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(C.Paper)) {
        NavigationBar(containerColor = C.Card, modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = it.height }) {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = t == tab,
                    onClick = { vm.selectTab(t) },
                    icon = {
                        Icon(
                            when (t) { Tab.Play -> RiftIcons.CrossedSwords; Tab.Matches -> Icons.AutoMirrored.Filled.FormatListBulleted; Tab.Cards -> RiftIcons.Cards; Tab.Decks -> RiftIcons.Deck; Tab.Settings -> Icons.Default.Settings },
                            null,
                        )
                    },
                    label = { Text(t.label, maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = C.Accent, selectedIconColor = C.OnAccent, selectedTextColor = C.Ink, unselectedIconColor = C.Muted, unselectedTextColor = C.Muted),
                )
            }
        }
        AnimatedContent(
            targetState = Destination(tab, top, stack.size),
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { screenTransition(initialState.depth, targetState.depth) },
            contentKey = { it.tab to it.screen },
            label = "screens",
        ) { d ->
            // A tab's main screen stops above the bar; a screen pushed on top covers it.
            val pad = if (d.screen == null) with(LocalDensity.current) { barHeight.toDp() } else 0.dp
            Box(Modifier.fillMaxSize().padding(bottom = pad).background(C.Paper)) {
                when (val s = d.screen) {
                    is Screen.MatchDetail -> MatchDetailScreen(vm, s.id)
                    is Screen.LegendDetail -> LegendDetailScreen(vm, s.legend)
                    Screen.LogMatch -> LogMatchScreen(vm)
                    Screen.Settings -> SettingsScreen(vm, onBack = { vm.back() })
                    is Screen.CardDetail -> CardDetailScreen(vm, s.slug)
                    Screen.NewDeck -> NewDeckScreen(vm)
                    is Screen.DeckDetail -> DeckScreen(vm, s.id)
                    is Screen.DeckAddCards -> CardsScreen(vm, deckId = s.id)
                    null -> when (d.tab) {
                        Tab.Play -> if (loaded) NewMatchScreen(vm)
                        Tab.Matches -> MatchesScreen(vm)
                        Tab.Cards -> CardsScreen(vm)
                        Tab.Decks -> DecksScreen(vm)
                        Tab.Settings -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}

/** What fills the area above the tab bar: a tab's main screen, or a screen pushed on top. */
private data class Destination(val tab: Tab, val screen: Screen?, val depth: Int)
