package com.starrootisle.app.game

import kotlin.math.hypot
import kotlin.random.Random

enum class PuffkinColor {
    ROSE, MINT, SUN, SKY
}

/**
 * Original companion creature for Starroot Isle.
 * Soft round body, tiny ears, star-fleck eyes — not based on any existing IP.
 */
data class Puffkin(
    val id: Int,
    var x: Float,
    var y: Float,
    val color: PuffkinColor,
    var trust: Float = 0f,
    var befriended: Boolean = false,
    var bobPhase: Float = Random.nextFloat() * 6.28f,
    var followTarget: Int = 0, // 0 = p1, 1 = p2
) {
    fun update(dt: Float, targets: List<Pair<Float, Float>>, world: World) {
        bobPhase += dt * 2.4f
        if (befriended && targets.isNotEmpty()) {
            val idx = followTarget.coerceIn(0, targets.lastIndex)
            val (px, py) = targets[idx]
            val dx = px - x
            val dy = py - y
            val dist = hypot(dx, dy)
            if (dist > 1.4f) {
                val speed = 2.8f * dt
                val nx = x + dx / dist * speed
                val ny = y + dy / dist * speed
                if (!world.blocksAt(nx, ny)) {
                    x = nx
                    y = ny
                }
            }
        } else if (!befriended) {
            if (Random.nextFloat() < 0.01f) {
                val nx = x + (Random.nextFloat() - 0.5f) * 0.8f
                val ny = y + (Random.nextFloat() - 0.5f) * 0.8f
                if (!world.blocksAt(nx, ny)) {
                    x = nx
                    y = ny
                }
            }
        }
    }

    fun tryFeed(amount: Float = 0.35f): Boolean {
        if (befriended) return false
        trust = (trust + amount).coerceAtMost(1f)
        if (trust >= 1f) {
            befriended = true
            return true
        }
        return false
    }
}
