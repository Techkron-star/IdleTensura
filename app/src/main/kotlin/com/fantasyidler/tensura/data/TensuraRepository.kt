package com.fantasyidler.tensura.data

import android.content.Context
import com.fantasyidler.tensura.domain.CombatEngine
import com.fantasyidler.tensura.domain.CombatReward
import com.fantasyidler.tensura.domain.MasteryLevel
import com.fantasyidler.tensura.domain.MultiplierType
import com.fantasyidler.tensura.domain.OwnedSkill
import com.fantasyidler.tensura.domain.SkillDefinition
import com.fantasyidler.tensura.domain.TensuraState
import com.fantasyidler.tensura.domain.label
import com.fantasyidler.tensura.domain.learnCost
import com.fantasyidler.tensura.domain.levelOf
import com.fantasyidler.tensura.domain.masteryCost
import com.fantasyidler.tensura.domain.phCost
import com.fantasyidler.tensura.domain.rankMultiplier
import com.fantasyidler.tensura.domain.value
import com.fantasyidler.tensura.domain.withLevel
import com.fantasyidler.tensura.domain.xpToNext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.random.Random

class TensuraRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val catalog: Map<String, SkillDefinition> = loadCatalog(context)
    val catalogList: List<SkillDefinition> = catalog.values.sortedWith(
        compareBy({ it.rank.ordinal }, { it.name })
    )

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<TensuraState> = _state.asStateFlow()

    val combat: CombatEngine = CombatEngine(
        resolveSkill = { id -> catalog[id] },
        resolveOwned = { id -> _state.value.owned[id] },
        playerStats = { _state.value.stats },
        onVictory = { coins, ph, xp, skillId -> recordVictory(coins, ph, xp, skillId) },
    )

    // ------------------------------------------------------------------
    // Persistencia
    // ------------------------------------------------------------------

    private fun loadState(): TensuraState {
        val raw = prefs.getString(KEY_STATE, null) ?: return TensuraState()
        return try {
            json.decodeFromString(TensuraState.serializer(), raw)
        } catch (t: Throwable) {
            TensuraState()
        }
    }

    private fun persist() {
        prefs.edit().putString(KEY_STATE, json.encodeToString(TensuraState.serializer(), _state.value)).apply()
    }

    private fun loadCatalog(context: Context): Map<String, SkillDefinition> {
        val fromAssets = try {
            val text = context.assets.open(ASSET_CATALOG).bufferedReader().use { it.readText() }
            json.decodeFromString(ListSerializer(SkillDefinition.serializer()), text)
        } catch (t: Throwable) {
            emptyList()
        }
        val merged = fromAssets + FallbackSkillCatalog.skills
        return merged.distinctBy { it.id }.associateBy { it.id }
    }

    // ------------------------------------------------------------------
    // PH, aprendizaje y maestría
    // ------------------------------------------------------------------

    fun addPh(amount: Long) {
        if (amount <= 0L) return
        val s = _state.value
        val applied = (amount * s.multipliers.phGain).toLong().coerceAtLeast(1L)
        _state.value = s.copy(ph = s.ph + applied, lifetimePh = s.lifetimePh + applied)
        persist()
    }

    fun learnSkill(id: String): String {
        val def = catalog[id] ?: return "Habilidad desconocida."
        val s = _state.value
        if (id in s.owned) return "Ya conoces " + def.name + "."
        val cost = learnCost(def.rank)
        if (s.ph < cost) return "Necesitas " + cost + " PH para aprender " + def.name + "."
        _state.value = s.copy(
            ph = s.ph - cost,
            owned = s.owned + (id to OwnedSkill(skillId = id)),
        )
        persist()
        return "Aprendiste " + def.name + "."
    }

    fun upgradeMastery(id: String): String {
        val s = _state.value
        val own = s.owned[id] ?: return "No conoces esa habilidad."
        if (own.mastery == MasteryLevel.MASTER) return "Ya está en maestría Maestro."
        val cost = masteryCost(own.mastery)
        if (s.ph < cost) return "Necesitas " + cost + " PH."
        val next = if (own.mastery == MasteryLevel.BASIC) MasteryLevel.ADVANCED else MasteryLevel.MASTER
        _state.value = s.copy(
            ph = s.ph - cost,
            owned = s.owned + (id to own.copy(mastery = next, masteryXp = 0)),
        )
        persist()
        val name = catalog[id]?.name ?: id
        return "Maestría de " + name + " ahora es " + next.label + "."
    }

    fun grantSkillXp(skillId: String, amount: Int) {
        if (amount <= 0) return
        val s = _state.value
        val own = s.owned[skillId] ?: return
        var mastery = own.mastery
        var xp = own.masteryXp + (amount * s.multipliers.skillXp).roundToInt()
        while (mastery != MasteryLevel.MASTER && xp >= xpToNext(mastery)) {
            xp -= xpToNext(mastery)
            mastery = when (mastery) {
                MasteryLevel.BASIC -> MasteryLevel.ADVANCED
                MasteryLevel.ADVANCED -> MasteryLevel.MASTER
                MasteryLevel.MASTER -> MasteryLevel.MASTER
            }
        }
        _state.value = s.copy(
            owned = s.owned + (skillId to own.copy(
                mastery = mastery,
                masteryXp = xp,
                timesUsed = own.timesUsed + 1,
            )),
        )
        persist()
    }

    // ------------------------------------------------------------------
    // Tienda de multiplicadores permanentes
    // ------------------------------------------------------------------

    fun buyMultiplier(type: MultiplierType): String {
        val s = _state.value
        val level = s.multipliers.levelOf(type)
        val cost = type.phCost(level)
        if (s.ph < cost) return "Necesitas " + cost + " PH."
        _state.value = s.copy(
            ph = s.ph - cost,
            multipliers = s.multipliers.withLevel(type, level + 1),
        )
        persist()
        return type.label() + " mejorado a nivel " + (level + 1) + "."
    }

    // ------------------------------------------------------------------
    // Colas automáticas
    // ------------------------------------------------------------------

    fun startQueue(index: Int, skillId: String, durationMs: Long): String {
        val s = _state.value
        if (skillId !in s.owned) return "Primero aprende esa habilidad."
        val slot = s.queue.getOrNull(index) ?: return "Slot inválido."
        val queue = s.queue.toMutableList()
        queue[index] = slot.copy(
            skillId = skillId,
            active = true,
            startedAtMs = System.currentTimeMillis(),
            durationMs = durationMs,
        )
        _state.value = s.copy(queue = queue)
        persist()
        val name = catalog[skillId]?.name ?: skillId
        return "Entrenando " + name + ". Se reinicia automáticamente."
    }

    fun stopQueue(index: Int): String {
        tickQueue()
        val s = _state.value
        val slot = s.queue.getOrNull(index) ?: return "Slot inválido."
        if (!slot.active) return "Ese slot ya está detenido."
        val queue = s.queue.toMutableList()
        queue[index] = slot.copy(active = false, startedAtMs = 0L)
        _state.value = s.copy(queue = queue)
        persist()
        return "Slot " + (index + 1) + " detenido. Lo pendiente se conserva."
    }

    fun tickQueue(now: Long = System.currentTimeMillis()) {
        val s = _state.value
        var changed = false
        val queue = s.queue.map { slot ->
            val skillId = slot.skillId
            if (!slot.active || skillId == null || slot.durationMs <= 0L) {
                slot
            } else if (now - slot.startedAtMs >= slot.durationMs) {
                changed = true
                val cycles = ((now - slot.startedAtMs) / slot.durationMs).coerceAtLeast(1L).toInt()
                val def = catalog[skillId]
                val rankMult = rankMultiplier(def?.rank)
                val minutes = slot.durationMs / 60000.0
                val phGain = (minutes * 1.5 * rankMult * s.multipliers.phGain * cycles).toLong().coerceAtLeast(1L)
                val coinsGain = (minutes * 8.0 * rankMult * s.multipliers.coins * cycles).toLong()
                val xpGain = (minutes * 4.0 * rankMult * s.multipliers.skillXp * cycles).toInt().coerceAtLeast(1)
                slot.copy(
                    startedAtMs = slot.startedAtMs + cycles.toLong() * slot.durationMs,
                    pendingPh = slot.pendingPh + phGain,
                    pendingCoins = slot.pendingCoins + coinsGain,
                    pendingXp = slot.pendingXp + xpGain,
                    completedSessions = slot.completedSessions + cycles,
                )
            } else {
                slot
            }
        }
        if (changed) {
            _state.value = s.copy(queue = queue)
            persist()
        }
    }

    fun collectQueue(index: Int): String {
        tickQueue()
        val s = _state.value
        val slot = s.queue.getOrNull(index) ?: return "Slot inválido."
        if (slot.pendingPh == 0L && slot.pendingCoins == 0L && slot.pendingXp == 0) {
            return "No hay nada que recoger en ese slot."
        }
        val queue = s.queue.toMutableList()
        queue[index] = slot.copy(pendingPh = 0L, pendingCoins = 0L, pendingXp = 0)
        _state.value = s.copy(
            ph = s.ph + slot.pendingPh,
            lifetimePh = s.lifetimePh + slot.pendingPh,
            coins = s.coins + slot.pendingCoins,
            queue = queue,
        )
        if (slot.skillId != null) grantSkillXp(slot.skillId, slot.pendingXp)
        persist()
        return "+" + slot.pendingPh + " PH · +" + slot.pendingCoins + " oro · +" + slot.pendingXp + " XP"
    }

    fun collectAll(): String {
        tickQueue()
        val s = _state.value
        var totalPh = 0L
        var totalCoins = 0L
        var totalXp = 0
        val xpPerSkill = mutableMapOf<String, Int>()
        val queue = s.queue.map { slot ->
            totalPh += slot.pendingPh
            totalCoins += slot.pendingCoins
            totalXp += slot.pendingXp
            val id = slot.skillId
            if (id != null && slot.pendingXp > 0) {
                xpPerSkill[id] = (xpPerSkill[id] ?: 0) + slot.pendingXp
            }
            slot.copy(pendingPh = 0L, pendingCoins = 0L, pendingXp = 0)
        }
        if (totalPh == 0L && totalCoins == 0L && totalXp == 0) {
            return "No hay recompensas pendientes todavía."
        }
        _state.value = s.copy(
            ph = s.ph + totalPh,
            lifetimePh = s.lifetimePh + totalPh,
            coins = s.coins + totalCoins,
            queue = queue,
        )
        xpPerSkill.forEach { (id, amount) -> grantSkillXp(id, amount) }
        persist()
        return "Recogido: +" + totalPh + " PH · +" + totalCoins + " oro · +" + totalXp + " XP"
    }

    fun applyOfflineProgress() {
        tickQueue()
    }

    // ------------------------------------------------------------------
    // Fusión, estimulación y trascendencia
    // ------------------------------------------------------------------

    fun fuse(idA: String, idB: String): String {
        val s = _state.value
        val a = s.owned[idA] ?: return "No conoces la primera habilidad."
        val b = s.owned[idB] ?: return "No conoces la segunda habilidad."
        if (idA == idB) return "Elige dos habilidades distintas."
        if (a.mastery != MasteryLevel.MASTER || b.mastery != MasteryLevel.MASTER) {
            return "Ambas habilidades deben estar en maestría Maestro."
        }
        val cost = 30L
        if (s.ph < cost) return "Necesitas " + cost + " PH para fusionar."

        val roll = Random.nextInt(1, 101) + s.stats.willpower / 10 + s.stats.intelligence / 10
        val potatoRoll = Random.nextInt(100) < 3
        val charged = s.copy(ph = s.ph - cost)

        if (potatoRoll) {
            val potatoSkill = catalogList.firstOrNull { it.potato && it.id !in s.owned }
            if (potatoSkill != null) {
                _state.value = charged.copy(
                    owned = charged.owned + (potatoSkill.id to OwnedSkill(skillId = potatoSkill.id)),
                )
                persist()
                return "🥔 ¡Fusión absurda! Obtuviste " + potatoSkill.name + "."
            }
        }

        if (roll > 70) {
            val rankA = catalog[idA]?.rank?.ordinal ?: 0
            val rankB = catalog[idB]?.rank?.ordinal ?: 0
            val maxRank = maxOf(rankA, rankB)
            val candidate = catalogList
                .filter { it.rank.ordinal > maxRank && it.id !in s.owned && !it.potato }
                .randomOrNull()
            if (candidate != null) {
                _state.value = charged.copy(
                    owned = charged.owned + (candidate.id to OwnedSkill(skillId = candidate.id)),
                )
                persist()
                return "✨ Fusión exitosa: nace " + candidate.name + "."
            }
            _state.value = charged.copy(ph = charged.ph + 15L)
            persist()
            return "✨ Fusión exitosa, pero no quedan habilidades superiores. +15 PH."
        }

        _state.value = charged
        persist()
        return "❌ Fusión fallida (tirada " + roll + "/100). Las habilidades siguen intactas."
    }

    fun stimulate(id: String): String {
        val s = _state.value
        val own = s.owned[id] ?: return "No conoces esa habilidad."
        if (own.awakened) return "Esa habilidad ya está despertada."
        val cost = 50L
        if (s.ph < cost) return "Necesitas " + cost + " PH."
        val success = Random.nextInt(100) < 40
        val updated = if (success) own.copy(awakened = true, awakenings = own.awakenings + 1) else own
        _state.value = s.copy(ph = s.ph - cost, owned = s.owned + (id to updated))
        persist()
        val name = catalog[id]?.name ?: id
        return if (success) {
            "🔥 ¡" + name + " ha despertado! +50% de poder, pero drena HP."
        } else {
            "La estimulación falló. El PH se consumió."
        }
    }

    fun transcend(id: String): String {
        val s = _state.value
        val own = s.owned[id] ?: return "No conoces esa habilidad."
        val def = catalog[id] ?: return "Habilidad desconocida."
        val targetId = def.evolvesTo ?: return "Esta habilidad aún no puede trascender."
        val target = catalog[targetId] ?: return "La evolución no está disponible."
        if (own.mastery != MasteryLevel.MASTER) return "Necesitas maestría Maestro para trascender."
        if (targetId in s.owned) return "Ya conoces " + target.name + "."
        val cost = 60L
        if (s.ph < cost) return "Necesitas " + cost + " PH."
        _state.value = s.copy(
            ph = s.ph - cost,
            owned = s.owned + (targetId to OwnedSkill(skillId = targetId)),
        )
        persist()
        return "🌌 " + def.name + " trasciende y se convierte en " + target.name + "."
    }

    // ------------------------------------------------------------------
    // Recompensas de combate
    // ------------------------------------------------------------------

    private fun recordVictory(baseCoins: Int, basePh: Long, baseXp: Int, skillId: String?): CombatReward {
        val s = _state.value
        val coins = (baseCoins * s.multipliers.coins).roundToInt().coerceAtLeast(1)
        val ph = (basePh * s.multipliers.phGain).toLong().coerceAtLeast(1L)
        val xp = (baseXp * s.multipliers.skillXp).roundToInt().coerceAtLeast(1)
        _state.value = s.copy(
            coins = s.coins + coins,
            ph = s.ph + ph,
            lifetimePh = s.lifetimePh + ph,
            flags = s.flags.copy(
                totalCombats = s.flags.totalCombats + 1,
                totalVictories = s.flags.totalVictories + 1,
            ),
        )
        if (skillId != null) grantSkillXp(skillId, xp)
        persist()
        return CombatReward(coins = coins, ph = ph, skillXp = xp)
    }

    fun resetProgress(): String {
        _state.value = TensuraState()
        persist()
        combat.reset()
        return "Progreso de Tensura reiniciado."
    }

    companion object {
        private const val PREFS = "tensura_player"
        private const val KEY_STATE = "state_v1"
        private const val ASSET_CATALOG = "data/tensura/skills.json"

        @Volatile
        private var instance: TensuraRepository? = null

        fun get(context: Context): TensuraRepository {
            return instance ?: synchronized(this) {
                instance ?: TensuraRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
