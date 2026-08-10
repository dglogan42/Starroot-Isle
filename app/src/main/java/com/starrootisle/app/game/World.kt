package com.starrootisle.app.game

import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

class World(
    val width: Int = 56,
    val height: Int = 56,
    seed: Long = System.currentTimeMillis(),
) {
    private val rng = Random(seed)
    val tiles: Array<Array<Tile>> = Array(height) { Array(width) { Tile(TileType.GRASS) } }
    val puffkins = mutableListOf<Puffkin>()
    var spawnX = width / 2f
    var spawnY = height / 2f
    val seedUsed = seed

    init {
        generate()
    }

    fun get(tx: Int, ty: Int): Tile? {
        if (tx !in 0 until width || ty !in 0 until height) return null
        return tiles[ty][tx]
    }

    fun tileAt(wx: Float, wy: Float): Tile? =
        get(floor(wx).toInt(), floor(wy).toInt())

    fun blocksAt(wx: Float, wy: Float): Boolean {
        val t = tileAt(wx, wy) ?: return true
        return t.blocksWalk()
    }

    fun biomeAt(wx: Float, wy: Float): Biome =
        tileAt(wx, wy)?.biome ?: Biome.OCEAN

    private fun generate() {
        val cx = width / 2f
        val cy = height / 2f
        val rx = width * 0.44f
        val ry = height * 0.42f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                val d = nx * nx + ny * ny
                val noise = (rng.nextFloat() - 0.5f) * 0.14f
                val dist = d + noise
                val angle = atan2(ny, nx) // -PI..PI
                val biome = biomeFor(angle, dist)

                tiles[y][x] = when {
                    dist > 1.18f -> Tile(TileType.WATER_DEEP, Biome.OCEAN)
                    dist > 1.02f -> Tile(TileType.WATER, Biome.OCEAN)
                    dist > 0.90f -> Tile(TileType.SAND, Biome.SHORE)
                    else -> baseFloor(biome)
                }
            }
        }

        // Farm dirt near center (meadow)
        for (i in 0 until 100) {
            val x = cx.toInt() + rng.nextInt(-7, 8)
            val y = cy.toInt() + rng.nextInt(-5, 7)
            val t = get(x, y)
            if (t != null && t.type == TileType.GRASS) {
                t.type = TileType.DIRT
                t.biome = Biome.MEADOW
            }
        }

        // Region features
        scatterRocks()
        scatterTrees()
        scatterStarroot()
        scatterFishingSpots()

        // Paths + home
        val sx = cx.toInt()
        val sy = cy.toInt()
        for (dx in -2..2) {
            for (dy in -2..2) {
                val t = get(sx + dx, sy + dy)
                if (t != null && !t.blocksWalk() && t.type != TileType.WATER && t.type != TileType.SAND) {
                    if (hypot(dx.toFloat(), dy.toFloat()) < 2.3f) {
                        t.type = TileType.PATH
                        t.biome = Biome.MEADOW
                    }
                }
            }
        }
        placeNear(sx, sy + 2, TileType.TENT, Biome.MEADOW)
        placeNear(sx + 2, sy + 1, TileType.WORKBENCH, Biome.MEADOW)
        get(sx, sy)?.apply {
            type = TileType.PATH
            biome = Biome.MEADOW
        }
        spawnX = sx + 0.5f
        spawnY = sy + 0.5f

        // Puffkins across biomes
        var pid = 0
        val colors = PuffkinColor.entries
        for (i in 0 until 10) {
            var tries = 0
            while (tries++ < 50) {
                val x = rng.nextFloat() * width
                val y = rng.nextFloat() * height
                if (!blocksAt(x, y) && tileAt(x, y)?.type != TileType.WATER) {
                    puffkins.add(
                        Puffkin(
                            id = pid++,
                            x = x,
                            y = y,
                            color = colors[rng.nextInt(colors.size)],
                        )
                    )
                    break
                }
            }
        }
    }

    private fun biomeFor(angle: Float, dist: Float): Biome {
        if (dist > 0.9f) return Biome.SHORE
        // Quadrants of the island
        return when {
            angle >= -PI_F * 0.75f && angle < -PI_F * 0.25f -> Biome.DEEPWOOD // north-ish
            angle >= -PI_F * 0.25f && angle < PI_F * 0.25f -> Biome.CRYSTAL   // east
            angle >= PI_F * 0.25f && angle < PI_F * 0.75f -> Biome.EMBER      // south
            else -> Biome.MEADOW // west + center bias
        }.let { b ->
            // Soft center meadow
            if (dist < 0.28f) Biome.MEADOW else b
        }
    }

    private fun baseFloor(biome: Biome): Tile = when (biome) {
        Biome.MEADOW -> Tile(TileType.GRASS, Biome.MEADOW)
        Biome.DEEPWOOD -> Tile(TileType.GRASS, Biome.DEEPWOOD)
        Biome.CRYSTAL -> Tile(TileType.CRYSTAL_FLOOR, Biome.CRYSTAL)
        Biome.EMBER -> Tile(if (rng.nextFloat() < 0.35f) TileType.ASH else TileType.SAND, Biome.EMBER)
        Biome.SHORE -> Tile(TileType.SAND, Biome.SHORE)
        Biome.OCEAN -> Tile(TileType.WATER, Biome.OCEAN)
    }

    private fun scatterRocks() {
        for (i in 0 until 70) {
            val x = rng.nextInt(width)
            val y = rng.nextInt(height)
            val t = get(x, y) ?: continue
            if (t.blocksWalk() || t.type == TileType.SAND || t.type == TileType.WATER) continue
            when (t.biome) {
                Biome.CRYSTAL -> {
                    t.type = TileType.CRYSTAL_ROCK
                    t.rockHp = 4 + rng.nextInt(3)
                }
                Biome.EMBER -> {
                    t.type = TileType.EMBER_ROCK
                    t.rockHp = 3 + rng.nextInt(3)
                }
                else -> {
                    t.type = TileType.ROCK
                    t.rockHp = 3 + rng.nextInt(3)
                }
            }
        }
    }

    private fun scatterTrees() {
        for (i in 0 until 90) {
            val x = rng.nextInt(width)
            val y = rng.nextInt(height)
            val t = get(x, y) ?: continue
            if (t.type != TileType.GRASS && t.type != TileType.CRYSTAL_FLOOR && t.type != TileType.ASH) continue
            if (t.biome == Biome.DEEPWOOD || rng.nextFloat() < 0.45f) {
                if (t.biome == Biome.DEEPWOOD && rng.nextFloat() < 0.65f) {
                    t.type = TileType.DEEPWOOD_TREE
                    t.treeHp = 3 + rng.nextInt(2)
                } else if (t.biome != Biome.EMBER) {
                    t.type = TileType.TREE
                    t.treeHp = 2 + rng.nextInt(2)
                }
            }
        }
    }

    private fun scatterStarroot() {
        for (i in 0 until 10) {
            val x = rng.nextInt(width)
            val y = rng.nextInt(height)
            val t = get(x, y) ?: continue
            if (t.type == TileType.GRASS || t.type == TileType.CRYSTAL_FLOOR) {
                t.type = TileType.STARROOT
            }
        }
    }

    private fun scatterFishingSpots() {
        var placed = 0
        var tries = 0
        while (placed < 8 && tries++ < 200) {
            val x = rng.nextInt(width)
            val y = rng.nextInt(height)
            val t = get(x, y) ?: continue
            if (t.type != TileType.SAND && t.type != TileType.ASH) continue
            // Prefer near water
            val nearWater = listOf(x to y - 1, x to y + 1, x - 1 to y, x + 1 to y)
                .any { (nx, ny) ->
                    val n = get(nx, ny)
                    n?.type == TileType.WATER || n?.type == TileType.WATER_DEEP
                }
            if (nearWater) {
                t.type = TileType.FISHING_SPOT
                placed++
            }
        }
    }

    private fun placeNear(x: Int, y: Int, type: TileType, biome: Biome) {
        for (r in 0..5) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    val t = get(x + dx, y + dy)
                    if (t != null && !t.blocksWalk() &&
                        t.type != TileType.WATER && t.type != TileType.WATER_DEEP
                    ) {
                        t.type = type
                        t.biome = biome
                        return
                    }
                }
            }
        }
    }

    fun growCrops() {
        for (row in tiles) {
            for (tile in row) {
                val c = tile.crop ?: continue
                if (c.watered && !c.ready) {
                    // Bonus growth in preferred biome
                    val bonus = if (c.kind.preferredBiome == tile.biome) 1 else 0
                    c.stage = (c.stage + 1 + if (bonus > 0 && Random.nextFloat() < 0.35f) 1 else 0)
                        .coerceAtMost(c.kind.maxStage)
                    c.watered = false
                }
            }
        }
    }

    fun nearestPuffkin(wx: Float, wy: Float, radius: Float = 1.5f): Puffkin? {
        return puffkins.minByOrNull { hypot(it.x - wx, it.y - wy) }
            ?.takeIf { hypot(it.x - wx, it.y - wy) <= radius }
    }

    companion object {
        private const val PI_F = Math.PI.toFloat()
    }
}
