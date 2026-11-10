package io.github.mustypit.rifttracker.ui

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The dark veil over legend art on the scoring screen. [darkness] runs from 0 (the lightest
 * allowed: the veil that passed the readability check at 0% of pixels under 3:1) to 1 (nearly black).
 * It can't go lighter than that, so the score always stays readable.
 */
object Veil {
    const val DEFAULT_DARKNESS = 0.35f

    /** Veil opacity behind the score (centre) and at the edges of the half. */
    fun alphas(darkness: Float): Pair<Float, Float> {
        val d = darkness.coerceIn(0f, 1f)
        return (0.66f + 0.28f * d) to (0.24f + 0.5f * d)
    }
}

/** Light or dark colours: follow the phone (default), or always one of them. */
enum class ThemeMode(val label: String) { SYSTEM("Phone"), LIGHT("Light"), DARK("Dark") }

/** App settings, kept on the phone. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _darkness = MutableStateFlow(prefs.getFloat(KEY_DARKNESS, Veil.DEFAULT_DARKNESS))
    val darkness: StateFlow<Float> = _darkness.asStateFlow()

    fun setDarkness(v: Float) {
        val d = v.coerceIn(0f, 1f)
        _darkness.value = d
        prefs.edit().putFloat(KEY_DARKNESS, d).apply()
    }

    private val _theme = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private val _accent = MutableStateFlow(
        if (prefs.contains(KEY_ACCENT_ARGB)) androidx.compose.ui.graphics.Color(prefs.getInt(KEY_ACCENT_ARGB, 0)) else Accents.DEFAULT,
    )
    val accent: StateFlow<androidx.compose.ui.graphics.Color> = _accent.asStateFlow()

    fun setAccent(c: androidx.compose.ui.graphics.Color) {
        _accent.value = c
        prefs.edit().putInt(KEY_ACCENT_ARGB, c.toArgb()).apply()
    }

    private companion object {
        const val KEY_ACCENT_ARGB = "accent_argb"
        const val KEY_DARKNESS = "veil_darkness"
        const val KEY_THEME = "theme"
    }
}
