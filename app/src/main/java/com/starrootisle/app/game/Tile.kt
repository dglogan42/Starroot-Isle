package com.starrootisle.app.game

enum class Biome(
    val displayName: String,
    val tint: Long, // ARGB hint for grass variant
) {
    MEADOW("Meadow", 0xFF5DAD4AL),
    DEEPWOOD("Deepwood", 0xFF3D6B3AL),
    CRYSTAL("Crystal Hollows", 0xFF6B8FBF),
    EMBER("Ember Shore", 0xFFB86B3A),
    SHORE("Shore", 0xFFE8D5A3),
    OCEAN("Ocean", 0xFF246B9AL),
}

enum class TileType {
    GRASS,
    DIRT,
    SOIL,
    SAND,
    ASH,           // ember biome floor
    CRYSTAL_FLOOR, // crystal biome walkable
    WATER,
    WATER_DEEP,
    STONE,
    ROCK,
    CRYSTAL_ROCK,  // mineable, star ore / shards
    EMBER_ROCK,    // mineable coal
    TREE,
    DEEPWOOD_TREE, // more wood / fiber
    STARROOT,
    PATH,
    TENT,
    WORKBENCH,
    FENCE,
    FISHING_SPOT,  // stand adjacent, use rod
    SIGN,          // biome marker (walkable overlay draw)
}

data class Crop(
    val kind: CropKind,
    var stage: Int = 0,
    var watered: Boolean = false,
) {
    val ready: Boolean get() = stage >= kind.maxStage
}

enum class CropKind(
    val displayName: String,
    val maxStage: Int,
    val seedItem: ItemId,
    val harvestItem: ItemId,
    val harvestCount: Int,
    val preferredBiome: Biome? = null,
) {
    GLOWBEAN("Glowbean", 3, ItemId.GLOWBEAN_SEED, ItemId.GLOWBEAN, 2, Biome.MEADOW),
    MOONWHEAT("Moonwheat", 4, ItemId.MOONWHEAT_SEED, ItemId.MOONWHEAT, 3, Biome.MEADOW),
    ROOTBERRY("Rootberry", 2, ItemId.ROOTBERRY_SEED, ItemId.ROOTBERRY, 2, Biome.DEEPWOOD),
    EMBER_PEPPER("Ember Pepper", 3, ItemId.EMBER_PEPPER_SEED, ItemId.EMBER_PEPPER, 2, Biome.EMBER),
    CRYSTAL_LETTUCE("Crystal Lettuce", 4, ItemId.CRYSTAL_LETTUCE_SEED, ItemId.CRYSTAL_LETTUCE, 2, Biome.CRYSTAL),
}

data class Tile(
    var type: TileType,
    var biome: Biome = Biome.MEADOW,
    var crop: Crop? = null,
    var rockHp: Int = 0,
    var treeHp: Int = 0,
) {
    fun blocksWalk(): Boolean = when (type) {
        TileType.WATER, TileType.WATER_DEEP,
        TileType.ROCK, TileType.CRYSTAL_ROCK, TileType.EMBER_ROCK,
        TileType.TREE, TileType.DEEPWOOD_TREE, TileType.STARROOT,
        TileType.FENCE -> true
        else -> false
    }

    fun isMineable(): Boolean = type == TileType.ROCK ||
        type == TileType.CRYSTAL_ROCK || type == TileType.EMBER_ROCK

    fun isChoppable(): Boolean = type == TileType.TREE || type == TileType.DEEPWOOD_TREE
}
