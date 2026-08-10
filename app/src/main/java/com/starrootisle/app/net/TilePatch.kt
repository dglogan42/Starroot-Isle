package com.starrootisle.app.net

import com.starrootisle.app.game.Biome
import com.starrootisle.app.game.Crop
import com.starrootisle.app.game.CropKind
import com.starrootisle.app.game.Tile
import com.starrootisle.app.game.TileType
import com.starrootisle.app.game.World
import org.json.JSONObject

/**
 * Network-serializable world tile mutation for online co-op sync.
 */
data class TilePatch(
    val x: Int,
    val y: Int,
    val type: String,
    val biome: String,
    val rockHp: Int = 0,
    val treeHp: Int = 0,
    val cropKind: String? = null,
    val cropStage: Int = 0,
    val cropWatered: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("x", x)
        .put("y", y)
        .put("t", type)
        .put("b", biome)
        .put("rh", rockHp)
        .put("th", treeHp)
        .apply {
            if (cropKind != null) {
                put("ck", cropKind)
                put("cs", cropStage)
                put("cw", cropWatered)
            }
        }

    fun applyTo(world: World) {
        val tile = world.get(x, y) ?: return
        try {
            tile.type = TileType.valueOf(type)
            tile.biome = try {
                Biome.valueOf(biome)
            } catch (_: Exception) {
                tile.biome
            }
            tile.rockHp = rockHp
            tile.treeHp = treeHp
            tile.crop = if (cropKind != null) {
                Crop(
                    kind = CropKind.valueOf(cropKind),
                    stage = cropStage,
                    watered = cropWatered,
                )
            } else null
        } catch (_: Exception) {
            // ignore malformed remote patches
        }
    }

    companion object {
        fun from(x: Int, y: Int, tile: Tile): TilePatch = TilePatch(
            x = x,
            y = y,
            type = tile.type.name,
            biome = tile.biome.name,
            rockHp = tile.rockHp,
            treeHp = tile.treeHp,
            cropKind = tile.crop?.kind?.name,
            cropStage = tile.crop?.stage ?: 0,
            cropWatered = tile.crop?.watered ?: false,
        )

        fun fromJson(o: JSONObject): TilePatch = TilePatch(
            x = o.getInt("x"),
            y = o.getInt("y"),
            type = o.getString("t"),
            biome = o.optString("b", Biome.MEADOW.name),
            rockHp = o.optInt("rh", 0),
            treeHp = o.optInt("th", 0),
            cropKind = if (o.has("ck")) o.getString("ck") else null,
            cropStage = o.optInt("cs", 0),
            cropWatered = o.optBoolean("cw", false),
        )
    }
}
