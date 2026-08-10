package com.starrootisle.app.game

enum class QuestId {
    WAKE_ASHOR,
    FIRST_TILL,
    FIRST_HARVEST,
    WOOD_FOR_FIRE,
    STONE_FOUNDATION,
    MEET_PUFFKIN,
    SHORE_CATCH,
    DEEPWOOD_VISIT,
    CRYSTAL_VISIT,
    EMBER_VISIT,
    CRAFT_ROD,
    STAR_ORE_HUNT,
    FARM_MASTER,
    ISLAND_FRIEND,
    STARROOT_HEART,
}

enum class QuestFlag {
    TILLED, PLANTED, HARVESTED, CHOPPED, MINED, BONDED, FISHED,
    CRAFTED_ROD, GOT_STAR_ORE, VISITED_DEEPWOOD, VISITED_CRYSTAL, VISITED_EMBER,
    SLEPT, CRAFTED_ANY,
}

data class QuestDef(
    val id: QuestId,
    val chapter: Int,
    val title: String,
    val story: String,
    val objective: String,
    val target: Int = 1,
    val requires: List<QuestId> = emptyList(),
    val rewardCoins: Int = 10,
    val rewardItems: Map<ItemId, Int> = emptyMap(),
    val check: (QuestLog) -> Int, // current progress
)

/**
 * Original short story arc for Starroot Isle.
 * "The Heartseed" — restore the island's gentle pulse.
 */
class QuestLog {
    val progress = mutableMapOf<QuestId, Int>()
    val completed = mutableSetOf<QuestId>()
    val claimed = mutableSetOf<QuestId>()
    val flags = mutableMapOf<QuestFlag, Int>()
    var storyBeat: String = "You wash ashore under a dim starroot glow…"
    var chapterUnlocked: Int = 1

    fun flag(f: QuestFlag, amount: Int = 1) {
        flags[f] = (flags[f] ?: 0) + amount
        recompute()
    }

    fun setFlagAtLeast(f: QuestFlag, value: Int) {
        flags[f] = maxOf(flags[f] ?: 0, value)
        recompute()
    }

    fun count(f: QuestFlag): Int = flags[f] ?: 0

    fun recompute() {
        for (q in ALL) {
            if (q.id in completed) continue
            if (!prereqsMet(q)) continue
            val p = q.check(this).coerceIn(0, q.target)
            progress[q.id] = p
            if (p >= q.target) {
                completed.add(q.id)
                storyBeat = q.story
                chapterUnlocked = maxOf(chapterUnlocked, q.chapter + 1)
            }
        }
    }

    fun prereqsMet(q: QuestDef): Boolean =
        q.requires.all { it in completed }

    fun isActive(q: QuestDef): Boolean =
        q.id !in completed && prereqsMet(q)

    fun claim(q: QuestDef, player: Player): Boolean {
        if (q.id !in completed || q.id in claimed) return false
        claimed.add(q.id)
        player.coins += q.rewardCoins
        for ((id, n) in q.rewardItems) player.addItem(id, n)
        return true
    }

    fun activeList(): List<Pair<QuestDef, Int>> =
        ALL.filter { isActive(it) }.map { it to (progress[it.id] ?: 0) }

    fun readyToClaim(): List<QuestDef> =
        ALL.filter { it.id in completed && it.id !in claimed }

    fun journalLines(): List<String> {
        val lines = mutableListOf<String>()
        lines += "✦ ${storyBeat}"
        lines += ""
        val ready = readyToClaim()
        if (ready.isNotEmpty()) {
            lines += "── Ready to claim ──"
            ready.forEach { lines += "★ ${it.title}  (+◎${it.rewardCoins})" }
            lines += ""
        }
        lines += "── Active ──"
        val active = activeList()
        if (active.isEmpty()) lines += "(Explore — new threads unlock as you play.)"
        else active.forEach { (q, p) ->
            lines += "• ${q.title}  ($p/${q.target})"
            lines += "  ${q.objective}"
        }
        lines += ""
        lines += "── Done ${completed.size}/${ALL.size} ──"
        ALL.filter { it.id in completed }.forEach {
            val tag = if (it.id in claimed) "✓" else "★ claim"
            lines += "$tag Ch${it.chapter}: ${it.title}"
        }
        return lines
    }

    companion object {
        val ALL: List<QuestDef> = listOf(
            QuestDef(
                id = QuestId.WAKE_ASHOR,
                chapter = 1,
                title = "Washed Ashore",
                story = "A soft voice in the wind: \"Tend the soil. The Heartseed waits.\"",
                objective = "Sleep once at your tent to settle in",
                rewardCoins = 15,
                rewardItems = mapOf(ItemId.GLOWBEAN_SEED to 4),
                check = { it.count(QuestFlag.SLEPT) },
            ),
            QuestDef(
                id = QuestId.FIRST_TILL,
                chapter = 1,
                title = "Broken Ground",
                story = "Dark soil remembers rain. Something under the meadow stirs.",
                objective = "Till soil 3 times",
                target = 3,
                requires = listOf(QuestId.WAKE_ASHOR),
                rewardCoins = 10,
                check = { it.count(QuestFlag.TILLED) },
            ),
            QuestDef(
                id = QuestId.FIRST_HARVEST,
                chapter = 1,
                title = "First Light Crop",
                story = "Your harvest glows faintly — the island notices kindness.",
                objective = "Harvest any crop",
                requires = listOf(QuestId.FIRST_TILL),
                rewardCoins = 20,
                rewardItems = mapOf(ItemId.PUFFKIN_TREAT to 2),
                check = { it.count(QuestFlag.HARVESTED) },
            ),
            QuestDef(
                id = QuestId.WOOD_FOR_FIRE,
                chapter = 1,
                title = "Kindling",
                story = "Smoke curls toward the Deepwood. Paths open for the brave.",
                objective = "Chop trees 4 times (fell or chip)",
                target = 4,
                requires = listOf(QuestId.WAKE_ASHOR),
                rewardCoins = 12,
                rewardItems = mapOf(ItemId.WOOD to 3),
                check = { it.count(QuestFlag.CHOPPED) },
            ),
            QuestDef(
                id = QuestId.STONE_FOUNDATION,
                chapter = 1,
                title = "Foundation Stone",
                story = "Ore flecks mirror the starroot crown. Dig deeper later.",
                objective = "Mine rocks 4 times",
                target = 4,
                requires = listOf(QuestId.WAKE_ASHOR),
                rewardCoins = 12,
                rewardItems = mapOf(ItemId.STONE_ORE to 2),
                check = { it.count(QuestFlag.MINED) },
            ),
            QuestDef(
                id = QuestId.MEET_PUFFKIN,
                chapter = 2,
                title = "Soft Footfalls",
                story = "A Puffkin chirps your name — or invents one. Bond complete.",
                objective = "Fully bond with a wild Puffkin",
                requires = listOf(QuestId.FIRST_HARVEST),
                rewardCoins = 25,
                rewardItems = mapOf(ItemId.PUFFKIN_TREAT to 3),
                check = { it.count(QuestFlag.BONDED) },
            ),
            QuestDef(
                id = QuestId.DEEPWOOD_VISIT,
                chapter = 2,
                title = "Whispering Canopy",
                story = "Deepwood logs hum like cello strings. The Heartseed’s roots run here.",
                objective = "Stand in the Deepwood biome",
                requires = listOf(QuestId.WOOD_FOR_FIRE),
                rewardCoins = 15,
                rewardItems = mapOf(ItemId.ROOTBERRY_SEED to 3),
                check = { it.count(QuestFlag.VISITED_DEEPWOOD) },
            ),
            QuestDef(
                id = QuestId.CRYSTAL_VISIT,
                chapter = 2,
                title = "Hollow Lights",
                story = "Crystal Hollows scatter pale fireflies. A shard points east of home.",
                objective = "Stand in Crystal Hollows",
                requires = listOf(QuestId.STONE_FOUNDATION),
                rewardCoins = 15,
                rewardItems = mapOf(ItemId.CRYSTAL_LETTUCE_SEED to 2),
                check = { it.count(QuestFlag.VISITED_CRYSTAL) },
            ),
            QuestDef(
                id = QuestId.EMBER_VISIT,
                chapter = 2,
                title = "Warm Tide",
                story = "Ember Shore smells of pepper and salt. Something old cools in the ash.",
                objective = "Stand on Ember Shore",
                requires = listOf(QuestId.FIRST_HARVEST),
                rewardCoins = 15,
                rewardItems = mapOf(ItemId.EMBER_PEPPER_SEED to 2),
                check = { it.count(QuestFlag.VISITED_EMBER) },
            ),
            QuestDef(
                id = QuestId.CRAFT_ROD,
                chapter = 2,
                title = "Line & Light",
                story = "Silverfins carry messages between shores. Listen when you cast.",
                objective = "Craft a Fishing Rod",
                requires = listOf(QuestId.WOOD_FOR_FIRE),
                rewardCoins = 20,
                check = { it.count(QuestFlag.CRAFTED_ROD) },
            ),
            QuestDef(
                id = QuestId.SHORE_CATCH,
                chapter = 2,
                title = "Silver Letter",
                story = "In the fish’s eye: a flicker of the Heartseed’s map.",
                objective = "Catch a Silverfin",
                requires = listOf(QuestId.CRAFT_ROD),
                rewardCoins = 25,
                rewardItems = mapOf(ItemId.SILVERFIN to 1),
                check = { it.count(QuestFlag.FISHED) },
            ),
            QuestDef(
                id = QuestId.STAR_ORE_HUNT,
                chapter = 3,
                title = "Sky Metal",
                story = "Star Ore sings when held. Three notes — meadow, hollow, shore.",
                objective = "Obtain Star Ore",
                requires = listOf(QuestId.CRYSTAL_VISIT, QuestId.STONE_FOUNDATION),
                rewardCoins = 40,
                rewardItems = mapOf(ItemId.STARFRUIT to 2),
                check = { it.count(QuestFlag.GOT_STAR_ORE) },
            ),
            QuestDef(
                id = QuestId.FARM_MASTER,
                chapter = 3,
                title = "Green Pulse",
                story = "Rows of light weave a lattice under the island.",
                objective = "Harvest crops 8 times",
                target = 8,
                requires = listOf(QuestId.FIRST_HARVEST),
                rewardCoins = 35,
                rewardItems = mapOf(ItemId.GRILLED_ROOT to 2),
                check = { it.count(QuestFlag.HARVESTED) },
            ),
            QuestDef(
                id = QuestId.ISLAND_FRIEND,
                chapter = 3,
                title = "Herd of Soft Stars",
                story = "Puffkins circle the tent at dusk. You are no longer alone.",
                objective = "Bond with 3 Puffkins",
                target = 3,
                requires = listOf(QuestId.MEET_PUFFKIN),
                rewardCoins = 50,
                rewardItems = mapOf(ItemId.PUFFKIN_TREAT to 5),
                check = { it.count(QuestFlag.BONDED) },
            ),
            QuestDef(
                id = QuestId.STARROOT_HEART,
                chapter = 4,
                title = "Heartseed Awakens",
                story = "The starroot crown brightens. Starroot Isle breathes with you. The story rests — for now.",
                objective = "Finish farm, friends, and sky metal threads",
                requires = listOf(
                    QuestId.FARM_MASTER,
                    QuestId.ISLAND_FRIEND,
                    QuestId.STAR_ORE_HUNT,
                    QuestId.SHORE_CATCH,
                ),
                rewardCoins = 100,
                rewardItems = mapOf(
                    ItemId.STARFRUIT to 5,
                    ItemId.STAR_ORE to 2,
                    ItemId.PEPPER_STEW to 2,
                ),
                check = {
                    // Completes when prereqs done (target 1, auto via recompute when requires met)
                    if (listOf(
                            QuestId.FARM_MASTER,
                            QuestId.ISLAND_FRIEND,
                            QuestId.STAR_ORE_HUNT,
                            QuestId.SHORE_CATCH,
                        ).all { q -> q in it.completed }
                    ) 1 else 0
                },
            ),
        )
    }
}
