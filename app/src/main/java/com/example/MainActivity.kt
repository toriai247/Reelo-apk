package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.ReeloNavGraph
import com.example.ui.theme.ReeloTheme
import com.example.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ReeloApplication).container

        setContent {
            val themeMode by appContainer.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            ReeloTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    ReeloNavGraph(
                        navController = navController,
                        container = appContainer,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
