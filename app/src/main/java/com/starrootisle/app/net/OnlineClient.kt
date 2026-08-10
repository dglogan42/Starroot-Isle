package com.starrootisle.app.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class RemotePeer(
    val id: String,
    val name: String,
    var x: Float,
    var y: Float,
    var fx: Float,
    var fy: Float,
    var tool: String,
    var color: Int,
    var host: Boolean,
)

class OnlineClient(
    private val onEvent: (OnlineEvent) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null
    private val open = AtomicBoolean(false)

    var roomCode: String? = null
        private set
    var playerId: String? = null
        private set
    var isHost: Boolean = false
        private set
    var worldSeed: Long? = null
        private set
    var serverUrl: String = ""
        private set

    val peers = mutableMapOf<String, RemotePeer>()

    fun connect(url: String) {
        disconnect()
        serverUrl = url
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                open.set(true)
                onEvent(OnlineEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    handle(JSONObject(text))
                } catch (e: Exception) {
                    onEvent(OnlineEvent.Error(e.message ?: "parse error"))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                open.set(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                open.set(false)
                onEvent(OnlineEvent.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                open.set(false)
                onEvent(OnlineEvent.Error(t.message ?: "connection failed"))
            }
        })
    }

    fun disconnect() {
        try {
            ws?.close(1000, "bye")
        } catch (_: Exception) {
        }
        ws = null
        open.set(false)
        roomCode = null
        playerId = null
        peers.clear()
    }

    fun isConnected(): Boolean = open.get()

    fun create(name: String, seed: Long) {
        send(JSONObject().put("type", "create").put("name", name).put("seed", seed))
    }

    fun join(name: String, room: String) {
        send(JSONObject().put("type", "join").put("name", name).put("room", room))
    }

    fun sendState(
        x: Float, y: Float, fx: Float, fy: Float,
        tool: String, day: Int, color: Int = 0,
    ) {
        send(
            JSONObject()
                .put("type", "state")
                .put("x", x.toDouble())
                .put("y", y.toDouble())
                .put("fx", fx.toDouble())
                .put("fy", fy.toDouble())
                .put("tool", tool)
                .put("day", day)
                .put("color", color)
        )
    }

    fun sendTile(patch: TilePatch) {
        send(JSONObject().put("type", "tile").put("patch", patch.toJson()))
    }

    fun sendTiles(patches: List<TilePatch>) {
        if (patches.isEmpty()) return
        val arr = JSONArray()
        patches.forEach { arr.put(it.toJson()) }
        send(JSONObject().put("type", "tiles").put("patches", arr))
    }

    fun sendDayTick() {
        send(JSONObject().put("type", "day_tick"))
    }

    fun say(text: String) {
        send(JSONObject().put("type", "say").put("text", text))
    }

    private fun send(o: JSONObject) {
        ws?.send(o.toString())
    }

    private fun handle(o: JSONObject) {
        when (o.getString("type")) {
            "created", "joined" -> {
                roomCode = o.getString("room")
                playerId = o.getString("playerId")
                isHost = o.optBoolean("host", false)
                worldSeed = o.getLong("seed")
                ingestPeers(o.optJSONArray("peers"))
                val patches = parsePatches(o.optJSONArray("patches"))
                onEvent(
                    OnlineEvent.InRoom(
                        room = roomCode!!,
                        seed = worldSeed!!,
                        host = isHost,
                        playerId = playerId!!,
                        patches = patches,
                    )
                )
            }
            "peers" -> {
                ingestPeers(o.optJSONArray("peers"))
                onEvent(OnlineEvent.PeersUpdated)
            }
            "tile" -> {
                val patch = TilePatch.fromJson(o.getJSONObject("patch"))
                onEvent(
                    OnlineEvent.RemoteTile(
                        from = o.optString("from"),
                        name = o.optString("name"),
                        patch = patch,
                    )
                )
            }
            "tiles" -> {
                val patches = parsePatches(o.optJSONArray("patches"))
                onEvent(OnlineEvent.RemoteTileBatch(patches))
            }
            "day_tick" -> {
                onEvent(
                    OnlineEvent.DayTick(
                        from = o.optString("from"),
                        name = o.optString("name"),
                    )
                )
            }
            "chat" -> {
                onEvent(
                    OnlineEvent.Chat(
                        from = o.optString("from"),
                        name = o.optString("name"),
                        text = o.optString("text"),
                    )
                )
            }
            "error" -> onEvent(OnlineEvent.Error(o.optString("message", "error")))
            "pong" -> {}
        }
    }

    private fun parsePatches(arr: JSONArray?): List<TilePatch> {
        if (arr == null) return emptyList()
        val out = mutableListOf<TilePatch>()
        for (i in 0 until arr.length()) {
            try {
                out.add(TilePatch.fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun ingestPeers(arr: JSONArray?) {
        if (arr == null) return
        val seen = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val id = p.getString("id")
            if (id == playerId) continue
            seen.add(id)
            val existing = peers[id]
            if (existing != null) {
                existing.x = p.getDouble("x").toFloat()
                existing.y = p.getDouble("y").toFloat()
                existing.fx = p.optDouble("fx", 0.0).toFloat()
                existing.fy = p.optDouble("fy", 1.0).toFloat()
                existing.tool = p.optString("tool", "HAND")
                existing.color = p.optInt("color", 0)
                existing.host = p.optBoolean("host", false)
            } else {
                peers[id] = RemotePeer(
                    id = id,
                    name = p.optString("name", "Settler"),
                    x = p.getDouble("x").toFloat(),
                    y = p.getDouble("y").toFloat(),
                    fx = p.optDouble("fx", 0.0).toFloat(),
                    fy = p.optDouble("fy", 1.0).toFloat(),
                    tool = p.optString("tool", "HAND"),
                    color = p.optInt("color", 0),
                    host = p.optBoolean("host", false),
                )
            }
        }
        peers.keys.filter { it !in seen }.forEach { peers.remove(it) }
    }
}

sealed class OnlineEvent {
    data object Connected : OnlineEvent()
    data object Disconnected : OnlineEvent()
    data object PeersUpdated : OnlineEvent()
    data class InRoom(
        val room: String,
        val seed: Long,
        val host: Boolean,
        val playerId: String,
        val patches: List<com.starrootisle.app.net.TilePatch> = emptyList(),
    ) : OnlineEvent()
    data class Chat(val from: String, val name: String, val text: String) : OnlineEvent()
    data class RemoteTile(
        val from: String,
        val name: String,
        val patch: com.starrootisle.app.net.TilePatch,
    ) : OnlineEvent()
    data class RemoteTileBatch(val patches: List<com.starrootisle.app.net.TilePatch>) : OnlineEvent()
    data class DayTick(val from: String, val name: String) : OnlineEvent()
    data class Error(val message: String) : OnlineEvent()
}
