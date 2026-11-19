package com.vibepizzaiolo.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.vibepizzaiolo.avatar.Pizzaiolo
import com.vibepizzaiolo.share.CardRenderer
import kotlin.math.sin

/**
 * What is drawn over each shot of the reel, by style. Plain Canvas drawing, so the same code
 * makes the video's overlays, the cover picture and the tests' frames.
 *
 * Words stay inside the area Instagram and TikTok leave free: below their top bar (y > 250),
 * above their caption and buttons (y < 1480) and left of the right-hand buttons (x < 920).
 */
class ReelOverlays(private val info: ReelInfo, private val face: Bitmap?) {
    companion object {
        const val W = 1080
        const val H = 1920
        const val LABEL_Y = 1300f
        const val SAFE_TOP = 250f
        const val SAFE_BOTTOM = 1480f
        const val SAFE_RIGHT = 920f
        private const val FLAME = 0xFFFFB020.toInt()
        private const val INK = 0xFF1A1E23.toInt()
        private const val FG = 0xFFF3EFE6.toInt()
        private const val MUTED = 0xFFC9CED4.toInt()
        /** Overlays fade out over the reel's last moment, so the loop back to the crunch is clean. */
        const val LOOP_FADE_S = 0.3f
    }

    private val style get() = info.style
    private val card get() = info.card
    private val night get() = info.night

    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val black = Typeface.create("sans-serif-black", Typeface.BOLD)
    private val monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private fun paint(size: Float, color: Int, face: Typeface = bold, align: Paint.Align = Paint.Align.LEFT, shadow: Boolean = true) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face; textAlign = align
            if (shadow) setShadowLayer(10f, 0f, 3f, 0xCC000000.toInt())
        }

    private fun fade(t: Float, start: Float = 0f, len: Float = 0.3f) = ((t - start) / len).coerceIn(0f, 1f)
    private fun ease(k: Float) = 1 - (1 - k) * (1 - k)

    private val bake get() = CardRenderer.clock(card.bakeMs)

    /** The dough and the oven, as short chips: "70% idratazione", "48 h lievitazione", "450 °C", "1:32 in forno". */
    fun stats(): List<String> {
        val n = night
        if (n != null) return buildList {
            add("${n.pizzas} pizze")
            if (n.guests.isNotEmpty()) add("${n.guests.size} amici")
            if (info.ovenC > 0) add("${info.ovenC} °C")
        }
        return buildList {
            add("${CardRenderer.pct(card.hydration)} idratazione")
            if (info.fermentH > 0) add("${info.fermentH} h lievitazione")
            if (info.ovenC > 0) add("${info.ovenC} °C")
            add("$bake in forno")
        }
    }

    /** What the cartoon pizzaiolo says on each shot in the Funny style. */
    internal fun quip(kind: Kind, index: Int): String? = when (kind) {
        Kind.HOOK -> "Shhh… ascolta!"
        Kind.LAUNCH -> "Vai, vai!"
        Kind.BAKE -> null
        Kind.PULL -> "Pronta!"
        Kind.TOP -> if (night != null) "Che serata!" else "Guarda che bella!"
        Kind.AROUND -> if (index == 0) "Che cornicione!" else "Gira, gira!"
        Kind.RIM -> "Senti che crunch!"
        Kind.UNDER -> "Il sotto? Leopardato!"
        Kind.STAMP -> null
        Kind.SLICE -> "Filante!"
        Kind.ENDING -> null
    }

    /** The scene's title and its line of numbers in the Stats style. */
    internal fun caption(kind: Kind, index: Int): Pair<String, String?>? {
        val n = night
        return when (kind) {
            Kind.HOOK, Kind.BAKE, Kind.STAMP, Kind.ENDING -> null
            Kind.LAUNCH -> "IN FORNO" to (if (info.ovenC > 0) "${info.ovenC} °C" else "vai!")
            Kind.PULL -> "PRONTA" to "$bake in forno"
            Kind.TOP -> "DALL'ALTO" to null
            Kind.AROUND -> when {
                n != null && index == 0 && n.favourite != null -> "LA PIÙ AMATA" to "${n.favourite} ×${n.favouriteCount}"
                n != null -> "TUTTO INTORNO" to "${n.pizzas} pizze, una per una"
                index == 0 -> "TUTTO INTORNO" to "${CardRenderer.pct(card.hydration)} idratazione"
                else -> "TUTTO INTORNO" to (if (info.fermentH > 0) "${info.fermentH} h di lievitazione" else "fatta a mano")
            }
            Kind.RIM -> "IL CORNICIONE" to "croccante · $bake in forno"
            Kind.UNDER -> "IL SOTTO" to "leopardato"
            Kind.SLICE -> "LA FETTA" to "filante"
        }
    }

    /**
     * Draws [shot]'s overlay at [t] seconds into it. [last] is true for the reel's final shot,
     * whose overlays fade out at its end.
     */
    fun draw(c: Canvas, shot: Shot, t: Float, last: Boolean = false) {
        if (style == ReelStyle.CLEAN) return
        val lenS = shot.lengthMs / 1000f
        val out = if (last) ((lenS - t) / LOOP_FADE_S).coerceIn(0f, 1f) else 1f
        if (out <= 0f) return
        if (out < 1f) c.saveLayerAlpha(null, (255 * out).toInt())
        val endAt = shot.endingFromMs?.let { it / 1000f }
        val inEnding = endAt != null && t >= endAt
        when {
            shot.kind == Kind.BAKE -> bakeClock(c, shot)
            shot.kind == Kind.STAMP -> stamp(c, t)
            inEnding -> ending(c, t - endAt!!)
            else -> scene(c, shot, t, endAt)
        }
        if (out < 1f) c.restore()
    }

    private fun scene(c: Canvas, shot: Shot, t: Float, endAt: Float?) {
        // words leave a little before the ending comes in
        val away = if (endAt != null) 1 - fade(t, endAt - 0.25f, 0.25f) else 1f
        if (away <= 0f) return
        if (away < 1f) c.saveLayerAlpha(null, (255 * away).toInt())
        when (style) {
            ReelStyle.STATS -> {
                if (shot.kind == Kind.HOOK) hookTitle(c, t)
                else {
                    caption(shot.kind, shot.index)?.let { (title, sub) -> label(c, t, title, sub) }
                    if (shot.kind == Kind.TOP) chips(c, t, stats())
                }
            }
            ReelStyle.FUNNY -> quip(shot.kind, shot.index)?.let { chef(c, t, it) }
            ReelStyle.ASMR, ReelStyle.CLEAN -> Unit
        }
        if (away < 1f) c.restore()
    }

    // ---------------- pieces ----------------

    /** The first frame's words: what this is, and a reason to keep the sound on. */
    private fun hookTitle(c: Canvas, t: Float) {
        val k = ease(fade(t, 0f, 0.2f))
        val n = night
        val line1 = if (n != null) "SERATA PIZZA" else info.pizzaTitle
        val line2 = if (n != null) n.title else "ASCOLTA IL CRUNCH"
        c.saveLayerAlpha(null, (255 * k).toInt())
        val y = 420f + (1 - k) * 40f
        val p1 = paint(92f, Color.WHITE, black, Paint.Align.CENTER)
        fitText(p1, line1, 900f)
        c.drawText(line1, W / 2f, y, p1)
        val p2 = paint(46f, FLAME, monoBold, Paint.Align.CENTER)
        fitText(p2, line2, 900f)
        c.drawText(line2, W / 2f, y + 78f, p2)
        c.restore()
    }

    /** Shrinks [p] until [s] fits in [max] pixels. */
    private fun fitText(p: Paint, s: String, max: Float) {
        while (p.measureText(s) > max && p.textSize > 24f) p.textSize -= 2f
    }

    /** The scene's name, low on the screen, sliding in, with its number in the brand colour. */
    private fun label(c: Canvas, t: Float, title: String, sub: String?) {
        val k = ease(fade(t, 0.05f, 0.3f))
        if (k <= 0f) return
        val x = 64f - (1 - k) * 120f
        val y = LABEL_Y
        val titleP = paint(64f, Color.WHITE, bold, shadow = false)
        val subP = paint(40f, FLAME, monoBold)
        fitText(titleP, title, SAFE_RIGHT - 64f - 56f)
        if (sub != null) fitText(subP, sub, SAFE_RIGHT - 64f - 20f)
        val w = titleP.measureText(title)
        c.saveLayerAlpha(null, (255 * k).toInt())
        c.drawRoundRect(RectF(x - 28f, y - 82f, x + w + 28f, y + 30f), 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB0000000.toInt() })
        c.drawText(title, x, y, titleP)
        if (sub != null) c.drawText(sub, x, y + 82f, subP)
        c.restore()
    }

    /** The numbers as chips stacked above the label, one after another. */
    private fun chips(c: Canvas, t: Float, items: List<String>) {
        val p = paint(42f, INK, monoBold, shadow = false)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FLAME }
        var y = LABEL_Y - 150f - (items.size - 1) * 92f
        items.forEachIndexed { i, s ->
            val k = ease(fade(t, 0.25f + i * 0.12f, 0.25f))
            if (k > 0f) {
                fitText(p, s, SAFE_RIGHT - 120f)
                val w = p.measureText(s)
                val x = 64f - (1 - k) * 80f
                c.saveLayerAlpha(null, (255 * k).toInt())
                c.drawRoundRect(RectF(x - 6f, y - 56f, x + w + 34f, y + 18f), 37f, 37f, bg)
                c.drawText(s, x + 14f, y, p)
                c.restore()
            }
            y += 92f
        }
    }

    /** A white speech bubble centred on ([bx], [by]) with its tail towards ([tx], [ty]). */
    private fun bubble(c: Canvas, text: String, bx: Float, by: Float, tx: Float, ty: Float, size: Float = 54f) {
        val p = paint(size, INK, bold, Paint.Align.CENTER, shadow = false)
        fitText(p, text, SAFE_RIGHT - 120f)
        val bw = p.measureText(text) + 76f
        val bh = p.textSize * 2.1f
        val left = (bx - bw / 2).coerceIn(40f, SAFE_RIGHT - bw)
        val cx = left + bw / 2
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(16f, 0f, 4f, 0x88000000.toInt()) }
        val tail = Path().apply {
            moveTo(cx - bw * 0.12f, by + bh / 2 - 6f); lineTo(tx, ty); lineTo(cx + bw * 0.08f, by + bh / 2 - 6f); close()
        }
        c.drawPath(tail, fill)
        c.drawRoundRect(RectF(left, by - bh / 2, left + bw, by + bh / 2), bh / 2, bh / 2, fill)
        c.drawText(text, cx, by + p.textSize * 0.36f, p)
    }

    /** Funny style: the pizzaiolo peeks in at the bottom left and says [text]. */
    private fun chef(c: Canvas, t: Float, text: String) {
        val up = ease(fade(t, 0f, 0.3f))
        val size = 460f
        val bottom = H - 170f + (1 - up) * 420f
        val cx = 230f
        Pizzaiolo.draw(c, cx, bottom, size, t, face)
        val b = fade(t, 0.2f, 0.2f)
        if (b > 0f) {
            c.saveLayerAlpha(null, (255 * b).toInt())
            val headTop = bottom - size * 0.98f
            bubble(c, text, cx + 300f, headTop - 40f, cx + 90f, headTop + 30f)
            c.restore()
        }
    }

    /** The bake's time-lapse: the real time in the oven, big, with a bar filling up. */
    private fun bakeClock(c: Canvas, shot: Shot) {
        val i = shot.index
        val pieces = ReelPlanner.BAKE_PIECES
        val ms = card.bakeMs * (i + 1) / pieces
        val big = style != ReelStyle.ASMR
        val cx = W / 2f
        val y = if (big) 560f else 360f
        val p = paint(if (big) 170f else 70f, Color.WHITE, monoBold, Paint.Align.CENTER)
        c.drawText(CardRenderer.clock(ms), cx, y, p)
        val bw = if (big) 620f else 300f
        val bh = if (big) 22f else 10f
        val top = y + (if (big) 50f else 26f)
        c.drawRoundRect(RectF(cx - bw / 2, top, cx + bw / 2, top + bh), bh / 2, bh / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF })
        c.drawRoundRect(RectF(cx - bw / 2, top, cx - bw / 2 + bw * (i + 1) / pieces, top + bh), bh / 2, bh / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FLAME })
        if (big && style == ReelStyle.STATS && info.ovenC > 0) {
            c.drawText("${info.ovenC} °C", cx, top + 110f, paint(52f, FLAME, monoBold, Paint.Align.CENTER))
        }
    }

    /** The quality seal falls onto the pizza (on moving footage, slightly darkened) and lands with a bounce. */
    private fun stamp(c: Canvas, t: Float) {
        c.drawColor(Color.argb((95 * fade(t, 0f, 0.25f)).toInt(), 0, 0, 0))
        val hit = VideoMaker.STAMP_HIT_MS / 1000f
        val cx = W / 2f
        val cy = H * 0.44f
        val r = 280f
        val scale: Float
        val turn: Float
        val alpha: Float
        if (t < hit) {
            val e = (t / hit) * (t / hit)
            scale = 3.2f - 2.2f * e
            turn = -35f * (1 - e)
            alpha = 0.25f + 0.75f * (t / hit)
        } else {
            val u = ((t - hit) / 0.3f).coerceIn(0f, 1f)
            scale = 1f + 0.09f * sin(u * Math.PI.toFloat()) * (1 - u)
            turn = 0f
            alpha = 1f
        }
        if (t >= hit && t < hit + 0.55f) {
            val u = (t - hit) / 0.55f
            c.drawCircle(cx, cy, r * (1.02f + 0.9f * u), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 34f * (1 - u); color = Color.argb((190 * (1 - u)).toInt(), 255, 244, 220)
            })
        }
        c.saveLayerAlpha(null, (255 * alpha).toInt())
        c.scale(scale, scale, cx, cy)
        c.rotate(turn, cx, cy)
        CardRenderer.qualitySeal(c, cx, cy, r, card, info.sealText)
        c.restore()
        val k = fade(t, hit + 0.3f, 0.35f)
        if (k > 0f) {
            c.saveLayerAlpha(null, (255 * k).toInt())
            val line = night?.title ?: "${info.pizzaTitle} · $bake in forno"
            val p = paint(48f, Color.WHITE, monoBold, Paint.Align.CENTER)
            fitText(p, line, 880f)
            c.drawText(line, cx, cy + r * 1.72f, p)
            c.restore()
        }
    }

    /**
     * The ending, over the last of the footage: the pizzaiolo's handle (the reel is theirs),
     * the cartoon pizzaiolo calling the pizza ready, and a small app tag if wanted.
     */
    internal fun ending(c: Canvas, t: Float) {
        val dim = fade(t, 0f, 0.35f)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(0x99000000.toInt(), 0x22000000, 0xAA000000.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            alpha = (255 * dim).toInt()
        })
        val cx = W / 2f
        val k = ease(fade(t, 0.05f, 0.3f))
        c.saveLayerAlpha(null, (255 * k).toInt())
        var y = 420f
        val handle = info.handleText
        if (handle.isNotEmpty()) {
            val p = paint(84f, Color.WHITE, black, Paint.Align.CENTER)
            fitText(p, handle, 880f)
            c.drawText(handle, cx, y, p)
            y += 70f
        }
        val n = night
        val title = n?.title ?: info.pizzaTitle
        val tp = paint(if (handle.isEmpty()) 76f else 44f, if (handle.isEmpty()) Color.WHITE else FLAME, if (handle.isEmpty()) black else monoBold, Paint.Align.CENTER)
        fitText(tp, title, 880f)
        if (style != ReelStyle.ASMR || handle.isEmpty()) c.drawText(title, cx, y, tp)
        y += 60f
        if (info.appTag) {
            val tag = paint(32f, MUTED, bold, Paint.Align.CENTER)
            val s = "made with VibePizzaiolo"
            val w = tag.measureText(s)
            CardRenderer.drawLogo(c, cx - w / 2 - 48f, y - 34f, 40f, FLAME)
            c.drawText(s, cx + 6f, y, tag)
            y += 60f
        }
        if (n != null && n.guests.isNotEmpty() && style != ReelStyle.ASMR) {
            val gp = paint(40f, FG, bold, Paint.Align.CENTER)
            for (line in thanks(n.guests, gp, 880f)) { y += 56f; c.drawText(line, cx, y, gp) }
        }
        c.restore()
        if (style == ReelStyle.ASMR) return

        // the pizzaiolo slides up and waves; the bubble comes with the voice
        val up = ease(fade(t, 0.1f, 0.45f))
        val size = 640f
        val pcx = 300f
        val bottom = H - 230f + (1 - up) * 700f
        Pizzaiolo.draw(c, pcx, bottom, size, t, face)
        val b = fade(t, VideoMaker.VOICE_AT_MS / 1000f, 0.2f)
        if (b > 0f) {
            c.saveLayerAlpha(null, (255 * b).toInt())
            val headTop = bottom - size * 0.98f
            bubble(c, if (n != null) "Alla prossima!" else "La pizza è pronta!", 620f, headTop - 10f, pcx + 120f, headTop + 60f, 52f)
            c.restore()
        }
    }

    /** "Grazie a Anna, Luca, Marta e Paolo", wrapped onto at most three lines. */
    internal fun thanks(guests: List<String>, p: Paint, max: Float): List<String> {
        val names = guests.filter { it.isNotBlank() }
        if (names.isEmpty()) return emptyList()
        val shown = names.take(12)
        val words = buildList {
            add("Grazie a")
            shown.forEachIndexed { i, g ->
                val sep = when {
                    i == shown.size - 1 && names.size > shown.size -> ","
                    i == shown.size - 1 -> ""
                    i == shown.size - 2 && names.size == shown.size -> " e"
                    else -> ","
                }
                add(g + sep)
            }
            if (names.size > shown.size) add("e altri ${names.size - shown.size}")
        }
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            val next = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(next) > max && cur.isNotEmpty()) { lines += cur; cur = w } else cur = next
        }
        if (cur.isNotEmpty()) lines += cur
        return if (lines.size <= 3) lines else lines.take(3).mapIndexed { i, l -> if (i == 2) "$l…" else l }
    }

    /**
     * The reel's cover for the profile grid: the pizza from above with the seal (if any), the
     * pizza's title and the handle, kept inside the middle 4:5 that the grid shows.
     */
    fun cover(c: Canvas, frame: Bitmap) {
        val scale = maxOf(W.toFloat() / frame.width, H.toFloat() / frame.height)
        val sw = (W / scale).toInt().coerceAtMost(frame.width)
        val sh = (H / scale).toInt().coerceAtMost(frame.height)
        val sx = (frame.width - sw) / 2
        val sy = (frame.height - sh) / 2
        c.drawBitmap(frame, android.graphics.Rect(sx, sy, sx + sw, sy + sh), android.graphics.Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawColor(Color.argb(70, 0, 0, 0))
        val cx = W / 2f
        // the grid shows the middle 1080×1350: from y = 285 to 1635
        val titleP = paint(96f, Color.WHITE, black, Paint.Align.CENTER)
        val title = night?.let { "SERATA PIZZA" } ?: info.pizzaTitle
        fitText(titleP, title, 940f)
        c.drawText(title, cx, 460f, titleP)
        val sub = night?.title ?: "$bake in forno · ${CardRenderer.pct(card.hydration)}"
        val subP = paint(46f, FLAME, monoBold, Paint.Align.CENTER)
        fitText(subP, sub, 900f)
        c.drawText(sub, cx, 540f, subP)
        if (info.sealText != com.vibepizzaiolo.settings.SealText.NONE) {
            CardRenderer.qualitySeal(c, cx, H * 0.53f, 250f, card, info.sealText)
        }
        if (info.handleText.isNotEmpty()) {
            val hp = paint(64f, Color.WHITE, black, Paint.Align.CENTER)
            fitText(hp, info.handleText, 900f)
            c.drawText(info.handleText, cx, 1500f, hp)
        }
    }
}
