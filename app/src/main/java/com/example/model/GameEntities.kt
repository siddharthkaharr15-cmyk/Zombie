package com.example.model

import java.util.UUID
import kotlin.math.sqrt

enum class ZombieType {
    WALKER, RUNNER, TANK, BOMBER, BOSS
}

data class Vector2(var x: Float, var y: Float) {
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun times(factor: Float) = Vector2(x * factor, y * factor)

    fun length(): Float = sqrt(x * x + y * y)

    fun normalized(): Vector2 {
        val len = length()
        return if (len > 0) Vector2(x / len, y / len) else Vector2(0f, 0f)
    }

    fun distanceTo(other: Vector2): Float {
        val dx = other.x - x
        val dy = other.y - y
        return sqrt(dx * dx + dy * dy)
    }
}

data class Zombie(
    val id: String = UUID.randomUUID().toString(),
    val type: ZombieType,
    var x: Float,
    var y: Float,
    var maxHp: Float,
    var hp: Float,
    var speed: Float,
    var damage: Float,
    var size: Float,
    var reward: Int,
    var colorHex: Long,
    var isExploding: Boolean = false,
    var explosionTimer: Float = 0f,
    var lastAttackTime: Long = 0L,
    val bossName: String? = null,
    var hitTimer: Float = 0f,
    var isGolden: Boolean = false
)

data class Bullet(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val speed: Float,
    val size: Float,
    val colorHex: Long,
    var piercesRemaining: Int = 1,
    val originalPierceCount: Int = 1,
    val isTurretBullet: Boolean = false,
    val isSplash: Boolean = false,
    val splashRadius: Float = 0f,
    val splashDamagePercent: Float = 0f
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val colorHex: Long,
    val size: Float,
    var life: Float,        // 1.0f decreasing to 0f
    val decay: Float
)

data class DamageNumber(
    val x: Float,
    var y: Float,
    val text: String,
    val colorHex: Long,
    var life: Float,        // 1.0f decreasing to 0f
    val isCritical: Boolean = false
)

data class Coin(
    var x: Float,
    var y: Float,
    val startX: Float,
    val startY: Float,
    var progress: Float = 0f, // 0.0f (at start) to 1.0f (at target)
    val amount: Int
)

enum class TurretType {
    NONE, GATLING, PLASMA, TESLA
}

data class Turret(
    val slotId: Int, // 0..3
    val x: Float,
    val y: Float,
    var type: TurretType,
    var level: Int = 1,
    var angle: Float = 0f,
    var lastShootTime: Long = 0L
)

