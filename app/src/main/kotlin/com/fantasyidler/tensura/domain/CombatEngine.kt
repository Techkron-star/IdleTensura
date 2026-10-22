package com.fantasyidler.tensura.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.random.Random

enum class PlayerAction(val label: String) {
    ATTACK("Atacar"),
    HEAVY_ATTACK("Ataque pesado"),
    DEFEND("Defender"),
    DODGE("Esquivar"),
    SKILL("Habilidad"),
    ITEM("Objeto"),
}

enum class EnemyAction(val label: String) {
    ATTACK("Ataca"),
    HEAVY_ATTACK("Ataque pesado"),
    DEFEND("Se defiende"),
    SPECIAL("Habilidad especial"),
    BUFF("Se potencia"),
}

data class EnemyDef(
    val id: String,
    val name: String,
    val tier: String,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val rewardCoins: Int,
    val rewardPh: Long,
    val rewardSkillXp: Int,
)

object EnemyCatalog {
    val enemies = listOf(
        EnemyDef("slime_01", "Slime Glotón", "Principiante", 60, 12, 4, 8, 8, 2L, 10),
        EnemyDef("goblin_01", "Jefe Goblin", "Principiante", 110, 18, 8, 11, 16, 4L, 15),
        EnemyDef("direwolf_01", "Lobo Feroz", "Intermedio", 160, 26, 12, 16, 28, 7L, 22),
        EnemyDef("orc_lord", "Señor Orco", "Intermedio", 240, 34, 18, 13, 45, 11L, 30),
        EnemyDef("charybdis", "Charybdis", "Élite", 420, 48, 26, 20, 90, 22L, 45),
        EnemyDef("demon_lord", "Señor Demonio", "Jefe", 700, 62, 34, 24, 160, 40L, 70),
    )
}

data class CombatReward(val coins: Int, val ph: Long, val skillXp: Int)

data class CombatState(
    val enemy: EnemyDef? = null,
    val enemyHp: Int = 0,
    val playerHp: Int = 0,
    val playerMp: Int = 0,
    val playerMaxHp: Int = 0,
    val playerMaxMp: Int = 0,
    val turn: Int = 0,
    val potions: Int = 3,
    val log: List<String> = emptyList(),
    val active: Boolean = false,
    val victory: Boolean = false,
    val defeat: Boolean = false,
    val rewardCoins: Int = 0,
    val rewardPh: Long = 0L,
    val rewardSkillXp: Int = 0,
)

class CombatEngine(
    private val resolveSkill: (String) -> SkillDefinition?,
    private val resolveOwned: (String) -> OwnedSkill?,
    private val playerStats: () -> PlayerStats,
    private val onVictory: (Int, Long, Int, String?) -> CombatReward,
) {
    private val _state = MutableStateFlow(CombatState())
    val state: StateFlow<CombatState> = _state.asStateFlow()

    fun start(enemy: EnemyDef) {
        val stats = playerStats()
        _state.value = CombatState(
            enemy = enemy,
            enemyHp = enemy.hp,
            playerHp = stats.maxHp,
            playerMp = stats.maxMp,
            playerMaxHp = stats.maxHp,
            playerMaxMp = stats.maxMp,
            turn = 1,
            potions = 3,
            log = listOf(
                "⚔️ Combate contra " + enemy.name + ".",
                "Es tu turno: elige una acción. El combate es por turnos.",
            ),
            active = true,
        )
    }

    fun reset() {
        _state.value = CombatState()
    }

    fun act(action: PlayerAction, skillId: String? = null) {
        val current = _state.value
        val enemy = current.enemy ?: return
        if (!current.active || current.victory || current.defeat) return

        val stats = playerStats()
        val log = current.log.toMutableList()
        var enemyHp = current.enemyHp
        var playerHp = current.playerHp
        var playerMp = current.playerMp
        var potions = current.potions
        var defending = false
        var dodging = false
        var usedSkill: String? = null
        var skillXp = 0

        when (action) {
            PlayerAction.ATTACK -> {
                val roll = rollAttack(stats.attack, enemy.defense, stats.speed, enemy.speed, "Tú", 1.0, 0.15)
                enemyHp -= roll.first
                log += roll.second
            }
            PlayerAction.HEAVY_ATTACK -> {
                val roll = rollAttack(stats.attack, enemy.defense, stats.speed - 4, enemy.speed, "Tú", 1.6, 0.25)
                enemyHp -= roll.first
                log += "💥 " + roll.second
            }
            PlayerAction.DEFEND -> {
                defending = true
                log += "🛡️ Te pones en guardia: el próximo daño se reduce a la mitad."
            }
            PlayerAction.DODGE -> {
                dodging = true
                log += "💨 Te preparas para esquivar el próximo ataque."
            }
            PlayerAction.SKILL -> {
                val def = skillId?.let(resolveSkill)
                val own = skillId?.let(resolveOwned)
                if (def == null || own == null) {
                    log += "❌ No conoces esa habilidad."
                } else if (playerMp < def.mpCost) {
                    log += "❌ MP insuficiente para " + def.name + "."
                } else {
                    playerMp -= def.mpCost
                    if (def.heal > 0) {
                        playerHp = (playerHp + def.heal).coerceAtMost(stats.maxHp)
                        log += "💚 " + def.name + " restaura " + def.heal + " HP."
                    } else {
                        val raw = def.basePower + stats.attack / 2.0
                        val power = own.mastery.powerMultiplier * (if (own.awakened) 1.5 else 1.0)
                        val dmg = (raw * power * Random.nextDouble(0.9, 1.1)).roundToInt().coerceAtLeast(1)
                        enemyHp -= dmg
                        var line = "✨ " + def.name + " inflige " + dmg + " de daño"
                        if (own.awakened) line += " (despertar)"
                        log += line + "."
                    }
                    usedSkill = def.id
                    skillXp = 12 + def.basePower / 4
                }
            }
            PlayerAction.ITEM -> {
                if (potions <= 0) {
                    log += "❌ No te quedan pociones."
                } else {
                    potions -= 1
                    playerHp = (playerHp + 35).coerceAtMost(stats.maxHp)
                    playerMp = (playerMp + 20).coerceAtMost(stats.maxMp)
                    log += "🧪 Poción usada: +35 HP, +20 MP."
                }
            }
        }

        if (usedSkill != null && resolveOwned(usedSkill)?.awakened == true) {
            playerHp = (playerHp - 3).coerceAtLeast(0)
            log += "🩸 El despertar inestable te cuesta 3 HP."
        }

        if (enemyHp <= 0) {
            val reward = onVictory(enemy.rewardCoins, enemy.rewardPh, enemy.rewardSkillXp + skillXp, usedSkill)
            log += "🎉 ¡Victoria! +" + reward.coins + " oro · +" + reward.ph + " PH · +" + reward.skillXp + " XP."
            _state.value = current.copy(
                enemyHp = 0,
                playerHp = playerHp,
                playerMp = playerMp,
                potions = potions,
                log = trimLog(log),
                active = false,
                victory = true,
                rewardCoins = reward.coins,
                rewardPh = reward.ph,
                rewardSkillXp = reward.skillXp,
            )
            return
        }

        val enemyAction = chooseEnemyAction(enemyHp, enemy.hp)
        var incoming = 0
        when (enemyAction) {
            EnemyAction.ATTACK -> {
                incoming = rollAttack(enemy.attack, stats.defense, enemy.speed, stats.speed, enemy.name, 1.0, 0.1).first
            }
            EnemyAction.HEAVY_ATTACK -> {
                incoming = rollAttack(enemy.attack, stats.defense, enemy.speed - 3, stats.speed, enemy.name, 1.5, 0.2).first
                log += "💥 " + enemy.name + " usa ataque pesado."
            }
            EnemyAction.DEFEND -> {
                log += "🛡️ " + enemy.name + " se defiende."
            }
            EnemyAction.SPECIAL -> {
                incoming = (enemy.attack * 1.4).roundToInt()
                log += "⚡ " + enemy.name + " usa su habilidad especial."
            }
            EnemyAction.BUFF -> {
                log += "📈 " + enemy.name + " se potencia."
            }
        }

        if (dodging && Random.nextDouble() < 0.6) {
            incoming = 0
            log += "💨 ¡Esquivaste el ataque de " + enemy.name + "!"
        }
        if (defending) incoming = incoming / 2
        if (incoming > 0) {
            playerHp = (playerHp - incoming).coerceAtLeast(0)
            log += "🩸 " + enemy.name + " te inflige " + incoming + " de daño."
        }

        playerMp = (playerMp + 3).coerceAtMost(stats.maxMp)
        val defeated = playerHp <= 0
        if (defeated) log += "💀 Has caído. Vuelve a intentarlo."

        _state.value = current.copy(
            enemyHp = enemyHp.coerceAtLeast(1),
            playerHp = playerHp,
            playerMp = playerMp,
            potions = potions,
            turn = current.turn + 1,
            log = trimLog(log),
            active = !defeated,
            defeat = defeated,
        )
    }

    private fun rollAttack(
        attack: Int,
        defense: Int,
        attackSpeed: Int,
        defenseSpeed: Int,
        who: String,
        power: Double,
        critChance: Double,
    ): Pair<Int, String> {
        val hitChance = (0.8 + (attackSpeed - defenseSpeed) * 0.01).coerceIn(0.45, 0.98)
        if (Random.nextDouble() > hitChance) {
            return 0 to (who + " falla el ataque.")
        }
        val base = (attack * power - defense / 2.0).coerceAtLeast(1.0)
        var damage = (base * Random.nextDouble(0.9, 1.1)).roundToInt().coerceAtLeast(1)
        var crit = false
        if (Random.nextDouble() < critChance) {
            damage = (damage * 1.6).roundToInt()
            crit = true
        }
        var line = who + " inflige " + damage + " de daño"
        if (crit) line += " ¡CRÍTICO!"
        return damage to (line + ".")
    }

    private fun chooseEnemyAction(enemyHp: Int, enemyMaxHp: Int): EnemyAction {
        val ratio = enemyHp.toDouble() / enemyMaxHp.toDouble()
        val roll = Random.nextDouble()
        return when {
            ratio < 0.3 && roll < 0.5 -> EnemyAction.SPECIAL
            ratio < 0.6 && roll < 0.2 -> EnemyAction.DEFEND
            roll < 0.25 -> EnemyAction.HEAVY_ATTACK
            else -> EnemyAction.ATTACK
        }
    }

    private fun trimLog(log: List<String>): List<String> {
        return if (log.size > 60) log.takeLast(60) else log
    }
}
