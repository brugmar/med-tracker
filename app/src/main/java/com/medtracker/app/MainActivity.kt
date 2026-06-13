package com.medtracker.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.medtracker.app.ui.MedTrackerApp
import com.medtracker.app.ui.theme.MedTrackerTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[AppViewModel::class.java]
        // First-frame default; the effect below refines it once theme is resolved.
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Re-apply edge-to-edge whenever the resolved theme flips so the
            // system-bar icons keep their contrast against the app background —
            // the in-app override can disagree with the system setting.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(dark),
                    navigationBarStyle = systemBarStyle(dark)
                )
                onDispose {}
            }
            MedTrackerTheme(darkTheme = dark) {
                MedTrackerApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Keep "today" correct if the app stayed open across midnight.
        viewModel.refreshToday()
    }
}

/** Transparent bars with icons tinted for a dark or light app background. */
private fun systemBarStyle(dark: Boolean): SystemBarStyle =
    if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
