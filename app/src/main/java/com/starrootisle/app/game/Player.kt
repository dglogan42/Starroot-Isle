package com.starrootisle.app.game

import kotlin.math.hypot

class Player(
    var x: Float,
    var y: Float,
    val isGuest: Boolean = false,
) {
    var facingX = 0f
    var facingY = 1f
    var energy = 100
    var maxEnergy = 100
    var coins = 0
    var day = 1
    var tool: Tool = Tool.HOE
    val inventory = mutableMapOf<ItemId, Int>()
    var seedSelection: CropKind = CropKind.GLOWBEAN
    var moveSpeed = 3.6f
    val professions = ProfessionBook()
    var animPhase = 0f
    var hasFishingRod: Boolean = false

    fun addItem(id: ItemId, count: Int = 1) {
        if (count <= 0) return
        if (id == ItemId.FISHING_ROD) hasFishingRod = true
        inventory[id] = (inventory[id] ?: 0) + count
    }

    fun has(id: ItemId, count: Int = 1): Boolean = (inventory[id] ?: 0) >= count

    fun take(id: ItemId, count: Int = 1): Boolean {
        val cur = inventory[id] ?: 0
        if (cur < count) return false
        val left = cur - count
        if (left <= 0) inventory.remove(id) else inventory[id] = left
        return true
    }

    fun spendEnergy(amount: Int, profession: Profession? = null): Boolean {
        val mult = profession?.let { professions.energyMult(it) } ?: 1f
        val cost = (amount * mult).toInt().coerceAtLeast(1)
        if (energy < cost) return false
        energy -= cost
        return true
    }

    fun restoreEnergy(amount: Int) {
        energy = (energy + amount).coerceAtMost(maxEnergy)
    }

    fun sleep() {
        energy = maxEnergy
        day++
    }

    fun tryMove(dx: Float, dy: Float, dt: Float, world: World) {
        val len = hypot(dx, dy)
        if (len < 0.05f) return
        facingX = dx / len
        facingY = dy / len
        animPhase += dt * 8f
        val step = moveSpeed * dt
        val nx = x + facingX * step
        val ny = y + facingY * step
        if (!world.blocksAt(nx, y)) x = nx
        if (!world.blocksAt(x, ny)) y = ny
        x = x.coerceIn(0.3f, world.width - 0.3f)
        y = y.coerceIn(0.3f, world.height - 0.3f)
    }

    fun cycleTool() {
        val order = mutableListOf(
            Tool.HOE, Tool.WATERING_CAN, Tool.SEEDS, Tool.PICKAXE,
            Tool.AXE, Tool.TREATS, Tool.HAND
        )
        if (hasFishingRod || has(ItemId.FISHING_ROD)) {
            hasFishingRod = true
            order.add(5, Tool.FISHING_ROD)
        }
        val i = order.indexOf(tool).let { if (it < 0) 0 else it }
        tool = order[(i + 1) % order.size]
    }

    fun cycleSeed() {
        val kinds = CropKind.entries
        val i = kinds.indexOf(seedSelection)
        seedSelection = kinds[(i + 1) % kinds.size]
    }

    fun facingTile(): Pair<Float, Float> =
        x + facingX * 0.9f to y + facingY * 0.9f

    companion object {
        fun starter(world: World, guest: Boolean = false): Player {
            val p = Player(world.spawnX + if (guest) 0.8f else 0f, world.spawnY, isGuest = guest)
            p.addItem(ItemId.GLOWBEAN_SEED, 8)
            p.addItem(ItemId.ROOTBERRY_SEED, 6)
            p.addItem(ItemId.MOONWHEAT_SEED, 4)
            p.addItem(ItemId.EMBER_PEPPER_SEED, 2)
            p.addItem(ItemId.CRYSTAL_LETTUCE_SEED, 2)
            p.addItem(ItemId.PUFFKIN_TREAT, 3)
            p.addItem(ItemId.ROOTBERRY, 2)
            return p
        }
    }
}
