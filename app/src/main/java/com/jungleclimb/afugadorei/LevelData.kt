package com.jungleclimb.afugadorei

import kotlin.math.abs

data class LevelSpec(
    val levelNumber: Int,
    val worldNumber: Int,
    val title: String,
    val subtitle: String,
    val isBossLevel: Boolean,
    val timeLimitSeconds: Int,
    val startX: Float,
    val startY: Float,
    val checkpointX: Float,
    val checkpointY: Float,
    val goalX: Float,
    val goalY: Float,
    val bossX: Float,
    val bossY: Float,
    val barrelSpawnInterval: Float,
    val barrelSpeed: Float,
    val platforms: List<Platform>,
    val ladders: List<Ladder>,
    val enemies: List<Enemy>,
    val collectibles: List<Collectible>,
    val bossSwitches: List<Collectible> = emptyList()
)

object LevelRepository {

    fun getWorldName(world: Int): String = when (world) {
        1 -> "Mundo 1: Canteiro Caótico"
        2 -> "Mundo 2: Selva Vertical"
        3 -> "Mundo 3: Fábrica Abandonada"
        else -> "Mundo 4: Fortaleza do Gorila"
    }

    fun createLevel(levelNum: Int): LevelSpec {
        val lvl = levelNum.coerceIn(1, 12)
        val world = ((lvl - 1) / 3) + 1
        val stageInWorld = ((lvl - 1) % 3) + 1
        val isBoss = (stageInWorld == 3)

        // Alturas base das 5 camadas verticais de escalada (de baixo para cima)
        val yFloor = 660f
        val yT1 = 545f
        val yT2 = 430f
        val yT3 = 315f
        val yT4 = 200f
        val yTop = 105f

        // Inclinação varia por fase para alterar a física dos barris
        val tilt = if (lvl % 2 == 1) 18f else -18f

        val platforms = mutableListOf<Platform>()
        val ladders = mutableListOf<Ladder>()
        val enemies = mutableListOf<Enemy>()
        val items = mutableListOf<Collectible>()
        val switches = mutableListOf<Collectible>()

        // Piso base
        platforms.add(Platform(80f, yFloor, 1200f, yFloor))

        when (world) {
            1 -> {
                // MUNDO 1: Vigas inclinadas clássicas, escadas e barris rolantes
                platforms.add(Platform(100f, yT1 - tilt, 1120f, yT1 + tilt))
                platforms.add(Platform(160f, yT2 + tilt, 1180f, yT2 - tilt))
                platforms.add(Platform(100f, yT3 - tilt, 1120f, yT3 + tilt))
                platforms.add(Platform(160f, yT4 + tilt, 1180f, yT4 - tilt))
                platforms.add(Platform(380f, yTop, 900f, yTop))

                ladders.add(Ladder(if (tilt > 0) 1040f else 180f, yT1 + abs(tilt), yFloor))
                ladders.add(Ladder(620f, yT1, yFloor, isBroken = (stageInWorld >= 2)))
                ladders.add(Ladder(if (tilt > 0) 240f else 1060f, yT2 + abs(tilt), yT1 + abs(tilt)))
                ladders.add(Ladder(if (tilt > 0) 1040f else 220f, yT3 + abs(tilt), yT2 + abs(tilt)))
                ladders.add(Ladder(680f, yT3, yT2, isBroken = true))
                ladders.add(Ladder(if (tilt > 0) 260f else 1040f, yT4 + abs(tilt), yT3 + abs(tilt)))
                ladders.add(Ladder(780f, yTop, yT4))
            }
            2 -> {
                // MUNDO 2: Selva Vertical - Plataformas divididas (gaps que exigem salto) e cipós
                platforms.add(Platform(100f, yT1, 600f, yT1 + 10f))
                platforms.add(Platform(700f, yT1 + 10f, 1180f, yT1))
                platforms.add(Platform(120f, yT2, 1140f, yT2 - 14f, isMoving = (stageInWorld >= 2), moveRangeX = 45f, moveSpeed = 2.0f))
                platforms.add(Platform(100f, yT3 - 12f, 560f, yT3))
                platforms.add(Platform(670f, yT3, 1180f, yT3 + 12f))
                platforms.add(Platform(160f, yT4 + 10f, 1140f, yT4 - 10f))
                platforms.add(Platform(360f, yTop, 920f, yTop))

                ladders.add(Ladder(520f, yT1 + 8f, yFloor))
                ladders.add(Ladder(1060f, yT1, yFloor))
                ladders.add(Ladder(220f, yT2, yT1))
                ladders.add(Ladder(880f, yT2 - 8f, yT1 + 6f, isBroken = true))
                ladders.add(Ladder(480f, yT3, yT2))
                ladders.add(Ladder(1040f, yT3 + 8f, yT2 - 10f))
                ladders.add(Ladder(260f, yT4 + 8f, yT3 - 8f))
                ladders.add(Ladder(820f, yTop, yT4))
            }
            3 -> {
                // MUNDO 3: Fábrica Abandonada - Esteiras rolantes, plataformas móveis e pisos elétricos
                val conv = if (stageInWorld % 2 == 0) 55f else -55f
                platforms.add(Platform(100f, yT1 - 12f, 1140f, yT1 + 12f, conveyorSpeed = conv))
                platforms.add(Platform(140f, yT2 + 12f, 1160f, yT2 - 12f, isTrap = true))
                platforms.add(Platform(120f, yT3 - 10f, 1120f, yT3 + 10f, isMoving = true, moveRangeX = 65f, moveSpeed = 2.4f))
                platforms.add(Platform(160f, yT4 + 12f, 1160f, yT4 - 12f, conveyorSpeed = -conv))
                platforms.add(Platform(350f, yTop, 930f, yTop))

                ladders.add(Ladder(1060f, yT1 + 10f, yFloor))
                ladders.add(Ladder(240f, yT2 + 10f, yT1 - 8f))
                ladders.add(Ladder(640f, yT2, yT1, isBroken = true))
                ladders.add(Ladder(1020f, yT3 + 8f, yT2 - 8f))
                ladders.add(Ladder(260f, yT4 + 10f, yT3 - 8f))
                ladders.add(Ladder(800f, yTop, yT4))
            }
            else -> {
                // MUNDO 4: Fortaleza do Gorila - Combinação de saltos de precisão, armadilhas e plataformas móveis
                platforms.add(Platform(90f, yT1 - 16f, 580f, yT1))
                platforms.add(Platform(680f, yT1, 1180f, yT1 + 16f, isTrap = true))
                platforms.add(Platform(140f, yT2 + 16f, 1150f, yT2 - 16f, conveyorSpeed = -65f))
                platforms.add(Platform(100f, yT3 - 14f, 600f, yT3, isMoving = true, moveRangeX = 50f, moveSpeed = 2.8f))
                platforms.add(Platform(700f, yT3, 1180f, yT3 + 14f))
                platforms.add(Platform(160f, yT4 + 15f, 1160f, yT4 - 15f, isTrap = (stageInWorld >= 2)))
                platforms.add(Platform(340f, yTop, 940f, yTop))

                ladders.add(Ladder(1100f, yT1 + 14f, yFloor))
                ladders.add(Ladder(220f, yT2 + 12f, yT1 - 12f))
                ladders.add(Ladder(1040f, yT3 + 12f, yT2 - 12f))
                ladders.add(Ladder(240f, yT4 + 12f, yT3 - 10f))
                ladders.add(Ladder(820f, yTop, yT4))
            }
        }

        // Inimigos escalonados conforme o mundo e fase
        enemies.add(Enemy(450f, yT1, 200f, 950f, 75f + lvl * 6f, EnemyType.CRAWLER))
        if (lvl >= 2) {
            enemies.add(Enemy(650f, yT2, 240f, 1020f, 85f + lvl * 6f, EnemyType.PATROLLER))
        }
        if (lvl >= 4) {
            enemies.add(Enemy(520f, yT3 - 35f, 220f, 1000f, 95f + lvl * 5f, EnemyType.FLYER))
        }
        if (lvl >= 7) {
            enemies.add(Enemy(700f, yT4, 260f, 1040f, 110f + lvl * 5f, EnemyType.PATROLLER))
        }

        // Coletáveis, Bônus de Martelo, Artefato Secreto e Checkpoint no meio da escalada
        items.add(Collectible(350f, yT1 - 12f, ItemType.COIN))
        items.add(Collectible(820f, yT2 - 12f, ItemType.COIN))
        items.add(Collectible(420f, yT3 - 12f, ItemType.COIN))
        items.add(Collectible(880f, yT4 - 12f, ItemType.COIN))
        items.add(Collectible(1130f, yFloor - 10f, ItemType.SECRET_ARTIFACT))
        items.add(Collectible(190f, yT2 - 14f, ItemType.HAMMER_POWER))
        items.add(Collectible(640f, yT2 - 10f, ItemType.CHECKPOINT_FLAG))

        // Nas fases de Chefe (Fases 3, 6, 9 e 12), o jogador também pode ativar alavancas estruturais para derrotar o Rei Korg
        if (isBoss) {
            switches.add(Collectible(390f, yTop, ItemType.COIN))
            switches.add(Collectible(640f, yT4, ItemType.COIN))
            switches.add(Collectible(880f, yTop, ItemType.COIN))
        }

        val titles = arrayOf(
            "Andaime Inicial", "Vigas Cruzadas", "Chefe: Guindaste do Rei",
            "Trilha das Raízes", "Pontes de Cipó", "Chefe: Copa Selvagem",
            "Esteiras de Fogo", "Curto-Circuito", "Chefe: Fornalha Central",
            "Muralha de Pedra", "Torre das Chamas", "Confronto Final: Rei Korg"
        )

        return LevelSpec(
            levelNumber = lvl,
            worldNumber = world,
            title = "Fase $lvl - ${titles[lvl - 1]}",
            subtitle = getWorldName(world),
            isBossLevel = isBoss,
            timeLimitSeconds = 120 - (world * 5),
            startX = 150f,
            startY = yFloor,
            checkpointX = 640f,
            checkpointY = yT2,
            goalX = 640f,
            goalY = yTop,
            bossX = 480f,
            bossY = yTop,
            barrelSpawnInterval = (3.3f - lvl * 0.16f).coerceAtLeast(1.35f),
            barrelSpeed = 165f + lvl * 14f,
            platforms = platforms,
            ladders = ladders,
            enemies = enemies,
            collectibles = items,
            bossSwitches = switches
        )
    }
}
