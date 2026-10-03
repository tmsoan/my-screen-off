package com.anos.myscreenoff.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.buttonDataStore: DataStore<Preferences> by preferencesDataStore(name = "floating_button")

/** Persists the button's settings (written by the app) and its position and visibility (written by the service). */
class ButtonSettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.buttonDataStore

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    val settings: Flow<ButtonSettings> = preferences.map { it.toButtonSettings() }.distinctUntilChanged()

    /** False after the button was dragged away, until the app is opened again. */
    val visible: Flow<Boolean> = preferences.map { it[Keys.visible] ?: true }.distinctUntilChanged()

    suspend fun current(): ButtonSettings = settings.first()

    suspend fun position(): ButtonPosition {
        val defaults = ButtonPosition()
        val saved = preferences.first()
        return ButtonPosition(
            xFraction = saved[Keys.xFraction] ?: defaults.xFraction,
            yFraction = saved[Keys.yFraction] ?: defaults.yFraction,
        )
    }

    suspend fun save(settings: ButtonSettings) {
        dataStore.edit { it.write(settings) }
    }

    suspend fun savePosition(position: ButtonPosition) {
        dataStore.edit {
            it[Keys.xFraction] = position.xFraction
            it[Keys.yFraction] = position.yFraction
        }
    }

    suspend fun setVisible(visible: Boolean) {
        dataStore.edit { it[Keys.visible] = visible }
    }

    private object Keys {
        val sizeDp = floatPreferencesKey("size_dp")
        val color = intPreferencesKey("color")
        val opacity = floatPreferencesKey("opacity")
        val fadeWhenIdle = booleanPreferencesKey("fade_when_idle")
        val idleOpacity = floatPreferencesKey("idle_opacity")
        val fadeDelayMs = intPreferencesKey("fade_delay_ms")
        val lockDelayMs = intPreferencesKey("lock_delay_ms")
        val snapToEdge = booleanPreferencesKey("snap_to_edge")
        val haptic = booleanPreferencesKey("haptic")
        val visible = booleanPreferencesKey("visible")
        val xFraction = floatPreferencesKey("x_fraction")
        val yFraction = floatPreferencesKey("y_fraction")
    }

    private fun Preferences.toButtonSettings(): ButtonSettings {
        val defaults = ButtonSettings()
        return ButtonSettings(
            sizeDp = this[Keys.sizeDp] ?: defaults.sizeDp,
            color = this[Keys.color] ?: defaults.color,
            opacity = this[Keys.opacity] ?: defaults.opacity,
            fadeWhenIdle = this[Keys.fadeWhenIdle] ?: defaults.fadeWhenIdle,
            idleOpacity = this[Keys.idleOpacity] ?: defaults.idleOpacity,
            fadeDelayMs = this[Keys.fadeDelayMs] ?: defaults.fadeDelayMs,
            lockDelayMs = this[Keys.lockDelayMs] ?: defaults.lockDelayMs,
            snapToEdge = this[Keys.snapToEdge] ?: defaults.snapToEdge,
            haptic = this[Keys.haptic] ?: defaults.haptic,
        )
    }

    private fun MutablePreferences.write(settings: ButtonSettings) {
        this[Keys.sizeDp] = settings.sizeDp
        this[Keys.color] = settings.color
        this[Keys.opacity] = settings.opacity
        this[Keys.fadeWhenIdle] = settings.fadeWhenIdle
        this[Keys.idleOpacity] = settings.idleOpacity
        this[Keys.fadeDelayMs] = settings.fadeDelayMs
        this[Keys.lockDelayMs] = settings.lockDelayMs
        this[Keys.snapToEdge] = settings.snapToEdge
        this[Keys.haptic] = settings.haptic
    }
}
