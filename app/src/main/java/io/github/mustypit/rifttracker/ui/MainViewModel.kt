package io.github.mustypit.rifttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mustypit.rifttracker.data.AppDb
import io.github.mustypit.rifttracker.data.MatchStore
import io.github.mustypit.rifttracker.game.AddResult
import io.github.mustypit.rifttracker.game.Format
import io.github.mustypit.rifttracker.game.GameRules
import io.github.mustypit.rifttracker.game.Match
import io.github.mustypit.rifttracker.game.MatchRules
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.PointKind
import io.github.mustypit.rifttracker.game.Side
import io.github.mustypit.rifttracker.stats.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** The few things tests swap out. */
object AppGraph {
    var clock: () -> Long = System::currentTimeMillis
    var random: Random = Random.Default
}

enum class Tab(val label: String) { Play("Play"), Matches("Matches"), Cards("Cards"), Legends("Legends") }

sealed interface Screen {
    data class MatchDetail(val id: Long) : Screen
    data class LegendDetail(val legend: String) : Screen
    data object LogMatch : Screen
    data object Settings : Screen
    data class CardDetail(val slug: String) : Screen
}

/** Values to start a match with (rematch fills them in). */
data class MatchSetup(val format: Format = Format.BO3, val myLegend: String = "", val oppName: String = "", val oppLegend: String = "")

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = MatchStore(AppDb.get(app).matches())
    private val now get() = AppGraph.clock()
    val settings = SettingsStore(app)

    private val _tab = MutableStateFlow(Tab.Play)
    val tab: StateFlow<Tab> = _tab.asStateFlow()
    private val _stack = MutableStateFlow<List<Screen>>(emptyList())
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    val history: StateFlow<List<Match>> = store.finished.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val period = MutableStateFlow(Period.ALL)

    /** The match being counted. Kept in memory for instant taps and saved after every change. */
    private val _active = MutableStateFlow<Match?>(null)
    val active: StateFlow<Match?> = _active.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Shown after a coin flip until the first point. */
    private val _coin = MutableStateFlow<Side?>(null)
    val coin: StateFlow<Side?> = _coin.asStateFlow()

    val setup = MutableStateFlow(MatchSetup())

    // Card library: filters and scroll position survive opening a card and coming back.
    val cardSearch = MutableStateFlow(io.github.mustypit.rifttracker.game.CardQuery())
    val cardFiltersOpen = MutableStateFlow(false)
    val cardsGrid = androidx.compose.foundation.lazy.grid.LazyGridState()

    private val saving = Mutex()

    init {
        viewModelScope.launch {
            saving.withLock { _active.value = store.activeNow() }
            _loaded.value = true
        }
    }

    // Navigation

    fun selectTab(t: Tab) { _tab.value = t; _stack.value = emptyList() }
    fun push(s: Screen) { _stack.value = _stack.value + s }
    fun back(): Boolean {
        if (_stack.value.isNotEmpty()) { _stack.value = _stack.value.dropLast(1); return true }
        if (_tab.value != Tab.Play) { _tab.value = Tab.Play; return true }
        return false
    }

    // Counting

    private fun persist() = viewModelScope.launch {
        saving.withLock {
            val m = _active.value ?: return@withLock
            val saved = store.save(m)
            if (m.id == 0L) _active.update { it?.copy(id = saved.id) }
        }
    }

    fun startMatch(s: MatchSetup) {
        setup.value = s
        _active.value = Match(format = s.format, myLegend = s.myLegend.trim(), oppName = s.oppName.trim(), oppLegend = s.oppLegend.trim(), startedAt = now)
        persist()
    }

    /** Battlefields picked on the "who goes first" card, for the game about to start. */
    private val _pendingBattlefields = MutableStateFlow<Map<Side, String>>(emptyMap())
    val pendingBattlefields: StateFlow<Map<Side, String>> = _pendingBattlefields.asStateFlow()

    fun pickPendingBattlefield(side: Side, name: String?) {
        _pendingBattlefields.update { if (name == null) it - side else it + (side to name) }
    }

    /** Starts the next game. [first] null means flip a coin. */
    fun chooseFirst(first: Side?) {
        val m = _active.value ?: return
        val who = first ?: (if (AppGraph.random.nextBoolean()) Side.ME else Side.OPP).also { _coin.value = it }
        if (first != null) _coin.value = null
        val started = MatchRules.startGame(m, who, now)
        val bfs = _pendingBattlefields.value
        _active.value = MatchRules.replaceCurrent(started, started.games.last().copy(battlefields = bfs))
        _pendingBattlefields.value = emptyMap()
        persist()
    }

    /** Sets or clears a battlefield on a saved match's game (from the match review). */
    fun setBattlefield(matchId: Long, gameIndex: Int, side: Side, name: String?) = viewModelScope.launch {
        val m = history.value.firstOrNull { it.id == matchId } ?: return@launch
        val g = m.games.getOrNull(gameIndex) ?: return@launch
        store.save(m.copy(games = m.games.toMutableList().also { it[gameIndex] = g.withBattlefield(side, name) }))
    }

    fun addPoint(side: Side, kind: PointKind) {
        val m = _active.value ?: return
        val g = MatchRules.current(m) ?: return
        val r = GameRules.add(g, side, kind, now)
        if (r is AddResult.Added) {
            _coin.value = null
            _lastRemoved.value = null // undo now means "take back this point"
            _active.value = MatchRules.replaceCurrent(m, r.game)
            persist()
        }
    }

    fun undo() {
        val m = _active.value ?: return
        val last = m.games.lastOrNull() ?: return
        // If the last thing done was taking a point off the side track, put it back.
        _lastRemoved.value?.let { (_, removed) ->
            _lastRemoved.value = null
            _active.value = MatchRules.replaceCurrent(m, GameRules.restorePoint(last, removed))
            persist()
            return
        }
        // Undo on a game that hasn't had a point yet takes back starting it.
        _active.value = if (!last.isOver && last.events.isEmpty()) m.copy(games = m.games.dropLast(1)) else MatchRules.undo(m)
        persist()
    }

    /** The last point taken off a side track, until something else happens (for undo). */
    private val _lastRemoved = MutableStateFlow<Pair<Side, GameRules.Removed>?>(null)
    val lastRemoved: StateFlow<Pair<Side, GameRules.Removed>?> = _lastRemoved.asStateFlow()

    /** Tapping a point on the side track: removes [side]'s [n]-th point (0 = oldest). */
    fun removePointAt(side: Side, n: Int) {
        val m = _active.value ?: return
        val g = m.games.lastOrNull() ?: return
        val (game, removed) = GameRules.removePoint(g, side, n, now) ?: return
        _active.value = MatchRules.replaceCurrent(m, game)
        _lastRemoved.value = side to removed
        persist()
    }

    /** XP from the pull-up panel, on the game in progress (or the one that just ended). */
    fun adjustXp(side: Side, delta: Int) {
        val m = _active.value ?: return
        val g = m.games.lastOrNull() ?: return
        _active.value = MatchRules.replaceCurrent(m, GameRules.adjustXp(g, side, delta))
        persist()
    }

    fun drawGame() {
        val m = _active.value ?: return
        val g = MatchRules.current(m) ?: return
        _active.value = MatchRules.replaceCurrent(m, GameRules.endAsDraw(g, now))
        persist()
    }

    fun abandonMatch() = viewModelScope.launch {
        saving.withLock {
            _active.value?.takeIf { it.id != 0L }?.let { store.delete(it.id) }
            _active.value = null
        }
    }

    /**
     * Saves the decided match to history and goes back to the New match screen (still filled in with
     * the same players, so a rematch is one tap). The match can be reviewed later from Matches.
     */
    fun finishMatch() = viewModelScope.launch {
        saving.withLock {
            val m = _active.value ?: return@withLock
            store.save(m.copy(endedAt = now))
            _active.value = null
        }
        _tab.value = Tab.Play
        _stack.value = emptyList()
    }

    fun rematch(m: Match) {
        _tab.value = Tab.Play
        _stack.value = emptyList()
        if (_active.value == null) startMatch(MatchSetup(m.format, m.myLegend, m.oppName, m.oppLegend))
    }

    // History

    fun logMatch(s: MatchSetup, results: List<Outcome>, firsts: List<Side?>) = viewModelScope.launch {
        store.save(MatchRules.logged(s.format, s.myLegend.trim(), s.oppName.trim(), s.oppLegend.trim(), results, firsts, now))
        back()
    }

    fun deleteMatch(id: Long) = viewModelScope.launch {
        store.delete(id)
        back()
    }
}
