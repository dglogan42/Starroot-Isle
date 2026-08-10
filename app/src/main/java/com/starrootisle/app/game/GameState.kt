package com.starrootisle.app.game

import com.starrootisle.app.audio.SoundManager
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

class GameState(seed: Long = System.currentTimeMillis()) {
    val world = World(seed = seed)
    val player = Player.starter(world, guest = false)
    var player2: Player? = null
    var coopEnabled: Boolean = false
    val quests = QuestLog()
    /** Remote online visitors (visit co-op). */
    val remotePeers = mutableListOf<com.starrootisle.app.net.RemotePeer>()
    var onlineRoom: String? = null
    var statusMessage: String =
        "Starroot Isle — a soft voice: tend the soil. Open Story for the Heartseed tale."
    var statusTimer: Float = 4.5f
    var timeOfDay: Float = 0.25f
    var lastLevelUp: String? = null
    private val rng = Random(seed xor 0x5EEDL)
    private var biomeCheckTimer = 0f

    @Transient
    var sound: SoundManager? = null

    @Transient
    var onWorldPatch: ((com.starrootisle.app.net.TilePatch) -> Unit)? = null

    @Transient
    var onDayTickLocal: (() -> Unit)? = null

    val cutscenes = CutscenePlayer()

    @Volatile
    private var applyingRemote = false

    fun enableCoop() {
        if (player2 == null) {
            player2 = Player.starter(world, guest = true)
            // Guest starts with lighter bag; shared coins via host display
            player2!!.coins = 0
        }
        coopEnabled = true
        setStatus("Co-op on! P2: right stick + right ACT.")
        sound?.play(SoundManager.Sfx.UI)
    }

    fun disableCoop() {
        coopEnabled = false
        setStatus("Solo mode.")
    }

    fun setStatus(msg: String, seconds: Float = 2.5f) {
        statusMessage = msg
        statusTimer = seconds
    }

    fun update(dt: Float, p1mx: Float, p1my: Float, p2mx: Float = 0f, p2my: Float = 0f) {
        player.tryMove(p1mx, p1my, dt, world)
        if (coopEnabled) player2?.tryMove(p2mx, p2my, dt, world)
        timeOfDay = (timeOfDay + dt / 180f) % 1f
        if (statusTimer > 0f) statusTimer -= dt
        val targets = buildList {
            add(player.x to player.y)
            if (coopEnabled) player2?.let { add(it.x to it.y) }
            remotePeers.forEach { add(it.x to it.y) }
        }
        for (p in world.puffkins) p.update(dt, targets, world)

        biomeCheckTimer += dt
        if (biomeCheckTimer > 1.2f) {
            biomeCheckTimer = 0f
            when (world.biomeAt(player.x, player.y)) {
                Biome.DEEPWOOD -> noteQuest(QuestFlag.VISITED_DEEPWOOD)
                Biome.CRYSTAL -> noteQuest(QuestFlag.VISITED_CRYSTAL)
                Biome.EMBER -> noteQuest(QuestFlag.VISITED_EMBER)
                else -> {}
            }
        }
    }

    private fun noteQuest(flag: QuestFlag, amount: Int = 1) {
        val before = quests.completed.toSet()
        quests.flag(flag, amount)
        val newly = quests.completed - before
        newly.firstOrNull()?.let { id ->
            val def = QuestLog.ALL.find { it.id == id }
            if (def != null) {
                setStatus("Story: ${def.title} complete — claim in Story!", 3.5f)
                sound?.play(SoundManager.Sfx.LEVEL)
                Cutscenes.forQuest(id)?.let { cutscenes.enqueue(it) }
            }
        }
    }

    fun claimAllReadyQuests(): Int {
        var n = 0
        for (q in quests.readyToClaim().toList()) {
            if (quests.claim(q, player)) {
                n++
                setStatus("Claimed “${q.title}” (+◎${q.rewardCoins})", 3f)
                sound?.play(SoundManager.Sfx.HARVEST)
                Cutscenes.forClaim(q.id)?.let { cutscenes.enqueue(it) }
            }
        }
        return n
    }

    fun performAction(forGuest: Boolean = false): Boolean {
        val actor = if (forGuest) player2 ?: return false else player
        if (actor.energy <= 0) {
            setStatus("Too tired. Sleep at the tent.")
            sound?.play(SoundManager.Sfx.ERROR)
            return false
        }
        val (fx, fy) = actor.facingTile()
        val tx = floor(fx).toInt()
        val ty = floor(fy).toInt()
        val tile = world.get(tx, ty)

        val ok = when (actor.tool) {
            Tool.TREATS -> feedPuffkin(actor)
            Tool.HAND -> useHand(actor, tile, tx, ty)
            Tool.HOE -> useHoe(actor, tile, tx, ty)
            Tool.WATERING_CAN -> useWater(actor, tile, tx, ty)
            Tool.SEEDS -> plant(actor, tile, tx, ty)
            Tool.PICKAXE -> mine(actor, tile, tx, ty)
            Tool.AXE -> chop(actor, tile, tx, ty)
            Tool.FISHING_ROD -> fish(actor, tile)
        }
        return ok
    }

    fun emitTile(tx: Int, ty: Int) {
        if (applyingRemote) return
        val t = world.get(tx, ty) ?: return
        onWorldPatch?.invoke(com.starrootisle.app.net.TilePatch.from(tx, ty, t))
    }

    fun applyRemotePatch(patch: com.starrootisle.app.net.TilePatch) {
        applyingRemote = true
        try {
            patch.applyTo(world)
        } finally {
            applyingRemote = false
        }
    }

    fun applyRemotePatches(patches: List<com.starrootisle.app.net.TilePatch>) {
        applyingRemote = true
        try {
            for (p in patches) p.applyTo(world)
        } finally {
            applyingRemote = false
        }
    }

    /** Collect current non-default-ish tiles for host resync (tilled/crops/mined). */
    fun collectSyncPatches(limit: Int = 400): List<com.starrootisle.app.net.TilePatch> {
        val out = mutableListOf<com.starrootisle.app.net.TilePatch>()
        for (y in 0 until world.height) {
            for (x in 0 until world.width) {
                val t = world.tiles[y][x]
                val interesting = t.crop != null ||
                    t.type == TileType.SOIL || t.type == TileType.STONE ||
                    t.type == TileType.PATH || t.type == TileType.DIRT ||
                    (t.type == TileType.ROCK && t.rockHp < 5) ||
                    t.type == TileType.CRYSTAL_FLOOR || t.type == TileType.ASH
                if (interesting) {
                    out.add(com.starrootisle.app.net.TilePatch.from(x, y, t))
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }

    fun applyRemoteDayTick() {
        applyingRemote = true
        try {
            world.growCrops()
            setStatus("Shared daybreak — crops grew on the isle.", 3f)
        } finally {
            applyingRemote = false
        }
    }

    private fun gainXp(actor: Player, prof: Profession, amount: Int) {
        if (actor.professions.addXp(prof, amount)) {
            lastLevelUp = "${prof.emoji} ${prof.displayName} → Lv${actor.professions.level(prof)}!"
            setStatus(lastLevelUp!!, 3.5f)
            sound?.play(SoundManager.Sfx.LEVEL)
        }
    }

    private fun useHoe(actor: Player, tile: Tile?, tx: Int, ty: Int): Boolean {
        if (tile == null) return false
        val tillable = tile.type == TileType.GRASS || tile.type == TileType.DIRT ||
            tile.type == TileType.PATH || tile.type == TileType.ASH ||
            tile.type == TileType.CRYSTAL_FLOOR
        if (!tillable) {
            setStatus("Can't till here.")
            sound?.play(SoundManager.Sfx.ERROR)
            return false
        }
        if (!actor.spendEnergy(3, Profession.FARMING)) {
            setStatus("Not enough energy.")
            sound?.play(SoundManager.Sfx.ERROR)
            return false
        }
        tile.type = TileType.SOIL
        tile.crop = null
        gainXp(actor, Profession.FARMING, 3)
        if (!actor.isGuest) noteQuest(QuestFlag.TILLED)
        emitTile(tx, ty)
        setStatus("Soil ready.")
        sound?.play(SoundManager.Sfx.HOE)
        return true
    }

    private fun useWater(actor: Player, tile: Tile?, tx: Int, ty: Int): Boolean {
        val crop = tile?.crop
        if (crop == null) {
            setStatus("Nothing to water.")
            return false
        }
        if (crop.watered) {
            setStatus("Already watered today.")
            return false
        }
        if (!actor.spendEnergy(2, Profession.FARMING)) {
            setStatus("Not enough energy.")
            return false
        }
        crop.watered = true
        gainXp(actor, Profession.FARMING, 2)
        emitTile(tx, ty)
        setStatus("Watered ${crop.kind.displayName}.")
        sound?.play(SoundManager.Sfx.WATER)
        return true
    }

    private fun plant(actor: Player, tile: Tile?, tx: Int, ty: Int): Boolean {
        if (tile == null || tile.type != TileType.SOIL) {
            setStatus("Till the ground first.")
            return false
        }
        if (tile.crop != null) {
            setStatus("Something already grows here.")
            return false
        }
        val kind = actor.seedSelection
        if (!actor.take(kind.seedItem, 1)) {
            setStatus("No ${Items.name(kind.seedItem)}.")
            sound?.play(SoundManager.Sfx.ERROR)
            return false
        }
        if (!actor.spendEnergy(1, Profession.FARMING)) {
            actor.addItem(kind.seedItem, 1)
            setStatus("Not enough energy.")
            return false
        }
        tile.crop = Crop(kind)
        val tip = if (kind.preferredBiome != null && tile.biome == kind.preferredBiome)
            " Perfect ${tile.biome.displayName} soil!"
        else if (kind.preferredBiome != null)
            " (Grows best in ${kind.preferredBiome.displayName}.)"
        else ""
        gainXp(actor, Profession.FARMING, 3)
        if (!actor.isGuest) noteQuest(QuestFlag.PLANTED)
        emitTile(tx, ty)
        setStatus("Planted ${kind.displayName}.$tip")
        sound?.play(SoundManager.Sfx.PLANT)
        return true
    }

    private fun mine(actor: Player, tile: Tile?, tx: Int, ty: Int): Boolean {
        if (tile == null || !tile.isMineable()) {
            setStatus("Swing pickaxe at rocks.")
            return false
        }
        if (!actor.spendEnergy(4, Profession.MINING)) {
            setStatus("Not enough energy.")
            sound?.play(SoundManager.Sfx.ERROR)
            return false
        }
        val dmg = 1 + actor.professions.powerBonus(Profession.MINING)
        tile.rockHp -= dmg
        sound?.play(SoundManager.Sfx.MINE)
        if (tile.rockHp <= 0) {
            when (tile.type) {
                TileType.CRYSTAL_ROCK -> {
                    tile.type = TileType.CRYSTAL_FLOOR
                    actor.addItem(ItemId.CRYSTAL_SHARD, 1 + rng.nextInt(2))
                    if (rng.nextFloat() < 0.25f + actor.professions.bonusYieldChance(Profession.MINING)) {
                        actor.addItem(ItemId.STAR_ORE, 1)
                        if (!actor.isGuest) noteQuest(QuestFlag.GOT_STAR_ORE)
                        setStatus("Crystal + Star Ore!")
                    } else setStatus("Crystal shard!")
                }
                TileType.EMBER_ROCK -> {
                    tile.type = TileType.ASH
                    actor.addItem(ItemId.EMBER_COAL, 1 + rng.nextInt(2))
                    actor.addItem(ItemId.STONE_ORE, 1)
                    setStatus("Ember coal!")
                }
                else -> {
                    tile.type = TileType.STONE
                    actor.addItem(ItemId.STONE_ORE, 1 + rng.nextInt(2))
                    if (rng.nextFloat() < 0.15f + actor.professions.bonusYieldChance(Profession.MINING)) {
                        actor.addItem(ItemId.STAR_ORE, 1)
                        if (!actor.isGuest) noteQuest(QuestFlag.GOT_STAR_ORE)
                        setStatus("Star Ore!")
                    } else setStatus("Stone ore.")
                }
            }
            gainXp(actor, Profession.MINING, 10)
        } else {
            setStatus("Crack… (${tile.rockHp} left)")
            gainXp(actor, Profession.MINING, 3)
        }
        if (!actor.isGuest) noteQuest(QuestFlag.MINED)
        emitTile(tx, ty)
        return true
    }

    private fun chop(actor: Player, tile: Tile?, tx: Int, ty: Int): Boolean {
        if (tile == null || !tile.isChoppable()) {
            setStatus("Chop trees with the axe.")
            return false
        }
        if (!actor.spendEnergy(3, Profession.FORAGING)) {
            setStatus("Not enough energy.")
            return false
        }
        val dmg = 1 + actor.professions.powerBonus(Profession.FORAGING)
        tile.treeHp -= dmg
        sound?.play(SoundManager.Sfx.CHOP)
        if (tile.treeHp <= 0) {
            val deep = tile.type == TileType.DEEPWOOD_TREE
            tile.type = when (tile.biome) {
                Biome.CRYSTAL -> TileType.CRYSTAL_FLOOR
                Biome.EMBER -> TileType.ASH
                else -> TileType.GRASS
            }
            if (deep) {
                actor.addItem(ItemId.DEEPWOOD_LOG, 1 + rng.nextInt(2))
                actor.addItem(ItemId.WOOD, 1)
                actor.addItem(ItemId.FIBER, 1 + rng.nextInt(2))
                setStatus("Deepwood log!")
            } else {
                actor.addItem(ItemId.WOOD, 2 + rng.nextInt(2))
                if (rng.nextFloat() < 0.4f) actor.addItem(ItemId.FIBER, 1)
                setStatus("Wood gathered.")
            }
            gainXp(actor, Profession.FORAGING, 8)
        } else {
            setStatus("Chop… (${tile.treeHp} left)")
            gainXp(actor, Profession.FORAGING, 2)
        }
        if (!actor.isGuest) noteQuest(QuestFlag.CHOPPED)
        emitTile(tx, ty)
        return true
    }

    private fun fish(actor: Player, tile: Tile?): Boolean {
        if (!actor.has(ItemId.FISHING_ROD) && !actor.hasFishingRod) {
            setStatus("Craft a Fishing Rod first.")
            sound?.play(SoundManager.Sfx.ERROR)
            return false
        }
        actor.hasFishingRod = true
        val nearSpot = tile?.type == TileType.FISHING_SPOT ||
            listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).any { (dx, dy) ->
                val (fx, fy) = actor.facingTile()
                world.get(floor(fx).toInt() + dx, floor(fy).toInt() + dy)?.type == TileType.FISHING_SPOT
            } || world.get(floor(actor.x).toInt(), floor(actor.y).toInt())?.type == TileType.FISHING_SPOT

        // Also allow fishing if adjacent to fishing spot
        val ax = floor(actor.x).toInt()
        val ay = floor(actor.y).toInt()
        val adjacent = (-1..1).any { dy ->
            (-1..1).any { dx ->
                world.get(ax + dx, ay + dy)?.type == TileType.FISHING_SPOT
            }
        }
        if (!nearSpot && !adjacent && tile?.type != TileType.FISHING_SPOT) {
            setStatus("Find a glowing fishing spot on the shore.")
            return false
        }
        if (!actor.spendEnergy(5, Profession.FISHING)) {
            setStatus("Not enough energy.")
            return false
        }
        sound?.play(SoundManager.Sfx.FISH)
        val chance = 0.45f + actor.professions.level(Profession.FISHING) * 0.08f
        if (rng.nextFloat() < chance) {
            actor.addItem(ItemId.SILVERFIN, 1)
            if (!actor.isGuest) noteQuest(QuestFlag.FISHED)
            if (rng.nextFloat() < actor.professions.bonusYieldChance(Profession.FISHING)) {
                actor.addItem(ItemId.SILVERFIN, 1)
                setStatus("Double Silverfin!")
            } else setStatus("Caught a Silverfin!")
            gainXp(actor, Profession.FISHING, 12)
        } else {
            setStatus("The line went slack…")
            gainXp(actor, Profession.FISHING, 3)
        }
        return true
    }

    private fun useHand(actor: Player, tile: Tile?, tx: Int, ty: Int): Boolean {
        if (tile?.crop?.ready == true) {
            val c = tile.crop!!
            if (!actor.spendEnergy(1, Profession.FARMING)) {
                setStatus("Not enough energy.")
                return false
            }
            var count = c.kind.harvestCount
            if (c.kind.preferredBiome == tile.biome) count++
            if (rng.nextFloat() < actor.professions.bonusYieldChance(Profession.FARMING)) count++
            actor.addItem(c.kind.harvestItem, count)
            if (rng.nextFloat() < 0.4f) actor.addItem(c.kind.seedItem, 1)
            tile.crop = null
            tile.type = TileType.SOIL
            gainXp(actor, Profession.FARMING, 8)
            if (!actor.isGuest) noteQuest(QuestFlag.HARVESTED)
            emitTile(tx, ty)
            setStatus("Harvested ${c.kind.displayName} ×$count!")
            sound?.play(SoundManager.Sfx.HARVEST)
            return true
        }
        if (tile?.type == TileType.STARROOT) {
            if (!actor.spendEnergy(2, Profession.FORAGING)) {
                setStatus("Not enough energy.")
                return false
            }
            if (rng.nextFloat() < 0.55f + actor.professions.bonusYieldChance(Profession.FORAGING)) {
                actor.addItem(ItemId.STARFRUIT, 1)
                setStatus("Starfruit!")
            } else {
                actor.addItem(ItemId.FIBER, 1)
                setStatus("Soft fibers.")
            }
            gainXp(actor, Profession.FORAGING, 6)
            sound?.play(SoundManager.Sfx.HARVEST)
            return true
        }
        val foods = listOf(
            ItemId.PEPPER_STEW, ItemId.CRYSTAL_SALAD, ItemId.GRILLED_ROOT,
            ItemId.STARFRUIT, ItemId.SILVERFIN, ItemId.ROOTBERRY,
            ItemId.EMBER_PEPPER, ItemId.CRYSTAL_LETTUCE, ItemId.GLOWBEAN, ItemId.MOONWHEAT
        )
        for (f in foods) {
            val def = Items.defs[f] ?: continue
            if (def.energyRestore > 0 && actor.has(f)) {
                actor.take(f, 1)
                actor.restoreEnergy(def.energyRestore)
                setStatus("Ate ${def.name} (+${def.energyRestore}).")
                sound?.play(SoundManager.Sfx.UI)
                return true
            }
        }
        val biome = world.biomeAt(actor.x, actor.y)
        setStatus("Hands free · ${biome.displayName}")
        return false
    }

    private fun feedPuffkin(actor: Player): Boolean {
        val p = world.nearestPuffkin(actor.x, actor.y) ?: run {
            setStatus("No Puffkin nearby.")
            return false
        }
        if (p.befriended) {
            setStatus("Already bonded. It follows ${if (p.followTarget == 0) "P1" else "P2"}.")
            return false
        }
        if (!actor.has(ItemId.PUFFKIN_TREAT)) {
            setStatus("Craft Puffkin Treats at the workbench.")
            return false
        }
        if (!actor.spendEnergy(2, Profession.RANCHING)) {
            setStatus("Not enough energy.")
            return false
        }
        actor.take(ItemId.PUFFKIN_TREAT, 1)
        val bonded = p.tryFeed()
        if (bonded) {
            p.followTarget = if (actor.isGuest) 1 else 0
            actor.coins += 15
            gainXp(actor, Profession.RANCHING, 15)
            if (!actor.isGuest) noteQuest(QuestFlag.BONDED)
            setStatus("Puffkin bonded! +15 ◎")
            sound?.play(SoundManager.Sfx.BOND)
        } else {
            gainXp(actor, Profession.RANCHING, 5)
            setStatus("Nibble… trust ${(p.trust * 100).toInt()}%")
            sound?.play(SoundManager.Sfx.UI)
        }
        return true
    }

    fun trySleep(forGuest: Boolean = false): Boolean {
        val actor = if (forGuest) player2 ?: return false else player
        val tent = findTile(TileType.TENT) ?: run {
            setStatus("No tent found.")
            return false
        }
        val d = hypot(tent.first + 0.5f - actor.x, tent.second + 0.5f - actor.y)
        if (d > 2.2f) {
            setStatus("Sleep at your tent.")
            return false
        }
        // Host sleep advances day for everyone
        if (!forGuest) {
            world.growCrops()
            player.sleep()
            player2?.sleep()
            timeOfDay = 0.22f
            noteQuest(QuestFlag.SLEPT)
            setStatus("Day ${player.day}. Crops grew. Energy full!", 3.5f)
            onDayTickLocal?.invoke()
            // sync crop growth tiles
            for (y in 0 until world.height) {
                for (x in 0 until world.width) {
                    if (world.tiles[y][x].crop != null) emitTile(x, y)
                }
            }
        } else {
            actor.energy = actor.maxEnergy
            setStatus("P2 rested.")
        }
        sound?.play(SoundManager.Sfx.SLEEP)
        return true
    }

    fun tryCraft(recipe: Recipe, forGuest: Boolean = false): Boolean {
        val actor = if (forGuest) player2 ?: return false else player
        val bench = findTile(TileType.WORKBENCH)
        if (bench != null) {
            val d = hypot(bench.first + 0.5f - actor.x, bench.second + 0.5f - actor.y)
            if (d > 2.5f) {
                setStatus("Stand near the workbench.")
                return false
            }
        }
        if (actor.professions.level(Profession.CRAFTING) < recipe.minCraftLevel) {
            setStatus("Need Crafting Lv${recipe.minCraftLevel}.")
            sound?.play(SoundManager.Sfx.ERROR)
            return false
        }
        for ((id, count) in recipe.costs) {
            if (!actor.has(id, count)) {
                setStatus("Need ${Items.name(id)} ×$count.")
                return false
            }
        }
        for ((id, count) in recipe.costs) actor.take(id, count)
        actor.addItem(recipe.result, recipe.resultCount)
        if (recipe.result == ItemId.FISHING_ROD) {
            actor.hasFishingRod = true
            if (!actor.isGuest) noteQuest(QuestFlag.CRAFTED_ROD)
        }
        gainXp(actor, Profession.CRAFTING, recipe.xp)
        if (!actor.isGuest) noteQuest(QuestFlag.CRAFTED_ANY)
        setStatus("Crafted ${recipe.name}!")
        sound?.play(SoundManager.Sfx.CRAFT)
        return true
    }

    fun sellAllCrops(forGuest: Boolean = false): Int {
        val actor = if (forGuest) player2 ?: return 0 else player
        var earned = 0
        val sellable = listOf(
            ItemId.GLOWBEAN, ItemId.MOONWHEAT, ItemId.ROOTBERRY,
            ItemId.EMBER_PEPPER, ItemId.CRYSTAL_LETTUCE, ItemId.STARFRUIT,
            ItemId.STONE_ORE, ItemId.STAR_ORE, ItemId.CRYSTAL_SHARD, ItemId.EMBER_COAL,
            ItemId.WOOD, ItemId.DEEPWOOD_LOG, ItemId.FIBER, ItemId.SILVERFIN
        )
        for (id in sellable) {
            val n = actor.inventory[id] ?: 0
            if (n <= 0) continue
            val price = Items.defs[id]?.sellPrice ?: 0
            earned += n * price
            actor.inventory.remove(id)
        }
        actor.coins += earned
        if (earned > 0) setStatus("Sold for ◎ $earned.")
        else setStatus("Nothing to sell.")
        return earned
    }

    private fun findTile(type: TileType): Pair<Int, Int>? {
        for (y in 0 until world.height) {
            for (x in 0 until world.width) {
                if (world.tiles[y][x].type == type) return x to y
            }
        }
        return null
    }

    fun inventoryLines(forGuest: Boolean = false): List<String> {
        val actor = if (forGuest) player2 ?: return listOf("(no P2)") else player
        if (actor.inventory.isEmpty()) return listOf("(empty)")
        return actor.inventory.entries
            .sortedBy { Items.name(it.key) }
            .map { "${Items.name(it.key)} ×${it.value}" }
    }
}
