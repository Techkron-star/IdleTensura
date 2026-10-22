package com.fantasyidler.tensura.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests puros del dominio de Tensura: costes, multiplicadores, colas y motor de combate.
 * No necesitan Android ni Robolectric.
 */
class TensuraDomainTest {

    // ---------------------------------------------------------------- costes

    @Test
    fun `learn cost grows with rank`() {
        val costs = SkillRank.entries.map { learnCost(it) }

        assertEquals(SkillRank.entries.size, costs.size)
        assertEquals(10L, learnCost(SkillRank.COMMON))
        assertEquals(160L, learnCost(SkillRank.ULTIMATE))
        assertEquals(costs.sorted(), costs)
        assertTrue(costs.all { it > 0L })
    }

    @Test
    fun `rank multiplier follows the catalog`() {
        assertEquals(1.0, rankMultiplier(null), 1e-9)
        assertEquals(1.0, rankMultiplier(SkillRank.COMMON), 1e-9)
        assertEquals(1.3, rankMultiplier(SkillRank.EXTRA), 1e-9)
        assertEquals(2.0, rankMultiplier(SkillRank.UNIQUE), 1e-9)
        assertEquals(3.2, rankMultiplier(SkillRank.ULTIMATE), 1e-9)

        val values = SkillRank.entries.map { rankMultiplier(it) }
        assertEquals(values.sorted(), values)
    }

    @Test
    fun `mastery and xp thresholds are coherent`() {
        assertEquals(25L, masteryCost(MasteryLevel.BASIC))
        assertEquals(50L, masteryCost(MasteryLevel.ADVANCED))
        assertEquals(50L, masteryCost(MasteryLevel.MASTER))

        assertEquals(100, xpToNext(MasteryLevel.BASIC))
        assertEquals(250, xpToNext(MasteryLevel.ADVANCED))
        assertEquals(Int.MAX_VALUE, xpToNext(MasteryLevel.MASTER))

        assertTrue(MasteryLevel.BASIC.powerMultiplier < MasteryLevel.ADVANCED.powerMultiplier)
        assertTrue(MasteryLevel.ADVANCED.powerMultiplier < MasteryLevel.MASTER.powerMultiplier)
    }

    // -------------------------------------------------------- multiplicadores

    @Test
    fun `multiplier values match their level`() {
        val m = PermanentMultipliers(
            skillXpLevel = 2,
            combatXpLevel = 4,
            lootLevel = 5,
            coinsLevel = 1,
            phGainLevel = 3,
            queueSpeedLevel = 2,
            dropRateLevel = 10,
        )

        assertEquals(1.30, m.skillXp, 1e-9)
        assertEquals(1.60, m.combatXp, 1e-9)
        assertEquals(1.50, m.loot, 1e-9)
        assertEquals(1.15, m.coins, 1e-9)
        assertEquals(1.30, m.phGain, 1e-9)
        assertEquals(1.20, m.queueSpeed, 1e-9)
        assertEquals(1.50, m.dropRate, 1e-9)
    }

    @Test
    fun `levelOf and withLevel round trip for every type`() {
        var m = PermanentMultipliers()
        MultiplierType.entries.forEach { type ->
            m = m.withLevel(type, 7)
        }
        MultiplierType.entries.forEach { type ->
            assertEquals(7, m.levelOf(type))
        }

        // A nivel 7 cada multiplicador aplica exactamente su incremento por nivel.
        assertEquals(1.0 + 0.15 * 7, m.skillXp, 1e-9)
        assertEquals(1.0 + 0.15 * 7, m.combatXp, 1e-9)
        assertEquals(1.0 + 0.10 * 7, m.loot, 1e-9)
        assertEquals(1.0 + 0.15 * 7, m.coins, 1e-9)
        assertEquals(1.0 + 0.10 * 7, m.phGain, 1e-9)
        assertEquals(1.0 + 0.10 * 7, m.queueSpeed, 1e-9)
        assertEquals(1.0 + 0.05 * 7, m.dropRate, 1e-9)
    }

    @Test
    fun `multiplier cost grows fifty percent per level`() {
        MultiplierType.entries.forEach { type ->
            val level0 = type.phCost(0)
            val level1 = type.phCost(1)
            val level2 = type.phCost(2)

            assertTrue("$type debe costar algo", level0 >= 1L)
            assertEquals((level0 * 1.5).toLong(), level1)
            assertEquals((level0 * 1.5 * 1.5).toLong(), level2)
            assertTrue(level2 > level1)
        }
    }

    @Test
    fun `every multiplier has label and description`() {
        MultiplierType.entries.forEach { type ->
            assertTrue(type.label().isNotBlank())
            assertTrue(type.description().isNotBlank())
        }
    }

    // ---------------------------------------------------------------- colas

    @Test
    fun `inactive queue slot reports no progress`() {
        val slot = QueueSlot(index = 1)

        assertFalse(slot.active)
        assertEquals(0L, slot.remainingMs(now = 1_000L))
        assertEquals(0f, slot.progress(now = 1_000L), 1e-6f)
    }

    @Test
    fun `queue slot reports remaining time and progress`() {
        val slot = QueueSlot(
            index = 0,
            skillId = "slime_01",
            active = true,
            startedAtMs = 1_000L,
            durationMs = 2_000L,
        )

        assertEquals(2_000L, slot.remainingMs(now = 1_000L))
        assertEquals(1_000L, slot.remainingMs(now = 2_000L))
        assertEquals(0L, slot.remainingMs(now = 9_000L))

        assertEquals(0f, slot.progress(now = 1_000L), 1e-6f)
        assertEquals(0.5f, slot.progress(now = 2_000L), 1e-6f)
        assertEquals(1f, slot.progress(now = 9_000L), 1e-6f)
    }

    @Test
    fun `default state starts with ph and five queue slots`() {
        val state = TensuraState()

        assertEquals(30L, state.ph)
        assertEquals(30L, state.lifetimePh)
        assertEquals(5, state.queue.size)
        assertTrue(state.owned.isEmpty())
        assertTrue(state.flags.autoQueue)
        assertEquals(0L, state.multipliers.levelOf(MultiplierType.SKILL_XP).toLong())
    }

    // -------------------------------------------------------------- combate

    private val skills: Map<String, SkillDefinition> = mapOf(
        "nuke" to SkillDefinition(
            id = "nuke",
            name = "Nuke",
            description = "Hace muchísimo daño",
            rank = SkillRank.UNIQUE,
            type = SkillType.ACTIVE,
            mpCost = 10,
            basePower = 10_000,
        ),
        "expensive" to SkillDefinition(
            id = "expensive",
            name = "Cara",
            description = "Cuesta más MP del disponible",
            rank = SkillRank.UNIQUE,
            type = SkillType.ACTIVE,
            mpCost = 999,
            basePower = 100,
        ),
        "heal" to SkillDefinition(
            id = "heal",
            name = "Curación",
            description = "Restaura HP",
            rank = SkillRank.COMMON,
            type = SkillType.ACTIVE,
            mpCost = 5,
            heal = 30,
        ),
    )

    private val owned: Map<String, OwnedSkill> = skills.keys.associateWith { OwnedSkill(skillId = it) }

    private fun engine(
        onVictory: (Int, Long, Int, String?) -> CombatReward = { coins, ph, xp, _ ->
            CombatReward(coins, ph, xp)
        },
    ): CombatEngine = CombatEngine(
        resolveSkill = { id -> skills[id] },
        resolveOwned = { id -> owned[id] },
        playerStats = {
            PlayerStats(maxHp = 100, maxMp = 50, attack = 40, defense = 10, speed = 20)
        },
        onVictory = onVictory,
    )

    @Test
    fun `combat starts with full resources and turn one`() {
        val engine = engine()
        val enemy = EnemyCatalog.enemies.first()

        engine.start(enemy)
        val state = engine.state.value

        assertTrue(state.active)
        assertFalse(state.victory)
        assertFalse(state.defeat)
        assertEquals(enemy, state.enemy)
        assertEquals(enemy.hp, state.enemyHp)
        assertEquals(100, state.playerHp)
        assertEquals(50, state.playerMp)
        assertEquals(100, state.playerMaxHp)
        assertEquals(50, state.playerMaxMp)
        assertEquals(1, state.turn)
        assertEquals(3, state.potions)
    }

    @Test
    fun `a powerful skill wins the combat and pays rewards`() {
        val enemy = EnemyCatalog.enemies.first { it.id == "slime_01" }
        val nuke = skills.getValue("nuke")
        var rewarded: CombatReward? = null
        var rewardedSkill: String? = null

        val engine = engine { coins, ph, xp, skillId ->
            rewardedSkill = skillId
            CombatReward(coins, ph, xp).also { rewarded = it }
        }

        engine.start(enemy)
        engine.act(PlayerAction.SKILL, "nuke")
        val state = engine.state.value

        assertTrue(state.victory)
        assertFalse(state.active)
        assertFalse(state.defeat)
        assertEquals(0, state.enemyHp)
        assertEquals("nuke", rewardedSkill)
        assertEquals(
            CombatReward(
                enemy.rewardCoins,
                enemy.rewardPh,
                enemy.rewardSkillXp + 12 + nuke.basePower / 4,
            ),
            rewarded,
        )
        assertEquals(50 - nuke.mpCost, state.playerMp)
    }

    @Test
    fun `skill without enough mp is rejected without damaging the enemy`() {
        val enemy = EnemyCatalog.enemies.first { it.id == "slime_01" }
        val engine = engine()

        engine.start(enemy)
        engine.act(PlayerAction.SKILL, "expensive")
        val state = engine.state.value

        assertTrue(state.log.any { it.contains("MP insuficiente") })
        assertEquals(enemy.hp, state.enemyHp)
        assertFalse(state.victory)
    }

    @Test
    fun `unknown skill is rejected`() {
        val engine = engine()
        val enemy = EnemyCatalog.enemies.first()

        engine.start(enemy)
        engine.act(PlayerAction.SKILL, "no_existe")

        assertTrue(engine.state.value.log.any { it.contains("No conoces esa habilidad") })
    }

    @Test
    fun `potion is consumed and never exceeds max hp or mp`() {
        val engine = engine()

        engine.start(EnemyCatalog.enemies.first())
        val before = engine.state.value
        engine.act(PlayerAction.ITEM)
        val after = engine.state.value

        assertEquals(before.potions - 1, after.potions)
        assertTrue(after.playerHp <= after.playerMaxHp)
        assertTrue(after.playerMp <= after.playerMaxMp)
    }

    @Test
    fun `reset clears the combat`() {
        val engine = engine()

        engine.start(EnemyCatalog.enemies.first())
        engine.reset()
        val state = engine.state.value

        assertNull(state.enemy)
        assertFalse(state.active)
        assertEquals(0, state.turn)
        assertEquals(0, state.enemyHp)
    }

    @Test
    fun `enemy catalog is consistent`() {
        val enemies = EnemyCatalog.enemies

        assertTrue(enemies.size >= 6)
        assertEquals(enemies.size, enemies.map { it.id }.distinct().size)
        enemies.forEach { enemy ->
            assertTrue("${enemy.id}: hp", enemy.hp > 0)
            assertTrue("${enemy.id}: attack", enemy.attack > 0)
            assertTrue("${enemy.id}: defense", enemy.defense >= 0)
            assertTrue("${enemy.id}: speed", enemy.speed > 0)
            assertTrue("${enemy.id}: coins", enemy.rewardCoins > 0)
            assertTrue("${enemy.id}: ph", enemy.rewardPh > 0L)
            assertTrue("${enemy.id}: xp", enemy.rewardSkillXp > 0)
            assertTrue("${enemy.id}: tier", enemy.tier.isNotBlank())
        }
    }
}
