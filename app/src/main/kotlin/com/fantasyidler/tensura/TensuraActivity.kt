package com.fantasyidler.tensura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.fantasyidler.tensura.data.TensuraRepository
import com.fantasyidler.tensura.ui.TensuraHubScreen

class TensuraActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = TensuraRepository.get(applicationContext)
        repo.applyOfflineProgress()
        setContent {
            MaterialTheme(colorScheme = TensuraColorScheme) {
                TensuraHubScreen(repo = repo)
            }
        }
    }
}

private val TensuraColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF00303A),
    tertiary = Color(0xFFFFC400),
    onTertiary = Color(0xFF3A2B00),
    background = Color(0xFF0E0B16),
    onBackground = Color(0xFFEDE7FF),
    surface = Color(0xFF171226),
    onSurface = Color(0xFFEDE7FF),
    surfaceVariant = Color(0xFF241C3A),
    onSurfaceVariant = Color(0xFFB9AEDC),
    error = Color(0xFFFF5370),
    onError = Color(0xFF2B0009),
)
