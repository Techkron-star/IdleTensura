package com.fantasyidler.tensura.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Valida la integridad del catálogo editable (assets/data/tensura/skills.json):
 * ids únicos, evoluciones existentes, rangos ascendentes y ausencia de ciclos.
 */
class SkillCatalogIntegrityTest {

    private val catalog: List<SkillDefinition> = run {
        val roots = generateSequence(File("").absoluteFile) { it.parentFile }.take(4).toList()
        val candidates = roots.flatMap { root ->
            listOf(
                File(root, "src/main/assets/data/tensura/skills.json"),
                File(root, "app/src/main/assets/data/tensura/skills.json"),
            )
        }
        val file = candidates.firstOrNull { it.exists() }
            ?: error("No se encontró skills.json en " + candidates.map { it.absolutePath })
        Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(SkillDefinition.serializer()), file.readText())
    }

    private val byId: Map<String, SkillDefinition> = catalog.associateBy { it.id }

    @Test
    fun `catalog ids are unique`() {
        assertEquals(catalog.size, byId.size)
    }

    @Test
    fun `every evolution points to an existing skill`() {
        catalog.forEach { def ->
            def.evolvesTo?.let { target ->
                assertNotNull("${def.id} evoluciona a $target, que no existe", byId[target])
            }
        }
    }

    @Test
    fun `evolutions always raise the rank`() {
        catalog.forEach { def ->
            val next = def.evolvesTo?.let { byId[it] } ?: return@forEach
            assertTrue(
                "${def.id} (${def.rank}) -> ${next.id} (${next.rank}) no sube de rango",
                next.rank.ordinal > def.rank.ordinal,
            )
        }
    }

    @Test
    fun `there are no evolution cycles`() {
        catalog.forEach { start ->
            val seen = mutableSetOf<String>()
            var current: SkillDefinition? = start
            while (current != null) {
                assertTrue("Ciclo evolutivo en ${start.id}", seen.add(current.id))
                current = current.evolvesTo?.let { byId[it] }
            }
        }
    }

    @Test
    fun `previously orphan skills now evolve`() {
        val orphans = listOf(
            "iron_skin", "water_veil", "wind_step", "analyze", "potato_sword",
            "shadow_movement", "healing_water", "blade_of_the_void",
            "celestial_shell", "inferno_domain", "sage_of_annihilation",
        )
        orphans.forEach { id ->
            assertNotNull("Falta la habilidad $id", byId[id])
            assertNotNull("$id sigue sin evolución", byId[id]?.evolvesTo)
        }
    }

    @Test
    fun `ultimate skills are terminal`() {
        catalog.filter { it.rank == SkillRank.ULTIMATE }.forEach { def ->
            assertTrue("${def.id} es Ultimate y no debería evolucionar", def.evolvesTo == null)
        }
    }

    @Test
    fun `evolution targets are not self references`() {
        catalog.forEach { def ->
            assertTrue("${def.id} evoluciona a sí misma", def.evolvesTo != def.id)
        }
    }
}
