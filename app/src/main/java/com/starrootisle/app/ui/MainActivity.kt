package com.starrootisle.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.starrootisle.app.BuildConfig
import com.starrootisle.app.R
import com.starrootisle.app.audio.MusicEngine
import com.starrootisle.app.audio.SoundManager
import com.starrootisle.app.data.SaveGame
import com.starrootisle.app.databinding.ActivityMainBinding
import com.starrootisle.app.game.Cutscenes
import com.starrootisle.app.game.GameState
import com.starrootisle.app.game.Items
import com.starrootisle.app.game.Profession
import com.starrootisle.app.game.Recipes
import com.starrootisle.app.game.Tool
import com.starrootisle.app.net.OnlineClient
import com.starrootisle.app.net.OnlineEvent
import com.starrootisle.app.net.RoomQr
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var state: GameState? = null
    private var autosaveCounter = 0
    private var netSyncCounter = 0
    private lateinit var sound: SoundManager
    private lateinit var music: MusicEngine
    private var online: OnlineClient? = null
    private var playerName: String = "Settler"
    private var pendingConnectAction: (() -> Unit)? = null
    private var pendingCreate = false
    private var pendingJoin = false
    private var lastCreateSeed: Long = 0
    private var lastJoinCode: String = ""
    private var pendingJoinWs: String? = null
    private var lastQrBitmap: Bitmap? = null

    private val pickQrImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val text = RoomQr.decode(bmp)
            if (text == null) {
                Toast.makeText(this, "No QR found in image", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val parsed = RoomQr.parseAny(text)
            if (parsed == null) {
                Toast.makeText(this, "QR not a Starroot room link", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val (room, ws) = parsed
            pendingJoinWs = ws
            lastJoinCode = room
            pendingJoin = true
            pendingCreate = false
            val url = ws ?: BuildConfig.ONLINE_URL
            ensureOnlineAnd(url) {
                online?.join(playerName, room)
                pendingJoin = false
            }
            Toast.makeText(this, "Joining room $room…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sound = SoundManager()
        music = MusicEngine()

        if (SaveGame.hasSave(this)) {
            binding.btnContinue.visibility = View.VISIBLE
        }

        binding.btnContinue.setOnClickListener {
            sound.play(SoundManager.Sfx.UI)
            val loaded = SaveGame.load(this)
            if (loaded != null) startGame(loaded, playIntro = false)
            else Toast.makeText(this, "Save unreadable — start a new island.", Toast.LENGTH_SHORT).show()
        }

        binding.btnNewGame.setOnClickListener {
            sound.play(SoundManager.Sfx.UI)
            if (SaveGame.hasSave(this)) {
                AlertDialog.Builder(this)
                    .setTitle("New Island?")
                    .setMessage("This overwrites your saved island.")
                    .setPositiveButton("Start fresh") { _, _ ->
                        SaveGame.clear(this)
                        startGame(GameState(), playIntro = true)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                startGame(GameState(), playIntro = true)
            }
        }

        binding.btnHow.setOnClickListener {
            sound.play(SoundManager.Sfx.UI)
            showHowTo()
        }

        binding.btnTool.setOnClickListener {
            val s = state ?: return@setOnClickListener
            s.player.cycleTool()
            s.setStatus("P1 Tool: ${toolLabel(s.player.tool, s)}")
            sound.play(SoundManager.Sfx.UI)
            refreshHud(s)
        }

        binding.btnTool.setOnLongClickListener {
            val s = state ?: return@setOnLongClickListener true
            if (s.coopEnabled && s.player2 != null) {
                s.player2!!.cycleTool()
                s.setStatus("P2 Tool: ${toolLabel(s.player2!!.tool, s, p2 = true)}")
            } else {
                s.player.cycleSeed()
                s.player.tool = Tool.SEEDS
                s.setStatus("Seeds: ${s.player.seedSelection.displayName}")
            }
            sound.play(SoundManager.Sfx.UI)
            refreshHud(s)
            true
        }

        binding.btnInventory.setOnClickListener {
            val s = state ?: return@setOnClickListener
            sound.play(SoundManager.Sfx.UI)
            val body = buildString {
                appendLine("── P1 ◎ ${s.player.coins} ──")
                append(s.inventoryLines(false).joinToString("\n"))
                if (s.coopEnabled && s.player2 != null) {
                    appendLine()
                    appendLine("── P2 ◎ ${s.player2!!.coins} ──")
                    append(s.inventoryLines(true).joinToString("\n"))
                }
            }
            AlertDialog.Builder(this)
                .setTitle("Bag")
                .setMessage(body)
                .setPositiveButton("OK", null)
                .setNeutralButton("Sell P1 goods") { _, _ ->
                    s.sellAllCrops(false)
                    save()
                    refreshHud(s)
                }
                .show()
        }

        binding.btnCraft.setOnClickListener {
            val s = state ?: return@setOnClickListener
            sound.play(SoundManager.Sfx.UI)
            val craftLv = s.player.professions.level(Profession.CRAFTING)
            val names = Recipes.all.map {
                val lock = if (craftLv < it.minCraftLevel) " 🔒Lv${it.minCraftLevel}" else ""
                val cost = it.costs.entries.joinToString { e ->
                    "${Items.name(e.key)}×${e.value}"
                }
                "${it.name}$lock  ($cost)"
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Workbench · Craft Lv$craftLv")
                .setItems(names) { _, which ->
                    s.tryCraft(Recipes.all[which], false)
                    save()
                    refreshHud(s)
                    refreshCutscene()
                }
                .setNegativeButton("Close", null)
                .show()
        }

        binding.btnSleep.setOnClickListener {
            val s = state ?: return@setOnClickListener
            if (s.trySleep(false)) {
                save()
                refreshHud(s)
                refreshCutscene()
            } else refreshHud(s)
        }

        binding.btnProfessions.setOnClickListener {
            val s = state ?: return@setOnClickListener
            sound.play(SoundManager.Sfx.UI)
            val body = buildString {
                appendLine("── P1 ──")
                append(s.player.professions.summaryLines().joinToString("\n"))
                if (s.coopEnabled && s.player2 != null) {
                    appendLine()
                    appendLine("── P2 ──")
                    append(s.player2!!.professions.summaryLines().joinToString("\n"))
                }
            }
            AlertDialog.Builder(this)
                .setTitle("Professions")
                .setMessage(body)
                .setPositiveButton("OK", null)
                .show()
        }

        binding.btnProfessions.setOnLongClickListener {
            val s = state ?: return@setOnLongClickListener true
            s.player.cycleSeed()
            s.player.tool = Tool.SEEDS
            s.setStatus("Seeds: ${s.player.seedSelection.displayName}")
            sound.play(SoundManager.Sfx.UI)
            refreshHud(s)
            true
        }

        binding.btnStory.setOnClickListener {
            val s = state ?: return@setOnClickListener
            sound.play(SoundManager.Sfx.UI)
            val ready = s.quests.readyToClaim()
            val builder = AlertDialog.Builder(this)
                .setTitle("Heartseed Story")
                .setMessage(s.quests.journalLines().joinToString("\n"))
                .setPositiveButton("OK", null)
            if (ready.isNotEmpty()) {
                builder.setNeutralButton("Claim rewards (${ready.size})") { _, _ ->
                    s.claimAllReadyQuests()
                    save()
                    refreshHud(s)
                    refreshCutscene()
                }
            }
            builder.show()
        }

        binding.btnCoop.setOnClickListener {
            val s = state ?: return@setOnClickListener
            sound.play(SoundManager.Sfx.UI)
            if (s.coopEnabled) {
                s.disableCoop()
                binding.btnCoop.text = "Co-op"
            } else {
                s.enableCoop()
                binding.btnCoop.text = "Solo"
            }
            refreshHud(s)
        }

        binding.btnOnline.setOnClickListener {
            sound.play(SoundManager.Sfx.UI)
            showOnlineDialog()
        }

        binding.btnSound.setOnClickListener {
            sound.enabled = !sound.enabled
            binding.btnSound.text = if (sound.enabled) "SFX: On" else "SFX: Off"
            if (sound.enabled) sound.play(SoundManager.Sfx.UI)
        }

        binding.btnMusic.setOnClickListener {
            music.enabled = !music.enabled
            binding.btnMusic.text = if (music.enabled) "Music: On" else "Music: Off"
            if (music.enabled) music.start()
        }

        binding.btnMenu.setOnClickListener {
            save()
            sound.play(SoundManager.Sfx.UI)
            music.stop()
            online?.disconnect()
            binding.gameView.playing = false
            binding.hudBar.visibility = View.GONE
            binding.cutsceneOverlay.visibility = View.GONE
            binding.titleOverlay.visibility = View.VISIBLE
            binding.btnContinue.visibility = View.VISIBLE
        }

        binding.btnCutsceneNext.setOnClickListener {
            val s = state ?: return@setOnClickListener
            sound.play(SoundManager.Sfx.UI)
            if (!s.cutscenes.advance()) {
                binding.cutsceneOverlay.visibility = View.GONE
            } else {
                showCutscenePage()
            }
        }

        binding.btnCutsceneSkip.setOnClickListener {
            state?.cutscenes?.skipAll()
            binding.cutsceneOverlay.visibility = View.GONE
            sound.play(SoundManager.Sfx.UI)
        }

        binding.cutsceneOverlay.setOnClickListener {
            binding.btnCutsceneNext.performClick()
        }

        binding.gameView.onHudTick = { s ->
            autosaveCounter++
            if (autosaveCounter % 300 == 0) save()
            music.timeOfDay = s.timeOfDay
            netSyncCounter++
            if (netSyncCounter % 12 == 0) {
                online?.takeIf { it.isConnected() && it.roomCode != null }?.sendState(
                    x = s.player.x,
                    y = s.player.y,
                    fx = s.player.facingX,
                    fy = s.player.facingY,
                    tool = s.player.tool.name,
                    day = s.player.day,
                )
            }
            online?.let { client ->
                s.remotePeers.clear()
                s.remotePeers.addAll(client.peers.values)
                s.onlineRoom = client.roomCode
            }
            runOnUiThread {
                refreshHud(s)
                if (s.cutscenes.isPlaying && binding.cutsceneOverlay.visibility != View.VISIBLE) {
                    refreshCutscene()
                }
            }
        }

        binding.gameView.onAction = { guest ->
            state?.performAction(guest)
            runOnUiThread { refreshCutscene() }
        }

        handleJoinIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleJoinIntent(intent)
    }

    private fun handleJoinIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val parsed = RoomQr.parseLink(data) ?: return
        val (room, ws) = parsed
        playerName = "Visitor"
        lastJoinCode = room
        pendingJoinWs = ws
        pendingJoin = true
        // Start or continue game first if needed
        if (state == null) {
            if (SaveGame.hasSave(this)) {
                SaveGame.load(this)?.let { startGame(it, playIntro = false) }
                    ?: startGame(GameState(), playIntro = false)
            } else {
                startGame(GameState(), playIntro = false)
            }
        }
        val url = ws ?: BuildConfig.ONLINE_URL
        ensureOnlineAnd(url) {
            online?.join(playerName, room)
            pendingJoin = false
        }
        Toast.makeText(this, "Opening room $room…", Toast.LENGTH_SHORT).show()
    }

    private fun wireOnlineHooks(s: GameState) {
        s.onWorldPatch = { patch ->
            online?.takeIf { it.isConnected() && it.roomCode != null }?.sendTile(patch)
        }
        s.onDayTickLocal = {
            online?.takeIf { it.isConnected() && it.roomCode != null }?.sendDayTick()
        }
    }

    private fun showOnlineDialog() {
        val s = state
        if (s == null) {
            Toast.makeText(this, "Start a game first.", Toast.LENGTH_SHORT).show()
            return
        }
        val room = online?.roomCode
        if (room != null && online?.isConnected() == true) {
            val link = RoomQr.buildLink(room, online?.serverUrl ?: BuildConfig.ONLINE_URL)
            AlertDialog.Builder(this)
                .setTitle("Online · room $room")
                .setMessage(
                    "Tile edits sync live. Sleep grows crops for everyone.\n" +
                        "Peers: ${online?.peers?.size ?: 0}\n\n$link"
                )
                .setPositiveButton("QR & Share") { _, _ -> showRoomQr(room, link) }
                .setNeutralButton("Chat") { _, _ -> promptChat() }
                .setNegativeButton("Leave") { _, _ ->
                    online?.disconnect()
                    s.remotePeers.clear()
                    s.onlineRoom = null
                    binding.btnOnline.text = "Online"
                    refreshHud(s)
                }
                .show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val nameIn = EditText(this).apply {
            hint = "Your name"
            setText(playerName)
        }
        val roomIn = EditText(this).apply {
            hint = "Room code to join"
            inputType = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val urlIn = EditText(this).apply {
            setText(BuildConfig.ONLINE_URL)
        }
        layout.addView(nameIn)
        layout.addView(roomIn)
        layout.addView(urlIn)

        AlertDialog.Builder(this)
            .setTitle("Online visit co-op")
            .setMessage(
                "Create a room, share QR or code.\n" +
                    "Server: cd server && npm start\n" +
                    "Tiles + day ticks sync for everyone."
            )
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                playerName = nameIn.text.toString().ifBlank { "Settler" }
                val url = urlIn.text.toString().ifBlank { BuildConfig.ONLINE_URL }
                lastCreateSeed = s.world.seedUsed
                pendingCreate = true
                pendingJoin = false
                ensureOnlineAnd(url) {
                    online?.create(playerName, s.world.seedUsed)
                    pendingCreate = false
                }
            }
            .setNeutralButton("Join / Scan") { _, _ ->
                playerName = nameIn.text.toString().ifBlank { "Visitor" }
                val code = roomIn.text.toString().trim()
                if (code.isNotBlank()) {
                    val url = urlIn.text.toString().ifBlank { BuildConfig.ONLINE_URL }
                    lastJoinCode = code
                    pendingJoin = true
                    pendingCreate = false
                    ensureOnlineAnd(url) {
                        online?.join(playerName, code)
                        pendingJoin = false
                    }
                } else {
                    // scan QR from gallery
                    pickQrImage.launch("image/*")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRoomQr(room: String, link: String) {
        val bmp = RoomQr.encode(link, 640)
        lastQrBitmap = bmp
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        box.addView(ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            maxWidth = (280 * resources.displayMetrics.density).toInt()
        })
        box.addView(TextView(this).apply {
            text = "Room $room\n$link"
            setTextColor(getColor(R.color.ui_text))
            textSize = 12f
            setPadding(0, pad, 0, 0)
        })
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("Share room")
            .setView(scroll)
            .setPositiveButton("Share") { _, _ -> shareRoom(room, link, bmp) }
            .setNeutralButton("Copy code") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("room", room))
                Toast.makeText(this, "Copied $room", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun shareRoom(room: String, link: String, bmp: Bitmap) {
        try {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "starroot-room-$room.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Join my Starroot Isle room $room\n$link")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share room QR"))
        } catch (e: Exception) {
            // Fallback text share
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Join Starroot Isle room $room\n$link")
            }
            startActivity(Intent.createChooser(send, "Share room"))
        }
    }

    private fun ensureOnlineAnd(url: String, afterOpen: () -> Unit) {
        if (online == null) {
            online = OnlineClient { event -> runOnUiThread { onOnlineEvent(event) } }
        }
        val client = online!!
        if (client.isConnected()) {
            afterOpen()
        } else {
            pendingConnectAction = afterOpen
            client.connect(url)
        }
    }

    private fun onOnlineEvent(event: OnlineEvent) {
        val s = state
        when (event) {
            OnlineEvent.Connected -> {
                pendingConnectAction?.invoke()
                pendingConnectAction = null
                if (pendingCreate && s != null) {
                    online?.create(playerName, lastCreateSeed)
                    pendingCreate = false
                }
                if (pendingJoin) {
                    online?.join(playerName, lastJoinCode)
                    pendingJoin = false
                }
            }
            OnlineEvent.Disconnected -> {
                s?.remotePeers?.clear()
                s?.onlineRoom = null
                binding.btnOnline.text = "Online"
                s?.setStatus("Online disconnected.")
            }
            is OnlineEvent.InRoom -> {
                binding.btnOnline.text = "Room ${event.room}"
                s?.onlineRoom = event.room
                s?.setStatus(
                    if (event.host) "Hosting ${event.room} — share QR!"
                    else "Joined ${event.room} · tiles syncing",
                    3.5f
                )
                if (!event.host && s != null && s.world.seedUsed != event.seed) {
                    AlertDialog.Builder(this)
                        .setTitle("Visit host island")
                        .setMessage("Load host map seed so terrain + patches match?")
                        .setPositiveButton("Visit map") { _, _ ->
                            val visit = GameState(event.seed)
                            visit.applyRemotePatches(event.patches)
                            startGame(visit, playIntro = false, resumeMusic = true)
                            visit.onlineRoom = event.room
                        }
                        .setNegativeButton("Stay") { _, _ ->
                            s.applyRemotePatches(event.patches)
                        }
                        .show()
                } else {
                    s?.applyRemotePatches(event.patches)
                    // Host: push current interesting tiles so late joiners get farm state
                    if (event.host && s != null) {
                        val batch = s.collectSyncPatches()
                        if (batch.isNotEmpty()) online?.sendTiles(batch)
                    }
                }
                sound.play(SoundManager.Sfx.BOND)
                // Offer QR immediately for host
                if (event.host) {
                    val link = RoomQr.buildLink(event.room, online?.serverUrl ?: BuildConfig.ONLINE_URL)
                    binding.root.postDelayed({
                        if (online?.roomCode == event.room) showRoomQr(event.room, link)
                    }, 400)
                }
            }
            OnlineEvent.PeersUpdated -> {
                s?.remotePeers?.clear()
                online?.peers?.values?.let { s?.remotePeers?.addAll(it) }
            }
            is OnlineEvent.RemoteTile -> {
                s?.applyRemotePatch(event.patch)
            }
            is OnlineEvent.RemoteTileBatch -> {
                s?.applyRemotePatches(event.patches)
            }
            is OnlineEvent.DayTick -> {
                s?.applyRemoteDayTick()
                sound.play(SoundManager.Sfx.SLEEP)
            }
            is OnlineEvent.Chat -> {
                s?.setStatus("${event.name}: ${event.text}", 4f)
                sound.play(SoundManager.Sfx.UI)
            }
            is OnlineEvent.Error -> {
                Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
                s?.setStatus("Online: ${event.message}")
                sound.play(SoundManager.Sfx.ERROR)
            }
        }
        s?.let { refreshHud(it) }
    }

    private fun promptChat() {
        val input = EditText(this).apply {
            hint = "Wave to friends…"
            maxLines = 2
        }
        AlertDialog.Builder(this)
            .setTitle("Island chat")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) online?.say(t)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startGame(
        game: GameState,
        playIntro: Boolean = false,
        resumeMusic: Boolean = true,
    ) {
        game.sound = sound
        wireOnlineHooks(game)
        state = game
        binding.gameView.state = game
        binding.gameView.playing = true
        binding.titleOverlay.visibility = View.GONE
        binding.hudBar.visibility = View.VISIBLE
        binding.btnCoop.text = if (game.coopEnabled) "Solo" else "Co-op"
        binding.btnSound.text = if (sound.enabled) "SFX: On" else "SFX: Off"
        binding.btnMusic.text = if (music.enabled) "Music: On" else "Music: Off"
        binding.btnOnline.text = game.onlineRoom?.let { "Room $it" } ?: "Online"
        binding.gameView.post { binding.gameView.centerCamera() }
        if (resumeMusic && music.enabled) music.start()
        sound.play(SoundManager.Sfx.HARVEST)
        if (playIntro) {
            game.cutscenes.enqueue(Cutscenes.INTRO, force = true)
        }
        refreshHud(game)
        refreshCutscene()
    }

    private fun refreshCutscene() {
        val s = state ?: return
        if (!s.cutscenes.isPlaying) {
            binding.cutsceneOverlay.visibility = View.GONE
            return
        }
        binding.cutsceneOverlay.visibility = View.VISIBLE
        showCutscenePage()
    }

    private fun showCutscenePage() {
        val s = state ?: return
        val scene = s.cutscenes.active ?: return
        val page = s.cutscenes.currentPage ?: return
        binding.cutsceneTitle.text = scene.title
        binding.cutsceneSpeaker.text = page.speaker
        binding.cutsceneText.text = page.text
        val bg = when (page.mood) {
            com.starrootisle.app.game.CutscenePage.Mood.NIGHT -> 0xF01A1A3E.toInt()
            com.starrootisle.app.game.CutscenePage.Mood.FINALE -> 0xF02A1A4A.toInt()
            com.starrootisle.app.game.CutscenePage.Mood.WONDER -> 0xE01A2A3E.toInt()
            com.starrootisle.app.game.CutscenePage.Mood.WARM -> 0xE02A2218.toInt()
            else -> 0xE60D1B2A.toInt()
        }
        binding.cutsceneOverlay.setBackgroundColor(bg)
        sound.play(SoundManager.Sfx.UI)
    }

    private fun refreshHud(s: GameState) {
        val biome = s.world.biomeAt(s.player.x, s.player.y)
        val claim = s.quests.readyToClaim().size
        val storyTag = if (claim > 0) " · ★$claim" else ""
        binding.txtDay.text = "Day ${s.player.day} · ${biome.displayName}$storyTag"
        val coinStr = if (s.coopEnabled && s.player2 != null)
            "◎ ${s.player.coins} / ${s.player2!!.coins}"
        else "◎ ${s.player.coins}"
        binding.txtCoins.text = coinStr
        val e2 = s.player2?.let { " · P2 ${it.energy}" } ?: ""
        val onlineTag = s.onlineRoom?.let { " · 🌐$it" } ?: ""
        binding.txtEnergy.text = "⚡ ${s.player.energy}/${s.player.maxEnergy}$e2$onlineTag"
        binding.energyBar.max = s.player.maxEnergy
        binding.energyBar.progress = s.player.energy
        binding.energyBar.progressTintList =
            android.content.res.ColorStateList.valueOf(
                if (s.player.energy < 25) getColor(R.color.energy_low)
                else getColor(R.color.energy)
            )
        binding.txtTool.text = "P1: ${toolLabel(s.player.tool, s)}" +
            if (s.coopEnabled && s.player2 != null)
                "  |  P2: ${toolLabel(s.player2!!.tool, s, p2 = true)}"
            else ""
        if (s.statusTimer > 0f) binding.txtStatus.text = s.statusMessage
    }

    private fun toolLabel(tool: Tool, s: GameState, p2: Boolean = false): String {
        val actor = if (p2) s.player2 ?: s.player else s.player
        return when (tool) {
            Tool.HOE -> "Hoe"
            Tool.WATERING_CAN -> "Water"
            Tool.PICKAXE -> "Pick"
            Tool.AXE -> "Axe"
            Tool.FISHING_ROD -> "Rod"
            Tool.SEEDS -> "Seed:${actor.seedSelection.displayName.take(8)}"
            Tool.TREATS -> "Treats"
            Tool.HAND -> "Hand"
        }
    }

    private fun showHowTo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.hint_title)
            .setMessage(R.string.hint_body)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun save() {
        state?.let { SaveGame.save(this, it) }
    }

    override fun onPause() {
        super.onPause()
        save()
        binding.gameView.playing = false
        music.stop()
    }

    override fun onResume() {
        super.onResume()
        if (state != null && binding.titleOverlay.visibility != View.VISIBLE) {
            binding.gameView.playing = true
            if (music.enabled) music.start()
        }
    }

    override fun onDestroy() {
        online?.disconnect()
        music.release()
        sound.release()
        super.onDestroy()
    }
}
