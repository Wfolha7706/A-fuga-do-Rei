package com.jungleclimb.afugadorei

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Espaço virtual fixo de 1280x720 (garante física idêntica em qualquer resolução de celular)
const val VIRTUAL_W = 1280f
const val VIRTUAL_H = 720f

data class Platform(
    var x1: Float,
    var y1: Float,
    var x2: Float,
    var y2: Float,
    val isMoving: Boolean = false,
    val moveRangeX: Float = 0f,
    val moveSpeed: Float = 0f,
    val isTrap: Boolean = false,
    val conveyorSpeed: Float = 0f // >0 empurra p/ direita, <0 p/ esquerda (Mundo 3)
) {
    private val baseX1 = x1
    private val baseX2 = x2
    var trapTimer = 0f
    var trapActive = false

    fun update(dt: Float, elapsed: Float) {
        if (isMoving) {
            val offset = sin(elapsed * moveSpeed) * moveRangeX
            x1 = baseX1 + offset
            x2 = baseX2 + offset
        }
        if (isTrap) {
            trapTimer += dt
            trapActive = (trapTimer % 4.0f) > 2.8f // Fica eletrificada/espinhos a cada ciclo
        }
    }

    fun getYAt(x: Float): Float {
        if (x2 == x1) return y1
        val t = ((x - x1) / (x2 - x1)).coerceIn(0f, 1f)
        return y1 + t * (y2 - y1)
    }

    fun getSlopeDirection(): Float {
        return when {
            y2 > y1 + 2f -> 1f   // Desce para a direita
            y1 > y2 + 2f -> -1f  // Desce para a esquerda
            else -> 0f
        }
    }
}

data class Ladder(
    val x: Float,
    val topY: Float,
    val bottomY: Float,
    val isBroken: Boolean = false // Escadas interrompidas (apenas decorativas no meio, exigem rota ou pulo)
) {
    val width = 38f
    fun getBounds() = RectF(x - width / 2, topY, x + width / 2, bottomY)
}

enum class ObstacleType { ROLLING_BARREL, BOUNCING_ROCK, FIRE_SPIRIT }

data class BarrelObstacle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float = 0f,
    val type: ObstacleType = ObstacleType.ROLLING_BARREL,
    var radius: Float = 16f,
    var rotation: Float = 0f,
    var scoredJumpOver: Boolean = false,
    var active: Boolean = true
) {
    fun getBounds() = RectF(x - radius + 3f, y - radius + 3f, x + radius - 3f, y + radius - 3f)
}

enum class EnemyType { CRAWLER, PATROLLER, FLYER }

data class Enemy(
    var x: Float,
    var y: Float,
    val minX: Float,
    val maxX: Float,
    var speed: Float,
    val type: EnemyType,
    var alive: Boolean = true
) {
    private val baseY = y
    private var phase = 0f
    var dir = 1f

    fun update(dt: Float, playerX: Float, playerY: Float) {
        if (!alive) return
        phase += dt * 4f

        // Perseguição leve para patrulheiros quando o jogador está na mesma plataforma
        val currentSpeed = if (type == EnemyType.PATROLLER && abs(playerY - y) < 40f && abs(playerX - x) < 220f) {
            dir = if (playerX > x) 1f else -1f
            speed * 1.35f
        } else {
            speed
        }

        x += currentSpeed * dir * dt
        if (x > maxX) { x = maxX; dir = -1f }
        if (x < minX) { x = minX; dir = 1f }

        if (type == EnemyType.FLYER) {
            y = baseY + sin(phase) * 26f
        }
    }

    fun getBounds() = RectF(x - 18f, y - 32f, x + 18f, y)
}

enum class ItemType { COIN, SECRET_ARTIFACT, HAMMER_POWER, CHECKPOINT_FLAG }

data class Collectible(
    val x: Float,
    val y: Float,
    val type: ItemType,
    var collected: Boolean = false
) {
    fun getBounds() = RectF(x - 18f, y - 36f, x + 18f, y)
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Int,
    val size: Float
)

enum class PlayerAnimState { IDLE, RUN, JUMP, FALL, CLIMB, DAMAGE, VICTORY }

class Player(var x: Float, var y: Float) {
    var vx = 0f
    var vy = 0f
    val width = 30f
    val height = 46f
    var onGround = false
    var onLadder = false
    var facingRight = true
    var invulnerableTimer = 0f
    var hammerTimer = 0f
    var animTimer = 0f
    var state = PlayerAnimState.IDLE

    fun getBounds() = RectF(x - width / 2 + 4f, y - height + 4f, x + width / 2 - 4f, y)
}

class BossGorilla(
    var x: Float,
    var y: Float,
    var spawnInterval: Float,
    var maxSwitches: Int
) {
    var timer = 1.2f
    var animState = 0 // 0 = Idle/Taunt, 1 = Throw, 2 = Angry/Hit, 3 = Defeated
    var animClock = 0f
    var switchesActivated = 0
}
