package com.fantasyidler.tensura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fantasyidler.tensura.data.TensuraRepository
import com.fantasyidler.tensura.domain.MasteryLevel

@Composable
fun TensuraHubScreen(repo: TensuraRepository) {
    val state by repo.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val emojis = listOf("⚔", "✨", "🛒", "⏳")
                val labels = listOf("Combate", "Skills", "Tienda", "Cola")
                emojis.forEachIndexed { index, emoji ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(emoji, style = MaterialTheme.typography.titleMedium) },
                        label = { Text(labels[index], style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            HubHeader(ph = state.ph, coins = state.coins)
            when (tab) {
                0 -> CombatTab(repo = repo, state = state)
                1 -> SkillsTab(repo = repo, state = state)
                2 -> ShopTab(repo = repo, state = state)
                else -> QueueTab(repo = repo, state = state)
            }
        }
    }
}

@Composable
private fun HubHeader(ph: Long, coins: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "IDLE TENsura",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Sistema de Habilidades · Entry 4",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatPill("PH", ph.toString(), MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.size(6.dp))
        StatPill("Oro", coins.toString(), MaterialTheme.colorScheme.secondary)
    }
}

@Composable
internal fun StatPill(label: String, value: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
internal fun Bar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

internal fun masteryColor(level: MasteryLevel): Color = when (level) {
    MasteryLevel.BASIC -> Color(0xFF9E9E9E)
    MasteryLevel.ADVANCED -> Color(0xFF42A5F5)
    MasteryLevel.MASTER -> Color(0xFFFFB300)
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) minutes.toString() + "m " + seconds + "s" else seconds.toString() + "s"
}

internal fun formatMultiplier(value: Double): String {
    return String.format(java.util.Locale.US, "%.2f", value)
}
