package com.fantasyidler.tensura.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fantasyidler.tensura.data.TensuraRepository
import com.fantasyidler.tensura.domain.MasteryLevel
import com.fantasyidler.tensura.domain.MultiplierType
import com.fantasyidler.tensura.domain.OwnedSkill
import com.fantasyidler.tensura.domain.SkillDefinition
import com.fantasyidler.tensura.domain.SkillRank
import com.fantasyidler.tensura.domain.TensuraState
import com.fantasyidler.tensura.domain.description
import com.fantasyidler.tensura.domain.label
import com.fantasyidler.tensura.domain.learnCost
import com.fantasyidler.tensura.domain.levelOf
import com.fantasyidler.tensura.domain.masteryCost
import com.fantasyidler.tensura.domain.phCost
import com.fantasyidler.tensura.domain.value
import com.fantasyidler.tensura.domain.xpToNext

@Composable
fun SkillsTab(repo: TensuraRepository, state: TensuraState) {
    var mode by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var rankFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var fusionSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }

    val ownedDefs = state.owned.keys.mapNotNull { repo.catalog[it] }
    val catalogDefs = repo.catalogList.filter { def ->
        val matchesRank = rankFilter == null || def.rank.name == rankFilter
        val matchesQuery = query.isBlank() ||
            def.name.contains(query, ignoreCase = true) ||
            def.description.contains(query, ignoreCase = true) ||
            def.categories.any { it.label.contains(query, ignoreCase = true) }
        matchesRank && matchesQuery
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = { Text("Mías (" + state.owned.size + ")") },
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                label = { Text("Catálogo (" + repo.catalogList.size + ")") },
            )
        }

        if (mode == 1) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                placeholder = { Text("Buscar por nombre, efecto o categoría") },
                leadingIcon = { Text("🔍") },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = rankFilter == null,
                    onClick = { rankFilter = null },
                    label = { Text("Todas") },
                )
                SkillRank.entries.forEach { rank ->
                    FilterChip(
                        selected = rankFilter == rank.name,
                        onClick = { rankFilter = if (rankFilter == rank.name) null else rank.name },
                        label = { Text(rank.label) },
                    )
                }
            }
        }

        message?.let { text ->
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }

        if (fusionSelection.size == 2) {
            FusionBar(
                ids = fusionSelection,
                catalog = repo.catalog,
                ph = state.ph,
                onClear = { fusionSelection = emptySet() },
                onFuse = {
                    val ids = fusionSelection.toList()
                    message = repo.fuse(ids[0], ids[1])
                    fusionSelection = emptySet()
                },
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (mode == 0) {
                if (ownedDefs.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Aún no tienes habilidades",
                            detail = "Abre la pestaña Catálogo y aprende tu primera habilidad con PH.",
                        )
                    }
                }
                item {
                    Text(
                        text = "Toca una habilidad para ver su ficha. Marca dos Maestro para fusionarlas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(ownedDefs, key = { it.id }) { def ->
                    val own = state.owned[def.id]
                    if (own != null) {
                        OwnedSkillCard(
                            def = def,
                            own = own,
                            ph = state.ph,
                            catalog = repo.catalog,
                            selectedForFusion = fusionSelection.contains(def.id),
                            onOpen = { detailId = def.id },
                            onToggleFusion = {
                                fusionSelection = if (fusionSelection.contains(def.id)) {
                                    fusionSelection - def.id
                                } else {
                                    fusionSelection + def.id
                                }
                            },
                            onMastery = { message = repo.upgradeMastery(def.id) },
                            onStimulate = { message = repo.stimulate(def.id) },
                            onTranscend = { message = repo.transcend(def.id) },
                        )
                    }
                }
            } else {
                if (catalogDefs.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Sin resultados",
                            detail = "Prueba con otro nombre o quita el filtro de rango.",
                        )
                    }
                }
                items(catalogDefs, key = { it.id }) { def ->
                    CatalogCard(
                        def = def,
                        ph = state.ph,
                        owned = state.owned.containsKey(def.id),
                        onOpen = { detailId = def.id },
                        onLearn = { message = repo.learnSkill(def.id) },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }

    val detailDef = detailId?.let { repo.catalog[it] }
    if (detailDef != null) {
        SkillDetailDialog(
            def = detailDef,
            own = state.owned[detailDef.id],
            catalog = repo.catalog,
            ph = state.ph,
            onDismiss = { detailId = null },
            onLearn = {
                message = repo.learnSkill(detailDef.id)
                detailId = null
            },
            onMastery = {
                message = repo.upgradeMastery(detailDef.id)
                detailId = null
            },
            onStimulate = {
                message = repo.stimulate(detailDef.id)
                detailId = null
            },
            onTranscend = {
                message = repo.transcend(detailDef.id)
                detailId = null
            },
        )
    }
}

@Composable
private fun FusionBar(
    ids: Set<String>,
    catalog: Map<String, SkillDefinition>,
    ph: Long,
    onClear: () -> Unit,
    onFuse: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fusión preparada", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = ids.mapNotNull { catalog[it]?.name }.joinToString(" + "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onClear) { Text("Cancelar") }
            Button(onClick = onFuse, enabled = ph >= 30L) { Text("Fusionar 30 PH") }
        }
    }
}

@Composable
private fun OwnedSkillCard(
    def: SkillDefinition,
    own: OwnedSkill,
    ph: Long,
    catalog: Map<String, SkillDefinition>,
    selectedForFusion: Boolean,
    onOpen: () -> Unit,
    onToggleFusion: () -> Unit,
    onMastery: () -> Unit,
    onStimulate: () -> Unit,
    onTranscend: () -> Unit,
) {
    val threshold = xpToNext(own.mastery)
    val progress = if (own.mastery == MasteryLevel.MASTER) 1f else own.masteryXp.toFloat() / threshold.toFloat()
    val nextForm = def.evolvesTo?.let { catalog[it]?.name }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(def.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Badge(def.rank.label, rankColor(def.rank))
                        Badge(def.type.label, MaterialTheme.colorScheme.primary)
                        if (own.awakened) Badge("Despertar", MaterialTheme.colorScheme.error)
                    }
                }
                if (own.mastery == MasteryLevel.MASTER) {
                    Checkbox(checked = selectedForFusion, onCheckedChange = { onToggleFusion() })
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Maestría " + own.mastery.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = masteryColor(own.mastery),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (own.mastery == MasteryLevel.MASTER) "MAX" else own.masteryXp.toString() + " / " + threshold + " XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Bar(progress = progress, color = masteryColor(own.mastery))

            if (nextForm != null) {
                Text(
                    text = "🔮 Evoluciona a " + (nextForm) + " · " + def.requirement,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
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
private fun CatalogCard(
    def: SkillDefinition,
    ph: Long,
    owned: Boolean,
    onOpen: () -> Unit,
    onLearn: () -> Unit,
) {
    val cost = learnCost(def.rank)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(def.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Badge(def.rank.label, rankColor(def.rank))
                        Badge(def.type.label, MaterialTheme.colorScheme.primary)
                        if (def.potato) Badge("Papa", Color(0xFFC8A165))
                    }
                }
                Text(
                    text = cost.toString() + " PH",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = def.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = def.categories.joinToString { it.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (owned) {
                    Text(
                        text = "Aprendida",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        onClick = onLearn,
                        enabled = ph >= cost,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text("Aprender", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillDetailDialog(
    def: SkillDefinition,
    own: OwnedSkill?,
    catalog: Map<String, SkillDefinition>,
    ph: Long,
    onDismiss: () -> Unit,
    onLearn: () -> Unit,
    onMastery: () -> Unit,
    onStimulate: () -> Unit,
    onTranscend: () -> Unit,
) {
    val chain = evolutionChain(def, catalog)
    val cost = learnCost(def.rank)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = {
            Column {
                Text(def.name, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Badge(def.rank.label, rankColor(def.rank))
                    Badge(def.type.label, MaterialTheme.colorScheme.primary)
                    if (def.potato) Badge("Papa", Color(0xFFC8A165))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(def.description, style = MaterialTheme.typography.bodyMedium)

                if (def.categories.isNotEmpty()) {
                    DetailLine("Categorías", def.categories.joinToString { it.label })
                }
                if (def.mpCost > 0) DetailLine("MP", def.mpCost.toString())
                if (def.hpCost > 0) DetailLine("HP", def.hpCost.toString())
                if (def.basePower > 0) DetailLine("Potencia base", def.basePower.toString())
                if (def.heal > 0) DetailLine("Curación", def.heal.toString())
                if (def.weaknesses.isNotEmpty()) {
                    DetailLine("Debilidades", def.weaknesses.joinToString(", "))
                }
                if (def.requirement.isNotBlank()) {
                    DetailLine("Requisito", def.requirement)
                }

                Text(
                    text = "Línea evolutiva",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = buildString {
                        append(def.name)
                        chain.forEach { append("  →  ").append(it.name) }
                        if (chain.isEmpty()) append("  (forma final)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                if (own != null) {
                    Text(
                        text = "Maestría " + own.mastery.label + " · " + own.masteryXp + " XP · usada " + own.timesUsed + " veces",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (own == null) {
                        Button(onClick = onLearn, enabled = ph >= cost, modifier = Modifier.fillMaxWidth()) {
                            Text("Aprender · " + cost + " PH")
                        }
                    } else {
                        if (own.mastery != MasteryLevel.MASTER) {
                            val masteryPrice = masteryCost(own.mastery)
                            Button(
                                onClick = onMastery,
                                enabled = ph >= masteryPrice,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Subir maestría · " + masteryPrice + " PH")
                            }
                        }
                        if (!own.awakened) {
                            OutlinedButton(
                                onClick = onStimulate,
                                enabled = ph >= 50L,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Estimular · 50 PH")
                            }
                        }
                        if (def.evolvesTo != null) {
                            OutlinedButton(
                                onClick = onTranscend,
                                enabled = ph >= 60L,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Trascender · 60 PH")
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = label + ": ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
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

private fun evolutionChain(start: SkillDefinition, catalog: Map<String, SkillDefinition>): List<SkillDefinition> {
    val chain = mutableListOf<SkillDefinition>()
    var next = start.evolvesTo?.let { catalog[it] }
    var guard = 0
    while (next != null && guard < 8) {
        chain += next
        next = next.evolvesTo?.let { catalog[it] }
        guard++
    }
    return chain
}

private fun rankColor(rank: SkillRank): Color = when (rank) {
    SkillRank.COMMON -> Color(0xFF9E9E9E)
    SkillRank.EXTRA -> Color(0xFF4CAF50)
    SkillRank.INNATE -> Color(0xFF29B6F6)
    SkillRank.UNIQUE -> Color(0xFF7E57C2)
    SkillRank.SINGULAR -> Color(0xFFFFA726)
    SkillRank.ULTIMATE -> Color(0xFFEF5350)
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
        items(MultiplierType.entries.toList(), key = { it.name }) { type ->
            val level = state.multipliers.levelOf(type)
            val cost = type.phCost(level)
            val current = state.multipliers.value(type)
            val next = when (type) {
                MultiplierType.SKILL_XP -> 1.0 + 0.15 * (level + 1)
                MultiplierType.COMBAT_XP -> 1.0 + 0.15 * (level + 1)
                MultiplierType.LOOT -> 1.0 + 0.10 * (level + 1)
                MultiplierType.COINS -> 1.0 + 0.15 * (level + 1)
                MultiplierType.PH_GAIN -> 1.0 + 0.10 * (level + 1)
                MultiplierType.QUEUE_SPEED -> 1.0 + 0.10 * (level + 1)
                MultiplierType.DROP_RATE -> 1.0 + 0.05 * (level + 1)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = type.label(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Badge("Nivel " + level, MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = type.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ahora x" + formatMultiplier(current),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Siguiente x" + formatMultiplier(next),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = cost.toString() + " PH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp),
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
