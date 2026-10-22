package com.fantasyidler.tensura.domain

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Biome(val label: String, val color: Int) {
    GRASSLAND("Llanura", 0xFF8BC34A),
    FOREST("Bosque", 0xFF4CAF50),
    CAVE("Cueva", 0xFF795548),
    VOLCANO("Volcán", 0xFFF44336),
    WASTELAND("Yermo", 0xFF9E9E9E),
    DUNGEON("Mazmorra", 0xFF4527A0),
}

@Serializable
data class ZoneDef(
    val id: String,
    val name: String,
    val biome: Biome,
    val danger: Int,
    val recommendedRank: SkillRank,
    val isHotZone: Boolean,
    val lootMultiplier: Double,
    val enemyIds: List<String>,
    val unlockPhCost: Long,
    val description: String,
)

@Serializable
data class ZoneState(
    val zoneId: String,
    val unlocked: Boolean = false,
    val visited: Int = 0,
    val enemiesDefeated: Int = 0,
    val lastEncounterMs: Long = 0L,
)

@Serializable
data class WorldMap(
    val zones: List<ZoneDef>,
    val zoneStates: Map<String, ZoneState> = emptyMap(),
    val currentZoneId: String? = null,
    val totalEncounters: Int = 0,
    val totalVictories: Int = 0,
)

object WorldZones {
    val all: List<ZoneDef> = listOf(
        ZoneDef(
            id = "plains_01",
            name = "Llanuras del Slime",
            biome = Biome.GRASSLAND,
            danger = 2,
            recommendedRank = SkillRank.COMMON,
            isHotZone = false,
            lootMultiplier = 1.0,
            enemyIds = listOf("slime_01"),
            unlockPhCost = 0L,
            description = "Zona segura para principiantes.",
        ),
        ZoneDef(
            id = "forest_01",
            name = "Bosque de los Goblins",
            biome = Biome.FOREST,
            danger = 4,
            recommendedRank = SkillRank.EXTRA,
            isHotZone = false,
            lootMultiplier = 1.2,
            enemyIds = listOf("goblin_01", "slime_01"),
            unlockPhCost = 50L,
            description = "Bosque denso. Goblins acechan entre los árboles.",
        ),
        ZoneDef(
            id = "cave_01",
            name = "Cueva del Lobo Feroz",
            biome = Biome.CAVE,
            danger = 6,
            recommendedRank = SkillRank.INNATE,
            isHotZone = true,
            lootMultiplier = 1.5,
            enemyIds = listOf("direwolf_01", "goblin_01"),
            unlockPhCost = 150L,
            description = "🔥 ZONA CALIENTE: peligro elevado, botín x1,5.",
        ),
        ZoneDef(
            id = "wasteland_01",
            name = "Yermo del Señor Orco",
            biome = Biome.WASTELAND,
            danger = 7,
            recommendedRank = SkillRank.INNATE,
            isHotZone = false,
            lootMultiplier = 1.3,
            enemyIds = listOf("orc_lord", "direwolf_01"),
            unlockPhCost = 300L,
            description = "Yermo árido dominado por orcos.",
        ),
        ZoneDef(
            id = "volcano_01",
            name = "Volcán de Charybdis",
            biome = Biome.VOLCANO,
            danger = 8,
            recommendedRank = SkillRank.UNIQUE,
            isHotZone = true,
            lootMultiplier = 1.8,
            enemyIds = listOf("charybdis", "orc_lord"),
            unlockPhCost = 600L,
            description = "🔥 ZONA CALIENTE: peligro extremo, botín x1,8.",
        ),
        ZoneDef(
            id = "dungeon_01",
            name = "Mazmorra del Señor Demonio",
            biome = Biome.DUNGEON,
            danger = 9,
            recommendedRank = SkillRank.SINGULAR,
            isHotZone = true,
            lootMultiplier = 2.0,
            enemyIds = listOf("demon_lord", "charybdis"),
            unlockPhCost = 1200L,
            description = "🔥 ZONA CALIENTE: el desafío definitivo, botín x2.",
        ),
    )

    fun byId(id: String): ZoneDef? = all.find { it.id == id }

    fun initialMap(): WorldMap = WorldMap(
        zones = all,
        zoneStates = all.associate { it.id to ZoneState(it.id, unlocked = it.unlockPhCost <= 0L) },
    )
}
