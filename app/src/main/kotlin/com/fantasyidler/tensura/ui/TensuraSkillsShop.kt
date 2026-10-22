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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.tensura.data.TensuraRepository
import com.fantasyidler.tensura.domain.MasteryLevel
import com.fantasyidler.tensura.domain.MultiplierType
import com.fantasyidler.tensura.domain.OwnedSkill
import com.fantasyidler.tensura.domain.SkillDefinition
import com.fantasyidler.tensura.domain.TensuraState
import com.fantasyidler.tensura.domain.learnCost
import com.fantasyidler.tensura.domain.levelOf
import com.fantasyidler.tensura.domain.masteryCost
import com.fantasyidler.tensura.domain.phCost
import com.fantasyidler.tensura.domain.value
import com.fantasyidler.tensura.domain.xpToNext

@Composable
fun SkillsTab(repo: TensuraRepository, state: TensuraState) {
    var fusionSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Tus habilidades (" + state.owned.size + ")")
                Spacer(modifier = Modifier.weight(1f))
                if (fusionSelection.size == 2) {
                    TextButton(onClick = {
                        val ids = fusionSelection.toList()
                        message = repo.fuse(ids[0], ids[1])
                        fusionSelection = emptySet()
                    }) {
                        Text("Fusionar (30 PH)")
                    }
                }
            }
            message?.let { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (state.owned.isEmpty()) {
                Text(
                    text = "Todavía no tienes habilidades. Aprende una del catálogo (abajo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Marca dos habilidades Maestro y pulsa Fusionar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.owned.keys.toList(), key = { it }) { id ->
            val own = state.owned[id]
            val def = repo.catalog[id]
            if (own != null && def != null) {
                OwnedSkillCard(
                    def = def,
                    own = own,
                    ph = state.ph,
                    selectedForFusion = fusionSelection.contains(id),
                    onToggleFusion = {
                        fusionSelection = if (fusionSelection.contains(id)) fusionSelection - id else fusionSelection + id
                    },
                    onMastery = { message = repo.upgradeMastery(id) },
                    onStimulate = { message = repo.stimulate(id) },
                    onTranscend = { message = repo.transcend(id) },
                )
            }
        }
        item {
            SectionTitle("Catálogo (" + repo.catalogList.size + ")")
        }
        items(repo.catalogList.filter { it.id !in state.owned }, key = { it.id }) { def ->
            CatalogCard(def = def, ph = state.ph, onLearn = { message = repo.learnSkill(def.id) })
        }
        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun OwnedSkillCard(
    def: SkillDefinition,
    own: OwnedSkill,
    ph: Long,
    selectedForFusion: Boolean,
    onToggleFusion: () -> Unit,
    onMastery: () -> Unit,
    onStimulate: () -> Unit,
    onTranscend: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(def.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = def.rank.label + " · " + def.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (own.awakened) {
                    Text(
                        text = "DESPERTAR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Checkbox(checked = selectedForFusion, onCheckedChange = { onToggleFusion() })
            }
            Text(
                text = "Maestría: " + own.mastery.label + " · XP " + own.masteryXp,
                style = MaterialTheme.typography.bodySmall,
                color = masteryColor(own.mastery),
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (own.mastery == MasteryLevel.MASTER) {
                Bar(progress = 1f, color = masteryColor(own.mastery))
            } else {
                val threshold = xpToNext(own.mastery).toFloat()
                Bar(progress = own.masteryXp / threshold, color = masteryColor(own.mastery))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (own.mastery != MasteryLevel.MASTER) {
                    val cost = masteryCost(own.mastery)
                    SmallButton("Maestría " + cost + " PH", ph >= cost, onMastery)
                }
                SmallButton("Estímulo 50 PH", ph >= 50L && !own.awakened, onStimulate)
                if (def.evolvesTo != null) {
                    SmallButton("Trascender 60 PH", ph >= 60L, onTranscend)
                }
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(34.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CatalogCard(def: SkillDefinition, ph: Long, onLearn: () -> Unit) {
    val cost = learnCost(def.rank)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(def.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = def.rank.label + " · " + def.type.label + " · " + def.categories.joinToString { it.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = def.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = cost.toString() + " PH",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Button(onClick = onLearn, enabled = ph >= cost) { Text("Aprender") }
            }
        }
    }
}

@Composable
fun ShopTab(repo: TensuraRepository, state: TensuraState) {
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionTitle("Multiplicadores permanentes")
            Text(
                text = "Se compran con PH y no se pierden nunca. El coste sube 1.5x por nivel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        items(MultiplierType.entries.toList(), key = { it.name }) { type ->
            val level = state.multipliers.levelOf(type)
            val cost = type.phCost(level)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(type.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = type.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Nivel " + level + " · x" + formatMultiplier(state.multipliers.value(type)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = cost.toString() + " PH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Button(
                            onClick = { message = repo.buyMultiplier(type) },
                            enabled = state.ph >= cost,
                        ) {
                            Text("Comprar")
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}
