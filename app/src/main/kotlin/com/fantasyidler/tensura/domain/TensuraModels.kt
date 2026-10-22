package com.fantasyidler.tensura.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SkillRank(val label: String) {
    COMMON("Común"),
    EXTRA("Extra"),
    INNATE("Innata"),
    UNIQUE("Única"),
    SINGULAR("Singular"),
    ULTIMATE("Ultimate"),
}

@Serializable
enum class SkillType(val label: String) {
    PASSIVE("Pasiva"),
    ACTIVE("Activa"),
    MIXED("Mixta"),
    TOGGLEABLE("Toggleable"),
}

@Serializable
enum class SkillCategory(val label: String) {
    ELEMENTAL("Elemental"),
    PHYSICAL("Física"),
    MENTAL("Mental"),
    SPIRITUAL("Espiritual"),
    SENSORY("Sensorial"),
    CREATION("Creación"),
    STATE("Estado"),
    COSMOLOGY("Cosmovisión"),
    REALITY_MARBLE("Reality Marble"),
    SUMMONING("Invocación"),
}

@Serializable
enum class MasteryLevel(val label: String, val powerMultiplier: Double) {
    BASIC("Básico", 1.0),
    ADVANCED("Avanzado", 1.5),
    MASTER("Maestro", 2.0),
}

@Serializable
data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val rank: SkillRank,
    val type: SkillType,
    val categories: List<SkillCategory> = emptyList(),
    val mpCost: Int = 0,
    val hpCost: Int = 0,
    val basePower: Int = 0,
    val heal: Int = 0,
    val weaknesses: List<String> = emptyList(),
    val requirement: String = "",
    val evolvesTo: String? = null,
    val potato: Boolean = false,
)

@Serializable
data class OwnedSkill(
    val skillId: String,
    val mastery: MasteryLevel = MasteryLevel.BASIC,
    val masteryXp: Int = 0,
    val timesUsed: Int = 0,
    val awakenings: Int = 0,
    val awakened: Boolean = false,
)

@Serializable
data class PlayerStats(
    val maxHp: Int = 120,
    val maxMp: Int = 80,
    val attack: Int = 30,
    val defense: Int = 15,
    val speed: Int = 14,
    val willpower: Int = 50,
    val intelligence: Int = 50,
)

@Serializable
data class PermanentMultipliers(
    val skillXpLevel: Int = 0,
    val combatXpLevel: Int = 0,
    val lootLevel: Int = 0,
    val coinsLevel: Int = 0,
    val phGainLevel: Int = 0,
    val queueSpeedLevel: Int = 0,
    val dropRateLevel: Int = 0,
) {
    val skillXp: Double get() = 1.0 + 0.15 * skillXpLevel
    val combatXp: Double get() = 1.0 + 0.15 * combatXpLevel
    val loot: Double get() = 1.0 + 0.10 * lootLevel
    val coins: Double get() = 1.0 + 0.15 * coinsLevel
    val phGain: Double get() = 1.0 + 0.10 * phGainLevel
    val queueSpeed: Double get() = 1.0 + 0.10 * queueSpeedLevel
    val dropRate: Double get() = 1.0 + 0.05 * dropRateLevel
}

@Serializable
enum class MultiplierType {
    SKILL_XP,
    COMBAT_XP,
    LOOT,
    COINS,
    PH_GAIN,
    QUEUE_SPEED,
    DROP_RATE,
}

fun MultiplierType.label(): String = when (this) {
    MultiplierType.SKILL_XP -> "XP de habilidades"
    MultiplierType.COMBAT_XP -> "XP de combate"
    MultiplierType.LOOT -> "Cantidad de botín"
    MultiplierType.COINS -> "Monedas"
    MultiplierType.PH_GAIN -> "Ganancia de PH"
    MultiplierType.QUEUE_SPEED -> "Velocidad de cola"
    MultiplierType.DROP_RATE -> "Probabilidad de drop"
}

fun MultiplierType.description(): String = when (this) {
    MultiplierType.SKILL_XP -> "+15% de XP para todas las habilidades por nivel."
    MultiplierType.COMBAT_XP -> "+15% de XP de combate por nivel."
    MultiplierType.LOOT -> "+10% de materiales por nivel."
    MultiplierType.COINS -> "+15% de oro por nivel."
    MultiplierType.PH_GAIN -> "+10% de PH obtenido por nivel."
    MultiplierType.QUEUE_SPEED -> "+10% de velocidad de las colas por nivel."
    MultiplierType.DROP_RATE -> "+5% de probabilidad de drop por nivel."
}

fun MultiplierType.phCost(level: Int): Long {
    val base = when (this) {
        MultiplierType.SKILL_XP -> 20.0
        MultiplierType.COMBAT_XP -> 20.0
        MultiplierType.LOOT -> 25.0
        MultiplierType.COINS -> 25.0
        MultiplierType.PH_GAIN -> 40.0
        MultiplierType.QUEUE_SPEED -> 35.0
        MultiplierType.DROP_RATE -> 30.0
    }
    return (base * Math.pow(1.5, level.toDouble())).toLong().coerceAtLeast(1L)
}

fun PermanentMultipliers.levelOf(type: MultiplierType): Int = when (type) {
    MultiplierType.SKILL_XP -> skillXpLevel
    MultiplierType.COMBAT_XP -> combatXpLevel
    MultiplierType.LOOT -> lootLevel
    MultiplierType.COINS -> coinsLevel
    MultiplierType.PH_GAIN -> phGainLevel
    MultiplierType.QUEUE_SPEED -> queueSpeedLevel
    MultiplierType.DROP_RATE -> dropRateLevel
}

fun PermanentMultipliers.withLevel(type: MultiplierType, level: Int): PermanentMultipliers = when (type) {
    MultiplierType.SKILL_XP -> copy(skillXpLevel = level)
    MultiplierType.COMBAT_XP -> copy(combatXpLevel = level)
    MultiplierType.LOOT -> copy(lootLevel = level)
    MultiplierType.COINS -> copy(coinsLevel = level)
    MultiplierType.PH_GAIN -> copy(phGainLevel = level)
    MultiplierType.QUEUE_SPEED -> copy(queueSpeedLevel = level)
    MultiplierType.DROP_RATE -> copy(dropRateLevel = level)
}

fun PermanentMultipliers.value(type: MultiplierType): Double = when (type) {
    MultiplierType.SKILL_XP -> skillXp
    MultiplierType.COMBAT_XP -> combatXp
    MultiplierType.LOOT -> loot
    MultiplierType.COINS -> coins
    MultiplierType.PH_GAIN -> phGain
    MultiplierType.QUEUE_SPEED -> queueSpeed
    MultiplierType.DROP_RATE -> dropRate
}

@Serializable
data class QueueSlot(
    val index: Int,
    val skillId: String? = null,
    val active: Boolean = false,
    val startedAtMs: Long = 0L,
    val durationMs: Long = 0L,
    val pendingPh: Long = 0L,
    val pendingCoins: Long = 0L,
    val pendingXp: Int = 0,
    val completedSessions: Int = 0,
) {
    fun remainingMs(now: Long): Long {
        if (!active || durationMs <= 0L) return 0L
        return (startedAtMs + durationMs - now).coerceAtLeast(0L)
    }

    fun progress(now: Long): Float {
        if (!active || durationMs <= 0L) return 0f
        return ((now - startedAtMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
}

@Serializable
data class TensuraFlags(
    val autoQueue: Boolean = true,
    val totalCombats: Int = 0,
    val totalVictories: Int = 0,
)

@Serializable
data class TensuraState(
    val ph: Long = 30L,
    val lifetimePh: Long = 30L,
    val coins: Long = 0L,
    val owned: Map<String, OwnedSkill> = emptyMap(),
    val multipliers: PermanentMultipliers = PermanentMultipliers(),
    val queue: List<QueueSlot> = listOf(
        QueueSlot(0), QueueSlot(1), QueueSlot(2), QueueSlot(3), QueueSlot(4),
    ),
    val stats: PlayerStats = PlayerStats(),
    val flags: TensuraFlags = TensuraFlags(),
)

fun learnCost(rank: SkillRank): Long = when (rank) {
    SkillRank.COMMON -> 10L
    SkillRank.EXTRA -> 20L
    SkillRank.INNATE -> 35L
    SkillRank.UNIQUE -> 60L
    SkillRank.SINGULAR -> 100L
    SkillRank.ULTIMATE -> 160L
}

fun rankMultiplier(rank: SkillRank?): Double = when (rank) {
    SkillRank.COMMON -> 1.0
    SkillRank.EXTRA -> 1.3
    SkillRank.INNATE -> 1.6
    SkillRank.UNIQUE -> 2.0
    SkillRank.SINGULAR -> 2.5
    SkillRank.ULTIMATE -> 3.2
    null -> 1.0
}

fun masteryCost(level: MasteryLevel): Long = if (level == MasteryLevel.BASIC) 25L else 50L

fun xpToNext(level: MasteryLevel): Int = when (level) {
    MasteryLevel.BASIC -> 100
    MasteryLevel.ADVANCED -> 250
    MasteryLevel.MASTER -> Int.MAX_VALUE
}
