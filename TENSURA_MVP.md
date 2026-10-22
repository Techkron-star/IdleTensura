# IdleTensura — MVP del Sistema de Habilidades (Entry 4)

Primera versión de prueba, autocontenida y compilable, integrada en el fork de IdleFantasy.

## Qué incluye

Todo el código vive en un paquete nuevo y aislado: `com.fantasyidler.tensura`.
No modifica ninguna pantalla existente del juego.

| Área | Archivo | Descripción |
|------|---------|-------------|
| Dominio | `domain/TensuraModels.kt` | 6 rangos, 4 tipos, 10 categorías, 3 niveles de maestría, PH, multiplicadores, colas |
| Combate | `domain/CombatEngine.kt` | Combate **por turnos**: el jugador decide atacar, ataque pesado, defender, esquivar, usar habilidad o poción. IA enemiga + recompensas |
| Datos | `data/TensuraRepository.kt` | Persistencia (SharedPreferences + kotlinx.serialization), PH, maestría, fusión, estímulo, trascendencia, colas con progreso automático |
| Datos | `data/FallbackSkillCatalog.kt` | 21 habilidades de respaldo (el juego las usa si el asset falta) |
| Asset | `assets/data/tensura/skills.json` | Mismo catálogo en JSON. Fuente de verdad editable sin recompilar lógica |
| UI | `ui/TensuraHubScreen.kt` | Hub con pestañas + componentes base |
| UI | `ui/TensuraSkillsShop.kt` | Habilidades (maestría, fusión, estímulo, trascendencia) y tienda de multiplicadores |
| UI | `ui/TensuraQueueCombat.kt` | Colas automáticas y combate por turnos |
| Entrada | `ui/navigation/Screen.kt` + `AppNavigation.kt` | Ruta `tensura` accesible con el botón ✨ de la pantalla de Inicio |

## Progreso y colas (rework "menos tedioso")

- 5 slots de cola simultáneos.
- La sesión se **reinicia sola** al terminar y **acumula** recompensas.
- Botón "Recoger todo". No hace falta entrar cada X minutos.
- Progreso offline: al abrir la app se calculan las sesiones completadas.
- El PH sobrante se invierte en **multiplicadores permanentes**.

## Tienda de multiplicadores permanentes

7 mejoras que nunca se pierden, con coste exponencial (x1.5 por nivel):

XP de habilidades · XP de combate · Cantidad de botín · Monedas ·
Ganancia de PH · Velocidad de cola · Probabilidad de drop

## Combate dinámico

Ya no es solo mirar cómo ataca el personaje. En cada turno eliges:

- ⚔ Atacar
- 💥 Ataque pesado (más daño, menos precisión)
- 🛡 Defender (reduce el daño entrante a la mitad)
- 💨 Esquivar
- ✨ Usar habilidad (consume MP, escala con la maestría)
- 🧪 Poción (3 por combate)

La maestría sube al usar habilidades. Con maestría **Maestro** puedes fusionar,
estimular (despertar, +50% de poder con drenaje de HP) y trascender a rangos superiores.

## Cómo compilar

1. Abrir el proyecto en Android Studio y sincronizar Gradle.
2. Build → Build APK(s). El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
3. Instalar en el móvil. Entra al juego y pulsa el botón **✨** de la pantalla de Inicio para abrir el hub de Tensura.

No hacen falta dependencias nuevas: el módulo ya usa Compose, Hilt, Room y kotlinx.serialization.

## Notas para la siguiente iteración

- Migrar el estado a Room si se quiere compartir con el guardado del juego original.
- Añadir imágenes/iconos propios y sonido a las pantallas nuevas.
