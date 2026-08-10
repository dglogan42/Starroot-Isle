package com.starrootisle.app.game

enum class ItemId {
    HOE, WATERING_CAN, PICKAXE, AXE, FISHING_ROD,
    GLOWBEAN_SEED, MOONWHEAT_SEED, ROOTBERRY_SEED,
    EMBER_PEPPER_SEED, CRYSTAL_LETTUCE_SEED,
    GLOWBEAN, MOONWHEAT, ROOTBERRY, EMBER_PEPPER, CRYSTAL_LETTUCE,
    STARFRUIT, GRILLED_ROOT, PEPPER_STEW, CRYSTAL_SALAD,
    WOOD, DEEPWOOD_LOG, STONE_ORE, STAR_ORE, CRYSTAL_SHARD, EMBER_COAL, FIBER,
    SILVERFIN, PUFFKIN_TREAT, COIN,
}

enum class Tool {
    HOE, WATERING_CAN, PICKAXE, AXE, FISHING_ROD, SEEDS, TREATS, HAND,
}

data class ItemDef(
    val id: ItemId,
    val name: String,
    val stackable: Boolean = true,
    val energyRestore: Int = 0,
    val sellPrice: Int = 0,
)

object Items {
    val defs: Map<ItemId, ItemDef> = mapOf(
        ItemId.HOE to ItemDef(ItemId.HOE, "Hoe", stackable = false),
        ItemId.WATERING_CAN to ItemDef(ItemId.WATERING_CAN, "Watering Can", stackable = false),
        ItemId.PICKAXE to ItemDef(ItemId.PICKAXE, "Pickaxe", stackable = false),
        ItemId.AXE to ItemDef(ItemId.AXE, "Axe", stackable = false),
        ItemId.FISHING_ROD to ItemDef(ItemId.FISHING_ROD, "Fishing Rod", stackable = false),
        ItemId.GLOWBEAN_SEED to ItemDef(ItemId.GLOWBEAN_SEED, "Glowbean Seeds", sellPrice = 2),
        ItemId.MOONWHEAT_SEED to ItemDef(ItemId.MOONWHEAT_SEED, "Moonwheat Seeds", sellPrice = 3),
        ItemId.ROOTBERRY_SEED to ItemDef(ItemId.ROOTBERRY_SEED, "Rootberry Seeds", sellPrice = 1),
        ItemId.EMBER_PEPPER_SEED to ItemDef(ItemId.EMBER_PEPPER_SEED, "Ember Pepper Seeds", sellPrice = 4),
        ItemId.CRYSTAL_LETTUCE_SEED to ItemDef(ItemId.CRYSTAL_LETTUCE_SEED, "Crystal Lettuce Seeds", sellPrice = 5),
        ItemId.GLOWBEAN to ItemDef(ItemId.GLOWBEAN, "Glowbean", energyRestore = 12, sellPrice = 8),
        ItemId.MOONWHEAT to ItemDef(ItemId.MOONWHEAT, "Moonwheat", energyRestore = 8, sellPrice = 10),
        ItemId.ROOTBERRY to ItemDef(ItemId.ROOTBERRY, "Rootberry", energyRestore = 15, sellPrice = 6),
        ItemId.EMBER_PEPPER to ItemDef(ItemId.EMBER_PEPPER, "Ember Pepper", energyRestore = 14, sellPrice = 14),
        ItemId.CRYSTAL_LETTUCE to ItemDef(ItemId.CRYSTAL_LETTUCE, "Crystal Lettuce", energyRestore = 10, sellPrice = 16),
        ItemId.STARFRUIT to ItemDef(ItemId.STARFRUIT, "Starfruit", energyRestore = 25, sellPrice = 20),
        ItemId.GRILLED_ROOT to ItemDef(ItemId.GRILLED_ROOT, "Grilled Root", energyRestore = 35, sellPrice = 15),
        ItemId.PEPPER_STEW to ItemDef(ItemId.PEPPER_STEW, "Pepper Stew", energyRestore = 45, sellPrice = 28),
        ItemId.CRYSTAL_SALAD to ItemDef(ItemId.CRYSTAL_SALAD, "Crystal Salad", energyRestore = 40, sellPrice = 30),
        ItemId.WOOD to ItemDef(ItemId.WOOD, "Wood", sellPrice = 2),
        ItemId.DEEPWOOD_LOG to ItemDef(ItemId.DEEPWOOD_LOG, "Deepwood Log", sellPrice = 6),
        ItemId.STONE_ORE to ItemDef(ItemId.STONE_ORE, "Stone Ore", sellPrice = 3),
        ItemId.STAR_ORE to ItemDef(ItemId.STAR_ORE, "Star Ore", sellPrice = 18),
        ItemId.CRYSTAL_SHARD to ItemDef(ItemId.CRYSTAL_SHARD, "Crystal Shard", sellPrice = 12),
        ItemId.EMBER_COAL to ItemDef(ItemId.EMBER_COAL, "Ember Coal", sellPrice = 8),
        ItemId.FIBER to ItemDef(ItemId.FIBER, "Fiber", sellPrice = 1),
        ItemId.SILVERFIN to ItemDef(ItemId.SILVERFIN, "Silverfin", energyRestore = 18, sellPrice = 14),
        ItemId.PUFFKIN_TREAT to ItemDef(ItemId.PUFFKIN_TREAT, "Puffkin Treat", sellPrice = 5),
    )

    fun name(id: ItemId): String = defs[id]?.name ?: id.name
}

data class Recipe(
    val id: String,
    val name: String,
    val result: ItemId,
    val resultCount: Int = 1,
    val costs: Map<ItemId, Int>,
    val description: String,
    val minCraftLevel: Int = 1,
    val xp: Int = 8,
)

object Recipes {
    val all = listOf(
        Recipe(
            id = "treats",
            name = "Puffkin Treats",
            result = ItemId.PUFFKIN_TREAT,
            resultCount = 3,
            costs = mapOf(ItemId.ROOTBERRY to 2, ItemId.GLOWBEAN to 1),
            description = "Wins wild Puffkin trust.",
            xp = 6,
        ),
        Recipe(
            id = "grilled",
            name = "Grilled Root",
            result = ItemId.GRILLED_ROOT,
            costs = mapOf(ItemId.ROOTBERRY to 2, ItemId.WOOD to 1),
            description = "Hearty energy meal.",
            xp = 8,
        ),
        Recipe(
            id = "pepper_stew",
            name = "Pepper Stew",
            result = ItemId.PEPPER_STEW,
            costs = mapOf(ItemId.EMBER_PEPPER to 2, ItemId.SILVERFIN to 1, ItemId.EMBER_COAL to 1),
            description = "Spicy full restore snack.",
            minCraftLevel = 2,
            xp = 14,
        ),
        Recipe(
            id = "crystal_salad",
            name = "Crystal Salad",
            result = ItemId.CRYSTAL_SALAD,
            costs = mapOf(ItemId.CRYSTAL_LETTUCE to 2, ItemId.CRYSTAL_SHARD to 1),
            description = "Crunchy, cool, energizing.",
            minCraftLevel = 2,
            xp = 14,
        ),
        Recipe(
            id = "seed_glow",
            name = "Glowbean Seeds ×4",
            result = ItemId.GLOWBEAN_SEED,
            resultCount = 4,
            costs = mapOf(ItemId.GLOWBEAN to 1),
            description = "Replant harvest.",
            xp = 4,
        ),
        Recipe(
            id = "seed_moon",
            name = "Moonwheat Seeds ×3",
            result = ItemId.MOONWHEAT_SEED,
            resultCount = 3,
            costs = mapOf(ItemId.MOONWHEAT to 1),
            description = "Replant harvest.",
            xp = 4,
        ),
        Recipe(
            id = "seed_root",
            name = "Rootberry Seeds ×5",
            result = ItemId.ROOTBERRY_SEED,
            resultCount = 5,
            costs = mapOf(ItemId.ROOTBERRY to 1),
            description = "Replant harvest.",
            xp = 4,
        ),
        Recipe(
            id = "seed_pepper",
            name = "Ember Pepper Seeds ×3",
            result = ItemId.EMBER_PEPPER_SEED,
            resultCount = 3,
            costs = mapOf(ItemId.EMBER_PEPPER to 1),
            description = "Replant harvest.",
            minCraftLevel = 2,
            xp = 6,
        ),
        Recipe(
            id = "seed_lettuce",
            name = "Crystal Lettuce Seeds ×3",
            result = ItemId.CRYSTAL_LETTUCE_SEED,
            resultCount = 3,
            costs = mapOf(ItemId.CRYSTAL_LETTUCE to 1),
            description = "Replant harvest.",
            minCraftLevel = 2,
            xp = 6,
        ),
        Recipe(
            id = "rod",
            name = "Fishing Rod",
            result = ItemId.FISHING_ROD,
            resultCount = 1,
            costs = mapOf(ItemId.WOOD to 4, ItemId.FIBER to 3),
            description = "Cast at glowing shore spots.",
            xp = 12,
        ),
    )
}
