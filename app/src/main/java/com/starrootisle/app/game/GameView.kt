package com.starrootisle.app.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var state: GameState? = null
        set(value) {
            field = value
            invalidate()
        }

    var playing: Boolean = false
    var onHudTick: ((GameState) -> Unit)? = null
    var onAction: ((guest: Boolean) -> Unit)? = null

    private var lastNanos = 0L
    private val tilePx = 48f

    private var camX = 0f
    private var camY = 0f

    // P1 joystick (left)
    private var joy1Active = false
    private var joy1Cx = 0f
    private var joy1Cy = 0f
    private var joy1Kx = 0f
    private var joy1Ky = 0f
    private var joy1Id = -1
    private var move1X = 0f
    private var move1Y = 0f

    // P2 joystick (right, co-op)
    private var joy2Active = false
    private var joy2Cx = 0f
    private var joy2Cy = 0f
    private var joy2Kx = 0f
    private var joy2Ky = 0f
    private var joy2Id = -1
    private var move2X = 0f
    private var move2Y = 0f

    private var act1Id = -1
    private var act2Id = -1
    private val act1Rect = RectF()
    private val act2Rect = RectF()
    private var act1Pressed = false
    private var act2Pressed = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }

    private val loop = object : Runnable {
        override fun run() {
            if (!playing) {
                invalidate()
                postOnAnimation(this)
                return
            }
            val now = System.nanoTime()
            if (lastNanos == 0L) lastNanos = now
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = now
            state?.let { s ->
                s.update(dt, move1X, move1Y, move2X, move2Y)
                val targetX = s.player.x * tilePx - width / 2f
                val targetY = s.player.y * tilePx - height / 2f
                // Slight bias toward midpoint if co-op
                if (s.coopEnabled && s.player2 != null) {
                    val midX = (s.player.x + s.player2!!.x) * 0.5f * tilePx - width / 2f
                    val midY = (s.player.y + s.player2!!.y) * 0.5f * tilePx - height / 2f
                    camX += (midX - camX) * min(1f, dt * 6f)
                    camY += (midY - camY) * min(1f, dt * 6f)
                } else {
                    camX += (targetX - camX) * min(1f, dt * 8f)
                    camY += (targetY - camY) * min(1f, dt * 8f)
                }
                onHudTick?.invoke(s)
            }
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Sprites.ensure(3)
        lastNanos = 0L
        postOnAnimation(loop)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(loop)
        super.onDetachedFromWindow()
    }

    fun centerCamera() {
        state?.let { s ->
            camX = s.player.x * tilePx - width / 2f
            camY = s.player.y * tilePx - height / 2f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = resources.displayMetrics.density
        joy1Cx = 100f * d
        joy1Cy = h - 150f * d
        joy1Kx = joy1Cx
        joy1Ky = joy1Cy
        joy2Cx = w - 100f * d
        joy2Cy = h - 150f * d
        joy2Kx = joy2Cx
        joy2Ky = joy2Cy
        val ar = 52f * d
        act1Rect.set(w * 0.5f - ar * 1.2f, h - ar * 3.4f, w * 0.5f - ar * 0.1f, h - ar * 1.6f)
        act2Rect.set(w * 0.5f + ar * 0.1f, h - ar * 3.4f, w * 0.5f + ar * 1.2f, h - ar * 1.6f)
        // Solo: single ACT on right
        if (state?.coopEnabled != true) {
            act1Rect.set(w - ar * 2.3f, h - ar * 3.5f, w - ar * 0.45f, h - ar * 1.65f)
        }
        centerCamera()
    }

    private fun layoutControls() {
        val w = width
        val h = height
        if (w == 0) return
        val d = resources.displayMetrics.density
        val ar = 52f * d
        if (state?.coopEnabled == true) {
            act1Rect.set(w * 0.42f - ar, h - ar * 3.4f, w * 0.42f, h - ar * 1.6f)
            act2Rect.set(w * 0.58f, h - ar * 3.4f, w * 0.58f + ar, h - ar * 1.6f)
        } else {
            act1Rect.set(w - ar * 2.3f, h - ar * 3.5f, w - ar * 0.45f, h - ar * 1.65f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!playing) return false
        layoutControls()
        val dens = resources.displayMetrics.density
        val joyR = 70f * dens
        val coop = state?.coopEnabled == true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val x = event.getX(i)
                val y = event.getY(i)
                when {
                    act1Rect.contains(x, y) && act1Id < 0 -> {
                        act1Id = id
                        act1Pressed = true
                        onAction?.invoke(false) ?: state?.performAction(false)
                    }
                    coop && act2Rect.contains(x, y) && act2Id < 0 -> {
                        act2Id = id
                        act2Pressed = true
                        onAction?.invoke(true) ?: state?.performAction(true)
                    }
                    x < width * 0.45f && joy1Id < 0 -> {
                        joy1Id = id
                        joy1Active = true
                        joy1Cx = x; joy1Cy = y
                        joy1Kx = x; joy1Ky = y
                        updateJoy(1, joyR)
                    }
                    coop && x > width * 0.55f && joy2Id < 0 -> {
                        joy2Id = id
                        joy2Active = true
                        joy2Cx = x; joy2Cy = y
                        joy2Kx = x; joy2Ky = y
                        updateJoy(2, joyR)
                    }
                    !coop && x >= width * 0.45f && joy1Id < 0 && !act1Rect.contains(x, y) -> {
                        // allow right-side drag as alt for p1 when solo? skip
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    if (id == joy1Id) {
                        clampKnob(1, x, y, joyR)
                        updateJoy(1, joyR)
                    }
                    if (id == joy2Id) {
                        clampKnob(2, x, y, joyR)
                        updateJoy(2, joyR)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = event.getPointerId(event.actionIndex)
                if (id == joy1Id) {
                    joy1Id = -1; joy1Active = false
                    move1X = 0f; move1Y = 0f
                    joy1Kx = joy1Cx; joy1Ky = joy1Cy
                }
                if (id == joy2Id) {
                    joy2Id = -1; joy2Active = false
                    move2X = 0f; move2Y = 0f
                    joy2Kx = joy2Cx; joy2Ky = joy2Cy
                }
                if (id == act1Id) {
                    act1Id = -1; act1Pressed = false
                }
                if (id == act2Id) {
                    act2Id = -1; act2Pressed = false
                }
            }
        }
        return true
    }

    private fun clampKnob(which: Int, x: Float, y: Float, joyR: Float) {
        val cx = if (which == 1) joy1Cx else joy2Cx
        val cy = if (which == 1) joy1Cy else joy2Cy
        val dx = x - cx
        val dy = y - cy
        val d = hypot(dx, dy)
        val kx: Float
        val ky: Float
        if (d > joyR) {
            kx = cx + dx / d * joyR
            ky = cy + dy / d * joyR
        } else {
            kx = x; ky = y
        }
        if (which == 1) {
            joy1Kx = kx; joy1Ky = ky
        } else {
            joy2Kx = kx; joy2Ky = ky
        }
    }

    private fun updateJoy(which: Int, joyR: Float) {
        val dx = if (which == 1) (joy1Kx - joy1Cx) / joyR else (joy2Kx - joy2Cx) / joyR
        val dy = if (which == 1) (joy1Ky - joy1Cy) / joyR else (joy2Ky - joy2Cy) / joyR
        val mag = hypot(dx, dy)
        val mx = if (mag < 0.15f) 0f else dx
        val my = if (mag < 0.15f) 0f else dy
        if (which == 1) {
            move1X = mx; move1Y = my
        } else {
            move2X = mx; move2Y = my
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Sprites.ensure(3)
        val s = state
        if (s == null) {
            canvas.drawColor(Color.parseColor("#1A1A3E"))
            return
        }
        drawSky(canvas, s.timeOfDay)
        drawWorld(canvas, s)
        if (playing) drawControls(canvas, s.coopEnabled)
    }

    private fun drawSky(canvas: Canvas, tod: Float) {
        val day = when {
            tod < 0.2f || tod > 0.85f -> 0f
            tod < 0.35f -> (tod - 0.2f) / 0.15f
            tod < 0.7f -> 1f
            else -> 1f - (tod - 0.7f) / 0.15f
        }.coerceIn(0f, 1f)
        val r = (26 + (135 - 26) * day).toInt()
        val g = (26 + (206 - 26) * day).toInt()
        val b = (62 + (235 - 62) * day).toInt()
        canvas.drawColor(Color.rgb(r, g, b))
        paint.style = Paint.Style.FILL
        if (day > 0.3f) {
            paint.color = Color.argb((200 * day).toInt(), 255, 230, 120)
            canvas.drawCircle(width * 0.75f, height * 0.12f, 36f, paint)
        } else {
            paint.color = Color.argb(180, 230, 230, 255)
            canvas.drawCircle(width * 0.2f, height * 0.1f, 22f, paint)
            // stars
            paint.color = Color.argb(160, 255, 255, 255)
            for (i in 0 until 12) {
                val sx = ((i * 97) % width).toFloat()
                val sy = ((i * 53) % (height / 3)).toFloat()
                canvas.drawCircle(sx, sy, 1.5f, paint)
            }
        }
    }

    private fun drawWorld(canvas: Canvas, s: GameState) {
        val world = s.world
        val startTx = floor(camX / tilePx).toInt() - 1
        val startTy = floor(camY / tilePx).toInt() - 1
        val endTx = startTx + (width / tilePx).toInt() + 3
        val endTy = startTy + (height / tilePx).toInt() + 3

        for (ty in startTy..endTy) {
            for (tx in startTx..endTx) {
                val tile = world.get(tx, ty) ?: continue
                val left = tx * tilePx - camX
                val top = ty * tilePx - camY
                drawTile(canvas, tile, left, top, tx, ty)
            }
        }

        for (p in world.puffkins) {
            val px = p.x * tilePx - camX
            val py = p.y * tilePx - camY + sin(p.bobPhase) * 3f
            val bmp = Sprites.puffFor(p.color)
            canvas.drawBitmap(bmp, px - bmp.width / 2f, py - bmp.height / 2f, bmpPaint)
            if (!p.befriended && p.trust > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.argb(180, 255, 213, 106)
                canvas.drawArc(px - 18f, py - 18f, px + 18f, py + 18f, -90f, 360f * p.trust, false, paint)
                paint.style = Paint.Style.FILL
            } else if (p.befriended) {
                paint.color = Color.parseColor("#FFD56A")
                canvas.drawCircle(px, py - 20f, 3f, paint)
            }
        }

        drawActor(canvas, s.player, Sprites.player)
        if (s.coopEnabled) s.player2?.let { drawActor(canvas, it, Sprites.player2) }

        // Online visitors
        for (peer in s.remotePeers) {
            val px = peer.x * tilePx - camX
            val py = peer.y * tilePx - camY
            val spr = if (peer.color % 2 == 0) Sprites.player2 else Sprites.player
            canvas.drawBitmap(spr, px - spr.width / 2f, py - spr.height * 0.7f, bmpPaint)
            textPaint.textSize = 11f * resources.displayMetrics.density
            textPaint.color = Color.parseColor("#FFD56A")
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(peer.name, px, py - spr.height * 0.75f - 4f, textPaint)
            paint.color = Color.argb(160, 255, 213, 106)
            paint.strokeWidth = 2.5f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(px, py - 4f, px + peer.fx * 14f, py + peer.fy * 10f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawActor(canvas: Canvas, p: Player, sprite: android.graphics.Bitmap) {
        val px = p.x * tilePx - camX
        val py = p.y * tilePx - camY
        val bob = if (hypot(p.facingX, p.facingY) > 0.1f) sin(p.animPhase) * 2f else 0f
        canvas.drawBitmap(sprite, px - sprite.width / 2f, py - sprite.height * 0.7f + bob, bmpPaint)
        paint.color = Color.parseColor("#FFD56A")
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(px, py - 4f, px + p.facingX * 16f, py + p.facingY * 10f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun grassColor(biome: Biome, tx: Int, ty: Int): Int {
        val base = when (biome) {
            Biome.MEADOW -> if ((tx + ty) % 2 == 0) 0xFF5DAD4A.toInt() else 0xFF54A344.toInt()
            Biome.DEEPWOOD -> if ((tx + ty) % 2 == 0) 0xFF3D6B3A.toInt() else 0xFF355F34.toInt()
            Biome.CRYSTAL -> if ((tx + ty) % 2 == 0) 0xFF6B8FBF.toInt() else 0xFF5F84B5.toInt()
            Biome.EMBER -> if ((tx + ty) % 2 == 0) 0xFFB86B3A.toInt() else 0xFFA85F32.toInt()
            Biome.SHORE -> 0xFFE8D5A3.toInt()
            Biome.OCEAN -> 0xFF246B9A.toInt()
        }
        return base
    }

    private fun drawTile(canvas: Canvas, tile: Tile, left: Float, top: Float, tx: Int, ty: Int) {
        val r = RectF(left, top, left + tilePx, top + tilePx)
        paint.style = Paint.Style.FILL
        paint.shader = null

        paint.color = when (tile.type) {
            TileType.GRASS -> grassColor(tile.biome, tx, ty)
            TileType.DIRT -> Color.parseColor("#8B6914")
            TileType.SOIL -> Color.parseColor("#6B4423")
            TileType.SAND -> Color.parseColor("#E8D5A3")
            TileType.ASH -> Color.parseColor("#5A4A42")
            TileType.CRYSTAL_FLOOR -> Color.parseColor("#7A9EC8")
            TileType.WATER -> Color.parseColor("#3A8FBF")
            TileType.WATER_DEEP -> Color.parseColor("#246B9A")
            TileType.STONE -> Color.parseColor("#7A7A7A")
            TileType.PATH -> Color.parseColor("#C2A66A")
            TileType.FISHING_SPOT -> Color.parseColor("#E8D5A3")
            TileType.TENT, TileType.WORKBENCH -> Color.parseColor("#C2A66A")
            TileType.ROCK, TileType.CRYSTAL_ROCK, TileType.EMBER_ROCK,
            TileType.TREE, TileType.DEEPWOOD_TREE, TileType.STARROOT -> grassColor(tile.biome, tx, ty)
            TileType.FENCE, TileType.SIGN -> grassColor(tile.biome, tx, ty)
        }
        canvas.drawRect(r, paint)

        if (tile.type == TileType.WATER || tile.type == TileType.WATER_DEEP) {
            paint.color = Color.argb(40, 255, 255, 255)
            canvas.drawCircle(left + tilePx * 0.3f, top + tilePx * 0.4f, 6f, paint)
        }
        if (tile.type == TileType.CRYSTAL_FLOOR) {
            paint.color = Color.argb(50, 255, 255, 255)
            canvas.drawCircle(left + 12f, top + 14f, 3f, paint)
        }
        if (tile.type == TileType.ASH) {
            paint.color = Color.argb(60, 255, 100, 40)
            canvas.drawCircle(left + 20f, top + 22f, 2f, paint)
        }

        tile.crop?.let { crop ->
            val growth = crop.stage.toFloat() / crop.kind.maxStage
            val stemH = 8f + growth * 22f
            val col = when (crop.kind) {
                CropKind.EMBER_PEPPER -> if (crop.ready) 0xFFFF6B3A.toInt() else 0xFF90EE90.toInt()
                CropKind.CRYSTAL_LETTUCE -> if (crop.ready) 0xFFB8E0FF.toInt() else 0xFF90EE90.toInt()
                else -> if (crop.ready) 0xFFFFD700.toInt() else 0xFF90EE90.toInt()
            }
            paint.color = col
            canvas.drawRect(
                left + tilePx * 0.4f, top + tilePx - 10f - stemH,
                left + tilePx * 0.6f, top + tilePx - 8f, paint
            )
            canvas.drawCircle(left + tilePx * 0.5f, top + tilePx - 10f - stemH, 6f + growth * 5f, paint)
            if (crop.watered) {
                paint.color = Color.argb(100, 80, 160, 255)
                canvas.drawCircle(left + tilePx * 0.75f, top + tilePx * 0.75f, 4f, paint)
            }
        }

        val sprite: android.graphics.Bitmap? = when (tile.type) {
            TileType.ROCK -> Sprites.rock
            TileType.CRYSTAL_ROCK -> Sprites.crystalRock
            TileType.EMBER_ROCK -> Sprites.emberRock
            TileType.TREE -> Sprites.tree
            TileType.DEEPWOOD_TREE -> Sprites.deepTree
            TileType.STARROOT -> Sprites.starroot
            TileType.TENT -> Sprites.tent
            TileType.WORKBENCH -> Sprites.bench
            TileType.FISHING_SPOT -> Sprites.fishSpot
            else -> null
        }
        sprite?.let {
            canvas.drawBitmap(it, left + (tilePx - it.width) / 2f, top + (tilePx - it.height) / 2f, bmpPaint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(16, 0, 0, 0)
        canvas.drawRect(r, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawControls(canvas: Canvas, coop: Boolean) {
        layoutControls()
        val dens = resources.displayMetrics.density
        val joyR = 70f * dens

        fun stick(cx: Float, cy: Float, kx: Float, ky: Float, label: String) {
            paint.color = Color.argb(70, 255, 255, 255)
            canvas.drawCircle(cx, cy, joyR, paint)
            paint.color = Color.argb(130, 255, 255, 255)
            canvas.drawCircle(kx, ky, joyR * 0.36f, paint)
            textPaint.textSize = 11f * dens
            textPaint.color = Color.argb(180, 255, 255, 255)
            canvas.drawText(label, cx, cy - joyR - 6f, textPaint)
        }

        stick(joy1Cx, joy1Cy, joy1Kx, joy1Ky, "P1")
        if (coop) stick(joy2Cx, joy2Cy, joy2Kx, joy2Ky, "P2")

        fun actBtn(rect: RectF, pressed: Boolean, label: String) {
            paint.color = if (pressed) Color.argb(210, 255, 213, 106) else Color.argb(160, 255, 213, 106)
            canvas.drawRoundRect(rect, 18f, 18f, paint)
            textPaint.textSize = 13f * dens
            textPaint.color = Color.parseColor("#1A1A2E")
            val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(label, rect.centerX(), cy, textPaint)
        }
        actBtn(act1Rect, act1Pressed, if (coop) "P1" else "ACT")
        if (coop) actBtn(act2Rect, act2Pressed, "P2")
    }
}
