package com.anos.myscreenoff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.anos.myscreenoff.data.ButtonSettingsRepository
import com.anos.myscreenoff.ui.SettingsScreen
import com.anos.myscreenoff.ui.theme.MyScreenOffTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyScreenOffTheme {
                SettingsScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app brings back a button that was dragged away.
        lifecycleScope.launch { ButtonSettingsRepository(applicationContext).setVisible(true) }
    }
}
