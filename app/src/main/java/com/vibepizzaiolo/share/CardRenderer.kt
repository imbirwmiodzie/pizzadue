package com.vibepizzaiolo.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class Design(val label: String) { STAMP("Stamp"), SHEET("Bake sheet"), TICKET("Ticket") }

enum class Format(val w: Int, val h: Int, val label: String) {
    FEED(1080, 1350, "Post 4:5"),
    STORY(1080, 1920, "Story 9:16"),
    SQUARE(1080, 1080, "Square 1:1"),
}

/** The real numbers printed on a card. */
data class CardInfo(
    val pizzaNo: Int,
    val bakeMs: Long,
    val whenMs: Long,
    val hydration: Float,
    val salt: Float,
    val yeast: Float,
    val preheatMin: Int,
    val balls: Int,
    val ballWeight: Int,
)

/** Draws the share image: the user's photo plus the bake's numbers and the VibePizzaiolo logo. */
object CardRenderer {
    private const val FLAME = 0xFFFFB020.toInt()
    private const val INK = 0xFF1A1E23.toInt()
    private const val FG = 0xFFF3EFE6.toInt()
    private const val MUTED = 0xFF9AA3AD.toInt()
    private const val PAPER = 0xFFF6F1E7.toInt()
    private const val PAPER_MUTED = 0xFF6B5F4E.toInt()
    private const val PAPER_TEXT = 0xFF4A4237.toInt()
    private const val GOLD = 0xFFE9A825.toInt()
    private const val GOLD_DARK = 0xFF8A5A10.toInt()
    private const val SEAL_RED = 0xFFB71C1C.toInt()
    private const val IT_GREEN = 0xFF009246.toInt()
    private const val IT_WHITE = 0xFFF8F5EE.toInt()
    private const val IT_RED = 0xFFCE2B37.toInt()

    // the app icon: oven dome that is also a stopwatch (same paths as the launcher icon)
    private val logo: List<Path> by lazy {
        listOf(
            "M24,78L24,60A30,30 0,0 1,84,60L84,78Z M41,78L41,66A13,13 0,0 1,67,66L67,78Z M51,47A3,3 0,1 0,57,47A3,3 0,1 0,51,47Z M55.67,43.45L60.95,37.17L62.49,38.45L57.21,44.73Z",
            "M50,19L58,19A2,2 0,0 1,60,21L60,25L48,25L48,21A2,2 0,0 1,50,19Z M52,25L56,25L56,30.5L52,30.5Z",
            "M54,76C49.6,76 47.5,72.7 48.8,69.2C49.7,66.7 52.1,65.6 52.4,62C55,63.6 56,66.1 55.5,68.2C56.5,67.6 57.1,66.6 57.3,65.4C59.3,67.5 59.9,70.4 58.8,72.8C57.9,74.8 56.2,76 54,76Z",
            "M22,80L86,80A2,2 0,0 1,88,82A2,2 0,0 1,86,84L22,84A2,2 0,0 1,20,82A2,2 0,0 1,22,80Z",
        ).mapIndexed { i, d ->
            PathParser.createPathFromPathData(d).apply { if (i == 0) fillType = Path.FillType.EVEN_ODD }
        }
    }

    fun render(photo: Bitmap, design: Design, format: Format, info: CardInfo): Bitmap {
        val bmp = Bitmap.createBitmap(format.w, format.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when (design) {
            Design.STAMP -> stamp(c, photo, format, info)
            Design.SHEET -> sheet(c, photo, format, info)
            Design.TICKET -> ticket(c, photo, format, info)
        }
        return bmp
    }

    // ---------- designs ----------

    private fun stamp(c: Canvas, photo: Bitmap, f: Format, info: CardInfo) {
        val w = f.w.toFloat()
        val h = f.h.toFloat()
        val m = w * 0.05f
        val top = if (f == Format.STORY) 250f else m
        val bottom = if (f == Format.STORY) h - 250f else h - m
        cover(c, photo, RectF(0f, 0f, w, h))
        // shade so white text reads on any photo
        c.drawRect(0f, 0f, w, h * 0.25f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * 0.25f, 0x66000000, 0, Shader.TileMode.CLAMP) })
        c.drawRect(0f, h * 0.5f, w, h, Paint().apply { shader = LinearGradient(0f, h * 0.5f, 0f, h, 0, 0xC0000000.toInt(), Shader.TileMode.CLAMP) })

        brand(c, m, top, 76f, FG)
        text(c, "Pizza nº ${info.pizzaNo}", m, bottom - 250f, mono(34f, false), FG)
        text(c, "IN THE OVEN", m, bottom - 196f, mono(36f, true, 0.12f), FLAME)
        text(c, clock(info.bakeMs), m - 6f, bottom - 6f, mono(200f, true, -0.03f), FG)

        // Italian quality seal, bottom right
        val r = 175f
        qualitySeal(c, w - m - r * 0.95f, bottom - r * 1.4f, r, info)
    }

    /**
     * A gold rosette like the seals on Italian food labels: "FATTA A MANO" round the top, "con amore"
     * and "QUALITÀ SUPERIORE" on a red disc (hand-made with love, superior quality), the app's name
     * round the bottom, and ribbon tails in the Italian colours.
     */
    private fun qualitySeal(c: Canvas, cx: Float, cy: Float, r: Float, info: CardInfo) {
        c.save()
        c.rotate(-10f, cx, cy)

        // ribbon tails, behind the seal
        val tailW = r * 0.42f
        val tailH = r * 1.0f
        val tailTop = cy + r * 0.35f
        for (side in listOf(-1f, 1f)) {
            c.save()
            c.rotate(side * 20f, cx, tailTop)
            val left = cx + side * r * 0.3f - tailW / 2
            val tail = Path().apply {
                moveTo(left, tailTop)
                lineTo(left + tailW, tailTop)
                lineTo(left + tailW, tailTop + tailH)
                lineTo(left + tailW / 2, tailTop + tailH - tailW * 0.4f)
                lineTo(left, tailTop + tailH)
                close()
            }
            c.clipPath(tail)
            val stripe = tailW / 3
            listOf(IT_GREEN, IT_WHITE, IT_RED).forEachIndexed { i, col ->
                c.drawRect(left + i * stripe, tailTop, left + (i + 1) * stripe, tailTop + tailH, fill(col))
            }
            c.restore()
        }

        // scalloped gold edge
        c.drawCircle(cx + r * 0.03f, cy + r * 0.05f, r, fill(0x55000000)) // soft shadow
        val bumps = 30
        for (i in 0 until bumps) {
            val a = Math.PI * 2 * i / bumps
            c.drawCircle(cx + (r * 0.9f * Math.cos(a)).toFloat(), cy + (r * 0.9f * Math.sin(a)).toFloat(), r * 0.115f, fill(GOLD))
        }
        c.drawCircle(cx, cy, r * 0.92f, fill(GOLD))
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = GOLD_DARK }
        line.strokeWidth = r * 0.025f
        c.drawCircle(cx, cy, r * 0.86f, line)
        c.drawCircle(cx, cy, r * 0.6f, fill(SEAL_RED))
        line.strokeWidth = r * 0.03f
        c.drawCircle(cx, cy, r * 0.6f, line)

        // ring text: upright on top, readable from below at the bottom
        val ringPaint = mono(r * 0.15f, true, 0.14f).apply { color = INK }
        val top = Path().apply { addArc(RectF(cx - r * 0.66f, cy - r * 0.66f, cx + r * 0.66f, cy + r * 0.66f), 180f, 180f) }
        arcText(c, "FATTA A MANO", top, Math.PI.toFloat() * r * 0.66f, mono(r * 0.17f, true, 0.18f).apply { color = INK })
        val bottomArc = Path().apply { addArc(RectF(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f), 180f, -180f) }
        arcText(c, "VIBEPIZZAIOLO", bottomArc, Math.PI.toFloat() * r * 0.78f, ringPaint)
        val star = display(r * 0.14f).apply { color = INK; textAlign = Paint.Align.CENTER }
        c.drawText("★", cx - r * 0.73f, cy + r * 0.05f, star)
        c.drawText("★", cx + r * 0.73f, cy + r * 0.05f, star)

        // centre
        val center = Paint.Align.CENTER
        c.drawText("QUALITÀ", cx, cy - r * 0.02f, display(r * 0.22f).apply { color = IT_WHITE; textAlign = center; letterSpacing = 0.04f })
        c.drawText("SUPERIORE", cx, cy + r * 0.19f, display(r * 0.155f).apply { color = IT_WHITE; textAlign = center; letterSpacing = 0.06f })
        c.drawText("Nº ${info.pizzaNo}", cx, cy + r * 0.4f, mono(r * 0.12f, true).apply { color = GOLD; textAlign = center })
        c.drawText("con amore", cx, cy - r * 0.25f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            textSize = r * 0.13f
            color = GOLD
            textAlign = center
        })

        c.restore()
    }

    /** Text centred along an arc of [arcLength]. */
    private fun arcText(c: Canvas, s: String, path: Path, arcLength: Float, p: Paint) {
        p.textAlign = Paint.Align.LEFT
        val offset = ((arcLength - p.measureText(s)) / 2).coerceAtLeast(0f)
        c.drawTextOnPath(s, path, offset, 0f, p)
    }

    private fun sheet(c: Canvas, photo: Bitmap, f: Format, info: CardInfo) {
        val w = f.w.toFloat()
        val h = f.h.toFloat()
        val m = w * 0.05f
        val pad = if (f == Format.STORY) 250f else 0f
        val band = 330f
        c.drawColor(INK)
        val photoTop = if (f == Format.STORY) 250f else 0f
        cover(c, photo, RectF(0f, photoTop, w, h - band - pad))
        val y0 = h - band - pad

        drawLogo(c, m, y0 + 44f, 84f, FLAME)
        text(c, "VibePizzaiolo", m + 104f, y0 + 100f, display(60f), FG)
        text(c, "Pizza nº ${info.pizzaNo} · ${date(info.whenMs)}", m + 104f, y0 + 146f, mono(30f, false), MUTED)
        textRight(c, "SAY “VAI!”", w - m, y0 + 96f, mono(32f, true, 0.06f), FLAME)

        val stats = listOf("BAKE" to clock(info.bakeMs), "WATER" to pct(info.hydration), "SALT" to pct(info.salt), "YEAST" to pct(info.yeast))
        val colW = (w - 2 * m) / stats.size
        stats.forEachIndexed { i, (label, value) ->
            val x = m + i * colW
            text(c, label, x, y0 + 214f, mono(26f, true, 0.1f), MUTED)
            text(c, value, x, y0 + 286f, mono(66f, true), if (i == 0) FLAME else FG)
        }
    }

    private fun ticket(c: Canvas, photo: Bitmap, f: Format, info: CardInfo) {
        val w = f.w.toFloat()
        val h = f.h.toFloat()
        val p = w * 0.06f
        val top = if (f == Format.STORY) 250f else p
        val bottom = if (f == Format.STORY) h - 250f else h - p * 1.1f
        val th = 470f
        c.drawColor(INK)
        c.drawRect(0f, 0f, w, h, Paint().apply { shader = RadialGradient(w / 2, 0f, max(w, h) * 0.7f, 0xFF2A2219.toInt(), INK, Shader.TileMode.CLAMP) })

        val radius = 44f
        val photoRect = RectF(p, top, w - p, bottom - th)
        c.save()
        c.clipPath(Path().apply { addRoundRect(photoRect, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW) })
        cover(c, photo, photoRect)
        c.restore()

        val t = RectF(p, bottom - th, w - p, bottom)
        c.drawPath(Path().apply { addRoundRect(t, floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius), Path.Direction.CW) }, fill(PAPER))
        // perforated edge
        var x = t.left + 20f
        while (x < t.right - 10f) {
            c.drawCircle(x, t.top, 13f, fill(INK))
            x += 40f
        }
        val l = t.left + 50f
        val r = t.right - 50f
        text(c, "PIZZA Nº ${info.pizzaNo}", l, t.top + 74f, mono(30f, true, 0.08f), PAPER_MUTED)
        textRight(c, date(info.whenMs).uppercase(Locale.getDefault()), r, t.top + 74f, mono(30f, true, 0.04f), PAPER_MUTED)
        text(c, clock(info.bakeMs), l - 6f, t.top + 250f, mono(170f, true, -0.03f), INK)
        textRight(c, "say “vai!”", r, t.top + 210f, mono(32f, false), PAPER_MUTED)
        text(c, "oven ${info.preheatMin} min · ${info.balls} × ${info.ballWeight} g", l, t.top + 308f, mono(30f, false), PAPER_TEXT)
        text(c, "${pct(info.hydration)} water · ${pct(info.salt)} salt · ${pct(info.yeast)} yeast", l, t.top + 350f, mono(30f, false), PAPER_TEXT)
        c.drawLine(l, t.top + 382f, r, t.top + 382f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCFC5B3.toInt(); strokeWidth = 3f; pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        })
        drawLogo(c, l, t.top + 398f, 52f, INK)
        text(c, "VibePizzaiolo", l + 66f, t.top + 440f, display(40f), INK)
    }

    // ---------- helpers ----------

    /** Centre-crop the photo into [dst]. */
    private fun cover(c: Canvas, photo: Bitmap, dst: RectF) {
        val scale = max(dst.width() / photo.width, dst.height() / photo.height)
        val sw = (dst.width() / scale).roundToInt().coerceAtMost(photo.width)
        val sh = (dst.height() / scale).roundToInt().coerceAtMost(photo.height)
        val sx = (photo.width - sw) / 2
        val sy = (photo.height - sh) / 2
        c.drawBitmap(photo, Rect(sx, sy, sx + sw, sy + sh), dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun brand(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        drawLogo(c, x, y, size, FLAME)
        text(c, "VibePizzaiolo", x + size + 16f, y + size * 0.76f, display(size * 0.74f), color)
    }

    private fun drawLogo(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        c.save()
        c.translate(x, y)
        c.scale(size / 72f, size / 72f)
        c.translate(-18f, -16f)
        val p = fill(color)
        logo.forEach { c.drawPath(it, p) }
        c.restore()
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun mono(size: Float, bold: Boolean, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textSize = size
        letterSpacing = spacing
    }

    private fun display(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = size
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, p: Paint, color: Int) {
        p.color = color
        p.textAlign = Paint.Align.LEFT
        c.drawText(s, x, y, p)
    }

    private fun textRight(c: Canvas, s: String, x: Float, y: Float, p: Paint, color: Int) {
        p.color = color
        p.textAlign = Paint.Align.RIGHT
        c.drawText(s, x, y, p)
    }

    fun clock(ms: Long): String {
        val s = (ms + 500) / 1000
        return "%d:%02d".format(Locale.US, s / 60, s % 60)
    }

    fun pct(v: Float): String {
        val r = (v * 100).roundToInt() / 100.0
        return (if (r == Math.floor(r)) r.toLong().toString() else r.toString()) + "%"
    }

    private fun date(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
}
