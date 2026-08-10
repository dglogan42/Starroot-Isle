package com.starrootisle.app.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Procedural pixel-sprite atlas — original art, no external IP assets.
 * Drawn once at scale then blitted by GameView.
 */
object Sprites {
    private const val S = 16 // base pixel grid
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }

    lateinit var player: Bitmap
        private set
    lateinit var player2: Bitmap
        private set
    lateinit var puffRose: Bitmap
        private set
    lateinit var puffMint: Bitmap
        private set
    lateinit var puffSun: Bitmap
        private set
    lateinit var puffSky: Bitmap
        private set
    lateinit var tree: Bitmap
        private set
    lateinit var deepTree: Bitmap
        private set
    lateinit var starroot: Bitmap
        private set
    lateinit var rock: Bitmap
        private set
    lateinit var crystalRock: Bitmap
        private set
    lateinit var emberRock: Bitmap
        private set
    lateinit var tent: Bitmap
        private set
    lateinit var bench: Bitmap
        private set
    lateinit var fishSpot: Bitmap
        private set

    @Volatile
    private var ready = false

    fun ensure(scale: Int = 3) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val px = S * scale
            player = make(px) { c, p, s -> drawSettler(c, p, s, Color.parseColor("#FFB347"), Color.parseColor("#FFDAB9")) }
            player2 = make(px) { c, p, s -> drawSettler(c, p, s, Color.parseColor("#7FDBCA"), Color.parseColor("#FFE0D0")) }
            puffRose = make(px) { c, p, s -> drawPuff(c, p, s, Color.parseColor("#FF9BB0")) }
            puffMint = make(px) { c, p, s -> drawPuff(c, p, s, Color.parseColor("#7FDBCA")) }
            puffSun = make(px) { c, p, s -> drawPuff(c, p, s, Color.parseColor("#FFE066")) }
            puffSky = make(px) { c, p, s -> drawPuff(c, p, s, Color.parseColor("#74C0FC")) }
            tree = make(px) { c, p, s -> drawTree(c, p, s, Color.parseColor("#2E7D32"), Color.parseColor("#43A047")) }
            deepTree = make(px) { c, p, s -> drawTree(c, p, s, Color.parseColor("#1B4D2E"), Color.parseColor("#2E6B3C")) }
            starroot = make(px) { c, p, s -> drawStarroot(c, p, s) }
            rock = make(px) { c, p, s -> drawRock(c, p, s, Color.parseColor("#8A8A8A"), Color.parseColor("#7EC8E3")) }
            crystalRock = make(px) { c, p, s -> drawRock(c, p, s, Color.parseColor("#9BB7E0"), Color.parseColor("#E0F0FF")) }
            emberRock = make(px) { c, p, s -> drawRock(c, p, s, Color.parseColor("#6B3A2A"), Color.parseColor("#FF6B3A")) }
            tent = make(px) { c, p, s -> drawTent(c, p, s) }
            bench = make(px) { c, p, s -> drawBench(c, p, s) }
            fishSpot = make(px) { c, p, s -> drawFishSpot(c, p, s) }
            ready = true
        }
    }

    fun puffFor(color: PuffkinColor): Bitmap = when (color) {
        PuffkinColor.ROSE -> puffRose
        PuffkinColor.MINT -> puffMint
        PuffkinColor.SUN -> puffSun
        PuffkinColor.SKY -> puffSky
    }

    private fun make(size: Int, draw: (Canvas, Paint, Float) -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }
        draw(c, p, size.toFloat())
        return bmp
    }

    private fun px(s: Float, v: Int): Float = s * v / S

    private fun fill(c: Canvas, p: Paint, s: Float, color: Int, x: Int, y: Int, w: Int, h: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        c.drawRect(px(s, x), px(s, y), px(s, x + w), px(s, y + h), p)
    }

    private fun circ(c: Canvas, p: Paint, s: Float, color: Int, cx: Int, cy: Int, r: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        c.drawCircle(px(s, cx), px(s, cy), px(s, r), p)
    }

    private fun drawSettler(c: Canvas, p: Paint, s: Float, body: Int, skin: Int) {
        // shadow
        p.color = Color.argb(50, 0, 0, 0)
        c.drawOval(RectF(px(s, 3), px(s, 13), px(s, 13), px(s, 16)), p)
        fill(c, p, s, body, 5, 7, 6, 6)       // body
        circ(c, p, s, skin, 8, 5, 3)          // head
        fill(c, p, s, Color.parseColor("#3D2914"), 4, 3, 8, 2) // hat brim
        fill(c, p, s, Color.parseColor("#E07A5F"), 5, 1, 6, 3) // hat
        fill(c, p, s, Color.parseColor("#1A1A2E"), 6, 5, 1, 1) // eyes
        fill(c, p, s, Color.parseColor("#1A1A2E"), 9, 5, 1, 1)
        fill(c, p, s, Color.parseColor("#5A3A1A"), 5, 12, 2, 3) // legs
        fill(c, p, s, Color.parseColor("#5A3A1A"), 9, 12, 2, 3)
    }

    private fun drawPuff(c: Canvas, p: Paint, s: Float, body: Int) {
        p.color = Color.argb(40, 0, 0, 0)
        c.drawOval(RectF(px(s, 3), px(s, 12), px(s, 13), px(s, 15)), p)
        circ(c, p, s, body, 8, 8, 5)
        circ(c, p, s, body, 4, 5, 2) // ears
        circ(c, p, s, body, 12, 5, 2)
        fill(c, p, s, Color.parseColor("#1A1A2E"), 6, 7, 1, 1)
        fill(c, p, s, Color.parseColor("#1A1A2E"), 9, 7, 1, 1)
        fill(c, p, s, Color.WHITE, 10, 9, 1, 1) // freckle
        fill(c, p, s, Color.parseColor("#FFD56A"), 8, 3, 1, 1) // star tuft
    }

    private fun drawTree(c: Canvas, p: Paint, s: Float, dark: Int, light: Int) {
        fill(c, p, s, Color.parseColor("#8B5A2B"), 7, 9, 2, 6)
        circ(c, p, s, dark, 8, 6, 5)
        circ(c, p, s, light, 5, 7, 3)
        circ(c, p, s, light, 11, 7, 3)
    }

    private fun drawStarroot(c: Canvas, p: Paint, s: Float) {
        fill(c, p, s, Color.parseColor("#6B4C9A"), 7, 9, 2, 6)
        circ(c, p, s, Color.parseColor("#B8E0FF"), 8, 5, 5)
        fill(c, p, s, Color.WHITE, 6, 4, 1, 1)
        fill(c, p, s, Color.WHITE, 10, 6, 1, 1)
        fill(c, p, s, Color.parseColor("#E0F7FF"), 8, 3, 1, 1)
    }

    private fun drawRock(c: Canvas, p: Paint, s: Float, base: Int, fleck: Int) {
        p.color = base
        c.drawRoundRect(RectF(px(s, 2), px(s, 6), px(s, 14), px(s, 14)), px(s, 2), px(s, 2), p)
        circ(c, p, s, Color.argb(60, 255, 255, 255), 5, 8, 2)
        circ(c, p, s, fleck, 10, 9, 1)
        circ(c, p, s, fleck, 7, 11, 1)
    }

    private fun drawTent(c: Canvas, p: Paint, s: Float) {
        p.color = Color.parseColor("#E07A5F")
        val path = android.graphics.Path()
        path.moveTo(px(s, 8), px(s, 2))
        path.lineTo(px(s, 14), px(s, 13))
        path.lineTo(px(s, 2), px(s, 13))
        path.close()
        c.drawPath(path, p)
        fill(c, p, s, Color.parseColor("#3D2914"), 7, 9, 2, 4)
        fill(c, p, s, Color.parseColor("#FFD56A"), 8, 4, 1, 1)
    }

    private fun drawBench(c: Canvas, p: Paint, s: Float) {
        fill(c, p, s, Color.parseColor("#8B5A2B"), 2, 9, 12, 4)
        fill(c, p, s, Color.parseColor("#C49A6C"), 2, 7, 12, 2)
        fill(c, p, s, Color.parseColor("#5A3A1A"), 3, 13, 2, 2)
        fill(c, p, s, Color.parseColor("#5A3A1A"), 11, 13, 2, 2)
        circ(c, p, s, Color.parseColor("#FFD56A"), 8, 5, 2)
    }

    private fun drawFishSpot(c: Canvas, p: Paint, s: Float) {
        circ(c, p, s, Color.parseColor("#3A8FBF"), 8, 8, 5)
        circ(c, p, s, Color.argb(120, 200, 240, 255), 8, 8, 3)
        fill(c, p, s, Color.parseColor("#C0D8E8"), 5, 7, 3, 1)
        fill(c, p, s, Color.parseColor("#FFD56A"), 10, 9, 2, 1)
    }
}
