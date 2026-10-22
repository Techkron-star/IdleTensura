package com.fantasyidler.tensura.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fantasyidler.tensura.data.TensuraRepository
import com.fantasyidler.tensura.domain.CombatState
import com.fantasyidler.tensura.domain.EnemyCatalog
import com.fantasyidler.tensura.domain.PlayerAction
import com.fantasyidler.tensura.domain.QueueSlot
import com.fantasyidler.tensura.domain.TensuraState
import kotlinx.coroutines.delay

@Composable
fun QueueTab(repo: TensuraRepository, state: TensuraState) {
    var durationMs by remember { mutableLongStateOf(60000L) }
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            repo.tickQueue()
            delay(1000L)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionTitle("Colas automáticas (" + state.queue.count { it.active } + "/5 activas)")
            Text(
                text = "Las sesiones se reinician solas y acumulan recompensas. Recógelas cuando quieras: no hay prisa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Duración de sesión:", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(60000L, 300000L, 900000L).forEach { option ->
                    val selected = durationMs == option
                    OutlinedButton(
                        onClick = { durationMs = option },
                        colors = if (selected) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text((option / 60000L).toString() + " min", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            TextButton(onClick = { message = repo.collectAll() }) {
                Text("Recoger todo")
            }
            message?.let { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        items(state.queue, key = { it.index }) { slot ->
            QueueSlotCard(
                slot = slot,
                now = now,
                owned = state.owned.keys.toList(),
                nameOf = { id -> repo.catalog[id]?.name ?: id },
                onStart = { skillId -> message = repo.startQueue(slot.index, skillId, durationMs) },
                onStop = { message = repo.stopQueue(slot.index) },
                onCollect = { message = repo.collectQueue(slot.index) },
            )
        }
        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun QueueSlotCard(
    slot: QueueSlot,
    now: Long,
    owned: List<String>,
    nameOf: (String) -> String,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
    onCollect: () -> Unit,
) {
    val pending = slot.pendingPh + slot.pendingCoins + slot.pendingXp.toLong()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Slot " + (slot.index + 1), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when {
                        slot.active -> "En curso"
                        pending > 0L -> "Listo para recoger"
                        else -> "Libre"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (slot.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
            if (slot.active && slot.skillId != null) {
                Text(nameOf(slot.skillId), style = MaterialTheme.typography.bodyMedium)
                Bar(progress = slot.progress(now), color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Quedan " + formatTime(slot.remainingMs(now)),
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = onStop) { Text("Detener (conserva lo pendiente)") }
            } else {
                Text("Elige una habilidad para entrenar:", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (owned.isEmpty()) {
                        Text(
                            text = "Primero aprende una habilidad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    owned.forEach { id ->
                        OutlinedButton(
                            onClick = { onStart(id) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(nameOf(id), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (pending > 0L) {
                Text(
                    text = "Pendiente: " + slot.pendingPh + " PH · " + slot.pendingCoins + " oro · " + slot.pendingXp + " XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Button(onClick = onCollect, modifier = Modifier.padding(top = 4.dp)) { Text("Recoger") }
            }
        }
    }
}

@Composable
fun CombatTab(repo: TensuraRepository, state: TensuraState) {
    val combat by repo.combat.state.collectAsStateWithLifecycle()
    var showSkills by remember { mutableStateOf(false) }

    if (combat.active) {
        CombatActive(
            repo = repo,
            state = state,
            combat = combat,
            showSkills = showSkills,
            setShowSkills = { showSkills = it },
        )
    } else {
        CombatHome(repo = repo, combat = combat)
    }
}

@Composable
private fun CombatHome(repo: TensuraRepository, combat: CombatState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (combat.victory || combat.defeat) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (combat.victory) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                        },
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (combat.victory) "🎉 ¡Victoria!" else "💀 Derrota",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (combat.victory) {
                            Text(
                                text = "+" + combat.rewardCoins + " oro · +" + combat.rewardPh + " PH · +" + combat.rewardSkillXp + " XP de skill",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { repo.combat.reset() }) { Text("Cerrar") }
                    }
                }
            }
        }
        item {
            SectionTitle("Elige un enemigo")
            Text(
                text = "Combate por turnos: tú decides cada acción. No es automático.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(EnemyCatalog.enemies, key = { it.id }) { enemy ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(enemy.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = enemy.tier + " · " + enemy.hp + " HP · ATK " + enemy.attack + " · DEF " + enemy.defense,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Recompensa: " + enemy.rewardCoins + " oro · " + enemy.rewardPh + " PH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Button(onClick = { repo.combat.start(enemy) }) { Text("Luchar") }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun CombatActive(
    repo: TensuraRepository,
    state: TensuraState,
    combat: CombatState,
    showSkills: Boolean,
    setShowSkills: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val enemyName = combat.enemy?.name ?: "Enemigo"
        val enemyMax = combat.enemy?.hp ?: 1

        Text(
            text = enemyName + " · turno " + combat.turn,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Bar(progress = combat.enemyHp.toFloat() / enemyMax.toFloat(), color = MaterialTheme.colorScheme.error)
        Text(
            text = combat.enemyHp.toString() + "/" + enemyMax + " HP",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text("Tú", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Bar(progress = combat.playerHp.toFloat() / combat.playerMaxHp.toFloat(), color = MaterialTheme.colorScheme.primary)
        Text(
            text = combat.playerHp.toString() + "/" + combat.playerMaxHp + " HP · " + combat.playerMp + "/" + combat.playerMaxMp + " MP",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(combat.log) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (combat.victory || combat.defeat) {
            Button(
                onClick = { repo.combat.reset() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Continuar")
            }
        } else {
            ActionRow(
                left = "⚔ Atacar" to { repo.combat.act(PlayerAction.ATTACK) },
                right = "💥 Pesado" to { repo.combat.act(PlayerAction.HEAVY_ATTACK) },
            )
            ActionRow(
                left = "🛡 Defender" to { repo.combat.act(PlayerAction.DEFEND) },
                right = "💨 Esquivar" to { repo.combat.act(PlayerAction.DODGE) },
            )
            ActionRow(
                left = "✨ Skills" to { setShowSkills(!showSkills) },
                right = "🧪 Poción (" + combat.potions + ")" to { repo.combat.act(PlayerAction.ITEM) },
            )
            if (showSkills) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.owned.keys.forEach { id ->
                        OutlinedButton(onClick = {
                            repo.combat.act(PlayerAction.SKILL, id)
                            setShowSkills(false)
                        }) {
                            Text(repo.catalog[id]?.name ?: id, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (state.owned.isEmpty()) {
                        Text(
                            text = "No tienes habilidades aprendidas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(left: Pair<String, () -> Unit>, right: Pair<String, () -> Unit>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(onClick = left.second, modifier = Modifier.weight(1f)) {
            Text(left.first, style = MaterialTheme.typography.labelMedium)
        }
        Button(onClick = right.second, modifier = Modifier.weight(1f)) {
            Text(right.first, style = MaterialTheme.typography.labelMedium)
        }
    }
}
