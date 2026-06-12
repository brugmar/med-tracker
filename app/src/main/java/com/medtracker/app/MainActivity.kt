package com.medtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.medtracker.app.ui.MedTrackerApp
import com.medtracker.app.ui.theme.MedTrackerTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[AppViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            MedTrackerTheme {
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
