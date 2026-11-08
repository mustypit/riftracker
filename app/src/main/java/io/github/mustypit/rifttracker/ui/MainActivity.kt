package io.github.mustypit.rifttracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
        // Legend data from Riftcodex, if the app was built with it (scripts/build_legend_assets.py).
        runCatching { assets.open("legends/legends.json").bufferedReader().use { Legends.install(it.readText()) } }
        setContent { RiftTheme { AppShell(vm) } }
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
        Box(Modifier.fillMaxSize().safeDrawingPadding()) { CounterScreen(vm, active!!) }
        return
    }
    Scaffold(
        containerColor = C.Paper,
        // Screens handle the status bar themselves (header blocks draw behind it).
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (top == null) NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { vm.selectTab(t) },
                        icon = {
                            Icon(
                                when (t) { Tab.Play -> RiftIcons.CrossedSwords; Tab.Matches -> Icons.Default.FormatListBulleted; Tab.Legends -> Icons.Default.BarChart },
                                null,
                            )
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = C.Yellow, selectedIconColor = C.Ink, selectedTextColor = C.Ink),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (top) {
                is Screen.MatchDetail -> MatchDetailScreen(vm, top.id)
                is Screen.LegendDetail -> LegendDetailScreen(vm, top.legend)
                Screen.LogMatch -> LogMatchScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                null -> when (tab) {
                    Tab.Play -> if (loaded) NewMatchScreen(vm)
                    Tab.Matches -> MatchesScreen(vm)
                    Tab.Legends -> LegendsScreen(vm)
                }
            }
        }
    }
}
