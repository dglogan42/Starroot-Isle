package com.starrootisle.app.data

import android.content.Context
import com.starrootisle.app.game.Biome
import com.starrootisle.app.game.Crop
import com.starrootisle.app.game.CropKind
import com.starrootisle.app.game.GameState
import com.starrootisle.app.game.ItemId
import com.starrootisle.app.game.Player
import com.starrootisle.app.game.Profession
import com.starrootisle.app.game.ProfessionProgress
import com.starrootisle.app.game.Puffkin
import com.starrootisle.app.game.PuffkinColor
import com.starrootisle.app.game.QuestFlag
import com.starrootisle.app.game.QuestId
import com.starrootisle.app.game.QuestLog
import com.starrootisle.app.game.TileType
import com.starrootisle.app.game.Tool
import org.json.JSONArray
import org.json.JSONObject

object SaveGame {
    private const val PREFS = "starroot_isle_save"
    private const val KEY = "slot0"
    private const val VERSION = 3

    fun hasSave(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun save(context: Context, state: GameState) {
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("seed", state.world.seedUsed)
        o.put("timeOfDay", state.timeOfDay.toDouble())
        o.put("coop", state.coopEnabled)
        o.put("player", playerJson(state.player))
        state.player2?.let { o.put("player2", playerJson(it)) }
        o.put("quests", questJson(state.quests))

        val tiles = JSONArray()
        for (y in 0 until state.world.height) {
            for (x in 0 until state.world.width) {
                val t = state.world.tiles[y][x]
                val to = JSONObject()
                to.put("t", t.type.name)
                to.put("b", t.biome.name)
                to.put("rh", t.rockHp)
                to.put("th", t.treeHp)
                t.crop?.let { c ->
                    val co = JSONObject()
                    co.put("k", c.kind.name)
                    co.put("s", c.stage)
                    co.put("w", c.watered)
                    to.put("c", co)
                }
                tiles.put(to)
            }
        }
        o.put("w", state.world.width)
        o.put("h", state.world.height)
        o.put("tiles", tiles)

        val puffs = JSONArray()
        for (p in state.world.puffkins) {
            val po = JSONObject()
            po.put("id", p.id)
            po.put("x", p.x.toDouble())
            po.put("y", p.y.toDouble())
            po.put("color", p.color.name)
            po.put("trust", p.trust.toDouble())
            po.put("bef", p.befriended)
            po.put("ft", p.followTarget)
            puffs.put(po)
        }
        o.put("puffkins", puffs)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, o.toString())
            .apply()
    }

    fun load(context: Context): GameState? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(raw)
            // Old saves without version → force new game if dimensions mismatch
            val seed = o.getLong("seed")
            val state = GameState(seed)
            val w = o.optInt("w", state.world.width)
            val h = o.optInt("h", state.world.height)
            if (w != state.world.width || h != state.world.height) {
                // Regen world size changed — keep seed but accept new gen; still try player stats
            } else {
                val tiles = o.getJSONArray("tiles")
                var i = 0
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val to = tiles.getJSONObject(i++)
                        val tile = state.world.tiles[y][x]
                        tile.type = TileType.valueOf(to.getString("t"))
                        tile.biome = try {
                            Biome.valueOf(to.optString("b", Biome.MEADOW.name))
                        } catch (_: Exception) {
                            Biome.MEADOW
                        }
                        tile.rockHp = to.optInt("rh", 0)
                        tile.treeHp = to.optInt("th", 0)
                        tile.crop = if (to.has("c")) {
                            val co = to.getJSONObject("c")
                            Crop(
                                kind = CropKind.valueOf(co.getString("k")),
                                stage = co.getInt("s"),
                                watered = co.getBoolean("w"),
                            )
                        } else null
                    }
                }
            }

            if (o.has("player")) {
                applyPlayer(state.player, o.getJSONObject("player"))
            } else {
                // v1 compat
                state.player.x = o.getDouble("px").toFloat()
                state.player.y = o.getDouble("py").toFloat()
                state.player.energy = o.getInt("energy")
                state.player.coins = o.getInt("coins")
                state.player.day = o.getInt("day")
                state.player.tool = Tool.valueOf(o.getString("tool"))
                val inv = o.getJSONObject("inv")
                state.player.inventory.clear()
                for (key in inv.keys()) {
                    try {
                        state.player.inventory[ItemId.valueOf(key)] = inv.getInt(key)
                    } catch (_: Exception) {
                    }
                }
            }

            if (o.has("player2")) {
                val p2 = Player.starter(state.world, guest = true)
                applyPlayer(p2, o.getJSONObject("player2"))
                state.player2 = p2
            }
            state.coopEnabled = o.optBoolean("coop", false)
            state.timeOfDay = o.optDouble("timeOfDay", 0.25).toFloat()
            if (o.has("quests")) applyQuests(state.quests, o.getJSONObject("quests"))

            state.world.puffkins.clear()
            val puffs = o.getJSONArray("puffkins")
            for (pi in 0 until puffs.length()) {
                val po = puffs.getJSONObject(pi)
                state.world.puffkins.add(
                    Puffkin(
                        id = po.getInt("id"),
                        x = po.getDouble("x").toFloat(),
                        y = po.getDouble("y").toFloat(),
                        color = PuffkinColor.valueOf(po.getString("color")),
                        trust = po.getDouble("trust").toFloat(),
                        befriended = po.getBoolean("bef"),
                        followTarget = po.optInt("ft", 0),
                    )
                )
            }
            state.setStatus("Welcome back — day ${state.player.day}.", 2f)
            state
        } catch (_: Exception) {
            null
        }
    }

    private fun playerJson(p: Player): JSONObject {
        val o = JSONObject()
        o.put("x", p.x.toDouble())
        o.put("y", p.y.toDouble())
        o.put("energy", p.energy)
        o.put("maxEnergy", p.maxEnergy)
        o.put("coins", p.coins)
        o.put("day", p.day)
        o.put("tool", p.tool.name)
        o.put("seedSel", p.seedSelection.name)
        o.put("rod", p.hasFishingRod)
        val inv = JSONObject()
        for ((k, v) in p.inventory) inv.put(k.name, v)
        o.put("inv", inv)
        val prof = JSONObject()
        for (pr in Profession.entries) {
            val pp = p.professions.progress[pr] ?: ProfessionProgress()
            val jo = JSONObject()
            jo.put("lv", pp.level)
            jo.put("xp", pp.xp)
            prof.put(pr.name, jo)
        }
        o.put("prof", prof)
        return o
    }

    private fun questJson(q: QuestLog): JSONObject {
        val o = JSONObject()
        o.put("beat", q.storyBeat)
        o.put("chapter", q.chapterUnlocked)
        val flags = JSONObject()
        for ((k, v) in q.flags) flags.put(k.name, v)
        o.put("flags", flags)
        val done = JSONArray()
        q.completed.forEach { done.put(it.name) }
        o.put("done", done)
        val claimed = JSONArray()
        q.claimed.forEach { claimed.put(it.name) }
        o.put("claimed", claimed)
        val prog = JSONObject()
        for ((k, v) in q.progress) prog.put(k.name, v)
        o.put("progress", prog)
        return o
    }

    private fun applyQuests(q: QuestLog, o: JSONObject) {
        q.storyBeat = o.optString("beat", q.storyBeat)
        q.chapterUnlocked = o.optInt("chapter", 1)
        q.flags.clear()
        val flags = o.optJSONObject("flags")
        if (flags != null) {
            for (key in flags.keys()) {
                try {
                    q.flags[QuestFlag.valueOf(key)] = flags.getInt(key)
                } catch (_: Exception) {
                }
            }
        }
        q.completed.clear()
        val done = o.optJSONArray("done")
        if (done != null) {
            for (i in 0 until done.length()) {
                try {
                    q.completed.add(QuestId.valueOf(done.getString(i)))
                } catch (_: Exception) {
                }
            }
        }
        q.claimed.clear()
        val claimed = o.optJSONArray("claimed")
        if (claimed != null) {
            for (i in 0 until claimed.length()) {
                try {
                    q.claimed.add(QuestId.valueOf(claimed.getString(i)))
                } catch (_: Exception) {
                }
            }
        }
        q.progress.clear()
        val prog = o.optJSONObject("progress")
        if (prog != null) {
            for (key in prog.keys()) {
                try {
                    q.progress[QuestId.valueOf(key)] = prog.getInt(key)
                } catch (_: Exception) {
                }
            }
        }
        q.recompute()
    }

    private fun applyPlayer(p: Player, o: JSONObject) {
        p.x = o.getDouble("x").toFloat()
        p.y = o.getDouble("y").toFloat()
        p.energy = o.getInt("energy")
        p.maxEnergy = o.optInt("maxEnergy", 100)
        p.coins = o.getInt("coins")
        p.day = o.getInt("day")
        p.tool = try {
            Tool.valueOf(o.getString("tool"))
        } catch (_: Exception) {
            Tool.HOE
        }
        p.seedSelection = try {
            CropKind.valueOf(o.optString("seedSel", CropKind.GLOWBEAN.name))
        } catch (_: Exception) {
            CropKind.GLOWBEAN
        }
        p.hasFishingRod = o.optBoolean("rod", false)
        p.inventory.clear()
        val inv = o.getJSONObject("inv")
        for (key in inv.keys()) {
            try {
                val id = ItemId.valueOf(key)
                p.inventory[id] = inv.getInt(key)
                if (id == ItemId.FISHING_ROD) p.hasFishingRod = true
            } catch (_: Exception) {
            }
        }
        if (o.has("prof")) {
            val prof = o.getJSONObject("prof")
            for (key in prof.keys()) {
                try {
                    val pr = Profession.valueOf(key)
                    val jo = prof.getJSONObject(key)
                    val pp = ProfessionProgress(xp = jo.optInt("xp", 0), level = jo.optInt("lv", 1))
                    p.professions.progress[pr] = pp
                } catch (_: Exception) {
                }
            }
        }
    }
}
