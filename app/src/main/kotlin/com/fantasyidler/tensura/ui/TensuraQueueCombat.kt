package com.fantasyidler.tensura.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fantasyidler.tensura.data.TensuraRepository
import com.fantasyidler.tensura.domain.CombatState
import com.fantasyidler.tensura.domain.EnemyCatalog
import com.fantasyidler.tensura.domain.EnemyDef
import com.fantasyidler.tensura.domain.OwnedSkill
import com.fantasyidler.tensura.domain.PlayerAction
import com.fantasyidler.tensura.domain.QueueSlot
import com.fantasyidler.tensura.domain.SkillDefinition
import com.fantasyidler.tensura.domain.TensuraState
import com.fantasyidler.tensura.domain.label
import kotlinx.coroutines.delay

@Composable
fun QueueTab(repo: TensuraRepository, state: TensuraState) {
    var durationMs by remember { mutableLongStateOf(60000L) }
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            repo.tickQueue()
            delay(1000L)
        }
    }

    val activeCount = state.queue.count { it.active }
    val readyCount = state.queue.count { slot ->
        slot.pendingPh > 0L || slot.pendingCoins > 0L || slot.pendingXp > 0
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionTitle("Colas automáticas (" + activeCount + "/5 activas)")
            Text(
                text = "Las sesiones se reinician solas y acumulan recompensas. Recógelas cuando quieras: no hay prisa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Duración de sesión", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
            ) {
                listOf(60000L, 300000L, 900000L).forEach { option ->
                    FilterChip(
                        selected = durationMs == option,
                        onClick = { durationMs = option },
                        label = { Text((option / 60000L).toString() + " min") },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { message = repo.collectAll() },
                    enabled = readyCount > 0,
                ) {
                    Text("Recoger todo (" + readyCount + ")")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Toca un slot para elegir habilidad",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message?.let { text ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
        items(state.queue, key = { it.index }) { slot ->
            QueueSlotCard(
                slot = slot,
                now = now,
                nameOf = { id -> repo.catalog[id]?.name ?: id },
                onPick = { pickerSlot = slot.index },
                onStop = { message = repo.stopQueue(slot.index) },
                onCollect = { message = repo.collectQueue(slot.index) },
            )
        }
        item { Spacer(modifier = Modifier.height(28.dp)) }
    }

    val slotIndex = pickerSlot
    if (slotIndex != null) {
        SkillPickerDialog(
            title = "Entrenar en el slot " + (slotIndex + 1),
            skills = state.owned.keys.mapNotNull { repo.catalog[it] }.sortedBy { it.rank.ordinal },
            owned = state.owned,
            onDismiss = { pickerSlot = null },
            onSelect = { id ->
                message = repo.startQueue(slotIndex, id, durationMs)
                pickerSlot = null
            },
        )
    }
}

@Composable
private fun QueueSlotCard(
    slot: QueueSlot,
    now: Long,
    nameOf: (String) -> String,
    onPick: () -> Unit,
    onStop: () -> Unit,
    onCollect: () -> Unit,
) {
    val pending = slot.pendingPh > 0L || slot.pendingCoins > 0L || slot.pendingXp > 0
    val statusColor = when {
        slot.active -> MaterialTheme.colorScheme.primary
        pending -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when {
        slot.active -> "En curso"
        pending -> "Listo para recoger"
        else -> "Libre"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = !slot.active, onClick = onPick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Slot " + (slot.index + 1), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            if (slot.active && slot.skillId != null) {
                Text(
                    text = nameOf(slot.skillId),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Bar(progress = slot.progress(now), color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Quedan " + formatTime(slot.remainingMs(now)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Sesiones: " + slot.completedSessions,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onStop) { Text("Detener (conserva lo pendiente)") }
            } else {
                Text(
                    text = if (slot.skillId != null) "Última: " + nameOf(slot.skillId) else "Sin habilidad asignada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(onClick = onPick, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Elegir habilidad")
                }
            }

            if (pending) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pendiente",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = slot.pendingPh.toString() + " PH · " + slot.pendingCoins + " oro · " + slot.pendingXp + " XP",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = onCollect) { Text("Recoger") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillPickerDialog(
    title: String,
    skills: List<SkillDefinition>,
    owned: Map<String, OwnedSkill>,
    mpAvailable: Int? = null,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text(title) },
        text = {
            if (skills.isEmpty()) {
                Text("Primero aprende una habilidad en la pestaña Skills.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(skills, key = { it.id }) { def ->
                        val own = owned[def.id]
                        val affordable = mpAvailable == null || def.mpCost <= mpAvailable
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable(enabled = affordable) { onSelect(def.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = def.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (affordable) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        },
                                    )
                                    Text(
                                        text = def.rank.label + " · " + def.type.label +
                                            (own?.let { " · " + it.mastery.label } ?: "") +
                                            (if (def.basePower > 0) " · Poder " + def.basePower else ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "MP " + def.mpCost,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (affordable) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
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
            EnemyCard(enemy = enemy, onFight = { repo.combat.start(enemy) })
        }
        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun EnemyCard(enemy: EnemyDef, onFight: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spriteForEnemy(enemy.id),
                    fontSize = 30.sp,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(enemy.name, fontWeight = FontWeight.Bold)
                    Text(
                        text = enemy.tier,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Button(onClick = onFight) { Text("Luchar") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MiniStat("HP", enemy.hp.toString(), MaterialTheme.colorScheme.error)
                MiniStat("ATK", enemy.attack.toString(), MaterialTheme.colorScheme.primary)
                MiniStat("DEF", enemy.defense.toString(), MaterialTheme.colorScheme.secondary)
                MiniStat("VEL", enemy.speed.toString(), MaterialTheme.colorScheme.tertiary)
            }
            Text(
                text = "Recompensa: " + enemy.rewardCoins + " oro · " + enemy.rewardPh + " PH · " + enemy.rewardSkillXp + " XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
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
    val enemy = combat.enemy
    val enemyName = enemy?.name ?: "Enemigo"
    val enemyMax = enemy?.hp ?: 1
    val latest = combat.log.lastOrNull().orEmpty()
    var typed by remember(latest) { mutableIntStateOf(0) }
    var showLog by remember { mutableStateOf(false) }

    LaunchedEffect(latest) {
        typed = 0
        while (typed < latest.length) {
            delay(14L)
            typed++
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0E2A16), Color(0xFF2E7D32))))
                .padding(10.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.Top) {
                    BattleInfoCard(
                        name = enemyName,
                        subtitle = enemy?.tier ?: "",
                        hp = combat.enemyHp,
                        maxHp = enemyMax,
                        mp = null,
                        maxMp = null,
                        stats = enemy?.let { "ATK " + it.attack + " · DEF " + it.defense + " · VEL " + it.speed } ?: "",
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = spriteForEnemy(enemy?.id),
                        fontSize = 52.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "🧙",
                        fontSize = 52.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    BattleInfoCard(
                        name = "Tú",
                        subtitle = "Turno " + combat.turn,
                        hp = combat.playerHp,
                        maxHp = combat.playerMaxHp,
                        mp = combat.playerMp,
                        maxMp = combat.playerMaxMp,
                        stats = "ATK " + state.stats.attack + " · DEF " + state.stats.defense + " · VEL " + state.stats.speed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (combat.log.size >= 2) {
                        Text(
                            text = combat.log[combat.log.size - 2],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = latest.take(typed),
                        style = MaterialTheme.typography.bodyMedium,
                        minLines = 2,
                    )
                }
                TextButton(onClick = { showLog = true }) { Text("📜") }
            }
        }

        if (combat.victory || combat.defeat) {
            Button(
                onClick = { repo.combat.reset() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(48.dp),
            ) {
                Text(if (combat.victory) "Continuar" else "Reintentar")
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BattleButton("⚔ Atacar", Color(0xFFD32F2F), Modifier.weight(1f)) {
                    repo.combat.act(PlayerAction.ATTACK)
                }
                BattleButton("✨ Habilidad", Color(0xFF7B1FA2), Modifier.weight(1f)) {
                    setShowSkills(true)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BattleButton("🛡 Defender", Color(0xFF1565C0), Modifier.weight(1f)) {
                    repo.combat.act(PlayerAction.DEFEND)
                }
                BattleButton("🧪 Poción (" + combat.potions + ")", Color(0xFF2E7D32), Modifier.weight(1f)) {
                    repo.combat.act(PlayerAction.ITEM)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BattleButton("💥 Pesado", Color(0xFFEF6C00), Modifier.weight(1f)) {
                    repo.combat.act(PlayerAction.HEAVY_ATTACK)
                }
                BattleButton("💨 Esquivar", Color(0xFF00897B), Modifier.weight(1f)) {
                    repo.combat.act(PlayerAction.DODGE)
                }
            }
        }
    }

    if (showSkills) {
        SkillPickerDialog(
            title = "Usar habilidad",
            skills = state.owned.keys.mapNotNull { repo.catalog[it] }.sortedBy { it.rank.ordinal },
            owned = state.owned,
            mpAvailable = combat.playerMp,
            onDismiss = { setShowSkills(false) },
            onSelect = { id ->
                repo.combat.act(PlayerAction.SKILL, id)
                setShowSkills(false)
            },
        )
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Cerrar") } },
            title = { Text("Registro de combate") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(combat.log) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun BattleInfoCard(
    name: String,
    subtitle: String,
    hp: Int,
    maxHp: Int,
    mp: Int?,
    maxMp: Int?,
    stats: String,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color(0xCC06140A), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB9F6CA),
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val hpRatio = if (maxHp <= 0) 0f else hp.toFloat() / maxHp.toFloat()
            BattleBar(progress = hpRatio, color = hpColor(hpRatio))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "HP " + hp + "/" + maxHp,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                if (mp != null && maxMp != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "MP " + mp + "/" + maxMp,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF90CAF9),
                    )
                }
            }
            if (mp != null && maxMp != null) {
                val mpRatio = if (maxMp <= 0) 0f else mp.toFloat() / maxMp.toFloat()
                BattleBar(progress = mpRatio, color = Color(0xFF42A5F5))
            }
            Text(
                text = stats,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFC8E6C9),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BattleBar(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x66000000)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

@Composable
private fun BattleButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun hpColor(ratio: Float): Color = when {
    ratio > 0.5f -> Color(0xFF66BB6A)
    ratio > 0.2f -> Color(0xFFFFCA28)
    else -> Color(0xFFEF5350)
}

private fun spriteForEnemy(id: String?): String = when (id) {
    "slime_01" -> "🟢"
    "goblin_01" -> "👹"
    "direwolf_01" -> "🐺"
    "orc_lord" -> "🐗"
    "charybdis" -> "🌀"
    "demon_lord" -> "😈"
    else -> "👾"
}
