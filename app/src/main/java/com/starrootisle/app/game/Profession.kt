package com.starrootisle.app.game

enum class Profession(
    val displayName: String,
    val emoji: String,
    val description: String,
) {
    FARMING("Farming", "🌾", "Till, plant, water, harvest"),
    MINING("Mining", "⛏", "Break rocks & crystal"),
    FORAGING("Foraging", "🌿", "Chop trees & pick Starroot"),
    FISHING("Fishing", "🎣", "Cast at fishing spots"),
    CRAFTING("Crafting", "🔧", "Workbench recipes"),
    RANCHING("Ranching", "🐾", "Bond with Puffkins"),
}

data class ProfessionProgress(
    var xp: Int = 0,
    var level: Int = 1,
) {
    fun xpToNext(): Int = 20 + (level - 1) * 25

    /** Returns true if leveled up. */
    fun addXp(amount: Int): Boolean {
        if (level >= MAX_LEVEL) {
            xp = xpToNext()
            return false
        }
        xp += amount
        var leveled = false
        while (level < MAX_LEVEL && xp >= xpToNext()) {
            xp -= xpToNext()
            level++
            leveled = true
        }
        if (level >= MAX_LEVEL) xp = xpToNext()
        return leveled
    }

    companion object {
        const val MAX_LEVEL = 5
    }
}

class ProfessionBook {
    val progress = Profession.entries.associateWith { ProfessionProgress() }.toMutableMap()

    fun level(p: Profession): Int = progress[p]?.level ?: 1

    fun xp(p: Profession): Int = progress[p]?.xp ?: 0

    fun xpToNext(p: Profession): Int = progress[p]?.xpToNext() ?: 20

    /** Energy multiplier 1.0 → 0.7 at L5 */
    fun energyMult(p: Profession): Float {
        val lv = level(p)
        return (1f - (lv - 1) * 0.075f).coerceAtLeast(0.65f)
    }

    /** Yield bonus chance */
    fun bonusYieldChance(p: Profession): Float = (level(p) - 1) * 0.08f

    /** Mining/chop damage bonus */
    fun powerBonus(p: Profession): Int = (level(p) - 1) / 2

    fun addXp(p: Profession, amount: Int): Boolean =
        progress.getOrPut(p) { ProfessionProgress() }.addXp(amount)

    fun summaryLines(): List<String> =
        Profession.entries.map { prof ->
            val pr = progress[prof]!!
            val bar = if (pr.level >= ProfessionProgress.MAX_LEVEL) "MAX"
            else "${pr.xp}/${pr.xpToNext()}"
            "${prof.emoji} ${prof.displayName}  Lv${pr.level}  ($bar)"
        }
}
