package com.anos.myscreenoff.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anos.myscreenoff.data.ButtonSettings
import com.anos.myscreenoff.data.ButtonSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ButtonSettingsRepository(application)

    private val _settings = MutableStateFlow<ButtonSettings?>(null)

    /** The settings shown on screen, or null while the saved ones load. */
    val settings: StateFlow<ButtonSettings?> = _settings.asStateFlow()

    /** The latest settings to save, which the service then applies to the button. */
    private val committed = MutableStateFlow<ButtonSettings?>(null)

    init {
        viewModelScope.launch {
            val saved = repository.current()
            _settings.value = saved
            committed.value = saved
            // StateFlow is conflated, so rapid changes collapse into one save of the newest settings.
            committed.filterNotNull().drop(1).collect { repository.save(it) }
        }
    }

    /** Changes only the preview, e.g. while a slider is dragged. Call [commit] when done. */
    fun preview(transform: (ButtonSettings) -> ButtonSettings) {
        _settings.update { it?.let(transform) }
    }

    /** Changes the settings and applies them to the button. */
    fun change(transform: (ButtonSettings) -> ButtonSettings) {
        preview(transform)
        commit()
    }

    fun commit() {
        committed.value = _settings.value
    }

    fun reset() = change { ButtonSettings() }
}
