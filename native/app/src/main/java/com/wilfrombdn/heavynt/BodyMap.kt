package com.wilfrombdn.heavynt

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput

// Front/back anatomy figure drawn with real Canvas paths. Region shapes are SVG path data in a 200x400 box per view.

/** dx shifts the shape sideways; sx < 1 slims it around the arm's centre line. */
private class Shape(val d: String, val mirror: Boolean, val dx: Float = 0f, val sx: Float = 1f)

private const val ARM_CX = 50f

/** height < 1 draws the whole figure shorter, kept centred at the same middle line. */
private class Figure(val front: Map<String, List<Shape>>, val back: Map<String, List<Shape>>, val outline: List<Shape>, val height: Float = 1f)

/** Halfway between the top of the head (y 12) and the soles (y 373). */
private const val MID_Y = 192.5f

private fun rr(x: Int, y: Int, w: Int, h: Int, r: Int) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r $r" + "v${h - 2 * r}a$r $r 0 0 1 -$r $r" + "h-${w - 2 * r}a$r $r 0 0 1 -$r -$r" + "v-${h - 2 * r}a$r $r 0 0 1 $r -${r}z"

// Shoulder cap split in two: the inner half is the front delt (front view) or rear delt (back view), the outer half the side delt.
private const val DELT_IN = "M72 68C67 65 62 64 58 66C57 76 58 86 60 92C64 86 68 81 72 78C75 75 75 71 72 68Z"
private const val DELT_OUT = "M55 67C49 69 46 74 45 80C43 91 47 99 54 100C56 97 57 94 58 92C56 84 55 75 55 67Z"
private const val UPPER_ARM = "M47 101C41 115 40 129 43 142C48 145 54 145 58 142C62 128 62 113 58 102C54 104 50 104 47 101Z"
private const val FOREARM = "M43 147C38 163 36 181 36 198C41 202 47 201 52 198C56 181 58 163 58 147C53 149 48 149 43 147Z"
private const val CALF_F = "M78 264C74 286 76 320 82 346C86 348 91 348 94 346C99 320 100 286 96 264C90 267 84 267 78 264Z"
private const val CALF_B = "M78 264C72 284 74 318 82 344C86 346 91 346 94 344C100 320 100 286 96 264C90 267 84 267 78 264Z"
private const val LOWER_BACK = "M84 139C90 141 96 141 99 139L99 162C92 164 86 163 83 160C82 153 82 146 84 139Z"
private val ABS = rr(84, 105, 14, 14, 4) + rr(84, 122, 14, 14, 4) + rr(84, 139, 14, 14, 4)

private val MALE = Figure(
    front = linkedMapOf(
        "neck" to listOf(Shape("M93 47C93 51 92 54 90 57C93 59 96 60 99 60L99 48C97 48 95 48 93 47Z", true)),
        "hipFlexors" to listOf(Shape("M86 157C90 158 95 158 99 157L99 167C94 166 89 165 85 165C85 162 85 159 86 157Z", true)),
        "abductors" to listOf(Shape("M80 160C76 163 73 168 71 175C70 179 70 183 71 186C73 180 75 175 78 170C80 167 81 164 80 160Z", true)),
        "upperChest" to listOf(Shape("M98 66C88 64 78 65 73 69C71 73 71 77 72 81C80 79 90 79 98 81Z", true)),
        "chest" to listOf(Shape("M98 83C90 81 80 81 72.5 83C73 88 74 92 77 96C85 102 94 102 98 100Z", true)),
        "shoulders" to listOf(Shape(DELT_IN, true)),
        "sideDelts" to listOf(Shape(DELT_OUT, true)),
        "biceps" to listOf(Shape(UPPER_ARM, true)),
        "forearms" to listOf(Shape(FOREARM, true)),
        "abs" to listOf(Shape(ABS, true)),
        "obliques" to listOf(Shape("M72 103C69 121 72 141 80 159L82 159C83 141 83 123 82 107C78 107 75 106 72 103Z", true)),
        "quads" to listOf(Shape("M82 168C72 188 69 228 77 256C84 260 92 259 96 255C97 246 96 236 93 228C90 212 89 192 91 172C88 169 85 168 82 168Z", true)),
        "adductors" to listOf(Shape("M93 169C96 169 98 169 99 170L99 238C98 236 97 232 96 229C93 214 92 194 93 176C93 173 93 171 93 169Z", true)),
        "calves" to listOf(Shape("M89 266C92 266 94 265 96 264C100 286 99 320 94 346C92 347 90 347 89 347C88 320 88 290 89 266Z", true)),
        "tibialis" to listOf(Shape("M78 264C74 286 76 320 82 346C84 347 86 347 87 347C86 320 86 290 87 266C84 266 81 265 78 264Z", true)),
    ),
    back = linkedMapOf(
        "neck" to listOf(Shape("M93 46C93 50 94 53 95 55C97 54 98 53 100 52C102 53 103 54 105 55C106 53 107 50 107 46C104 47 96 47 93 46Z", false)),
        "abductors" to listOf(Shape("M80 152C82 156 83 160 83 165C80 166 77 166 75 165C75 160 77 155 80 152Z", true)),
        "traps" to listOf(Shape("M100 52C92 56 84 60 76 66C80 71 85 76 90 80C94 82 97 83 100 84C103 83 106 82 110 80C115 76 120 71 124 66C116 60 108 56 100 52Z", false)),
        "upperBack" to listOf(Shape("M79 70C83 76 88 81 93 84L99 87L99 106C91 101 84 94 78 85C78 80 78 75 79 70Z", true)),
        "lats" to listOf(Shape("M76 89C72 106 74 125 82 137C90 139 96 137 98 135L98 110C89 104 81 97 76 89Z", true)),
        "rearDelts" to listOf(Shape(DELT_IN, true)),
        "sideDelts" to listOf(Shape(DELT_OUT, true)),
        "triceps" to listOf(Shape(UPPER_ARM, true)),
        "forearms" to listOf(Shape(FOREARM, true)),
        "lowerBack" to listOf(Shape(LOWER_BACK, true)),
        "glutes" to listOf(Shape("M82 166C72 172 70 190 78 203C88 209 96 205 99 197L99 168C94 166 88 165 82 166Z", true)),
        "hamstrings" to listOf(Shape("M79 208C72 226 73 244 78 258C85 261 92 260 95 257C94 243 93 225 93 210C88 211 83 210 79 208Z", true)),
        "adductors" to listOf(Shape("M95 209C97 209 98 208 99 208L99 250C98 252 97 254 96 255C95 240 95 224 95 209Z", true)),
        "calves" to listOf(Shape(CALF_B, true)),
    ),
    outline = listOf(
        Shape("M100 12C109 12 115 20 115 29C115 40 108 47 100 47C92 47 85 40 85 29C85 20 91 12 100 12Z", false),
        Shape("M93 46C93 52 92 55 90 57M107 46C107 52 108 55 110 57", false),
        Shape("M91 57C82 60 70 62 60 67", true),
        Shape("M37 201C32 207 34 217 41 219C47 217 49 208 47 201", true),
        Shape("M77 350C73 362 76 372 84 373C90 373 94 369 94 360L94 350", true),
    ),
)

// Hourglass: narrow shoulders, slim arms, a small waist, wide hips and full thighs and glutes, drawn with soft curves.
private const val ARM_IN = 7f
private const val ARM_SLIM = 0.8f
private const val F_DELT_IN = "M80 70C75 67 70 66 66 68C65 76 66 84 68 89C72 84 76 80 80 77C82 75 82 72 80 70Z"
private const val F_DELT_OUT = "M63 69C58 71 55 76 55 81C54 90 57 96 62 97C64 94 65 91 66 89C64 82 63 76 63 69Z"
private fun arm(d: String) = Shape(d, true, ARM_IN, ARM_SLIM)

private val FEMALE = Figure(
    front = linkedMapOf(
        "neck" to listOf(Shape("M95 48C95 52 94 55 92 57C94 59 97 60 99 60L99 49C98 49 96 49 95 48Z", true)),
        "hipFlexors" to listOf(Shape("M88 158C92 159 96 159 99 158L99 167C94 166 90 165 86 165C86 162 87 160 88 158Z", true)),
        "abductors" to listOf(Shape("M75 161C70 165 66 172 65 180C64 184 65 188 66 191C67 184 70 177 74 171C76 167 76 164 75 161Z", true)),
        "upperChest" to listOf(Shape("M98 69C92 67 86 67 81 70C80 73 80 76 81 79C87 78 93 78 98 80Z", true)),
        "chest" to listOf(Shape("M98 83C90 80 82 81 78 87C73 95 75 105 83 109C90 112 95 109 98 104Z", true)),
        "shoulders" to listOf(Shape(F_DELT_IN, true)),
        "sideDelts" to listOf(Shape(F_DELT_OUT, true)),
        "biceps" to listOf(arm(UPPER_ARM)),
        "forearms" to listOf(arm(FOREARM)),
        "abs" to listOf(Shape("M98 113C94 113 91 114 90 117C89 128 89 142 91 151C93 155 96 156 98 155Z", true)),
        "obliques" to listOf(Shape("M83 111C82 119 84 126 86 132C85 142 80 151 74 159C79 160 84 160 88 158C87 145 87 128 88 115C86 114 84 113 83 111Z", true)),
        "quads" to listOf(Shape("M77 168C67 186 67 226 79 256C86 261 92 260 96 255C97 246 96 236 93 228C90 212 89 192 91 172C86 169 82 168 77 168Z", true)),
        "adductors" to listOf(Shape("M93 169C96 169 98 169 99 170L99 238C98 236 97 232 96 229C93 214 92 194 93 176C93 173 93 171 93 169Z", true)),
        "calves" to listOf(Shape("M89 267C91 267 93 266 95 265C99 288 98 320 93 345C92 346 90 346 89 346C88 320 88 292 89 267Z", true)),
        "tibialis" to listOf(Shape("M78 265C73 286 77 318 84 345C85 346 86 346 87 346C86 320 86 292 87 267C84 267 81 266 78 265Z", true)),
    ),
    back = linkedMapOf(
        "neck" to listOf(Shape("M95 48C95 51 96 54 97 56C98 55 99 55 100 55C101 55 102 55 103 56C104 54 105 51 105 48C102 49 98 49 95 48Z", false)),
        "abductors" to listOf(Shape("M81 147C83 151 84 155 84 159C80 160 76 160 73 160C74 155 77 150 81 147Z", true)),
        "traps" to listOf(Shape("M100 57C94 59 88 62 83 66C86 72 92 77 100 81C108 77 114 72 117 66C112 62 106 59 100 57Z", false)),
        "upperBack" to listOf(Shape("M84 70C86 75 91 79 99 83L99 101C92 97 87 92 84 85C83 80 83 75 84 70Z", true)),
        "lats" to listOf(Shape("M80 86C75 100 79 117 86 129C91 132 96 132 98 131L98 105C90 100 84 94 80 86Z", true)),
        "rearDelts" to listOf(Shape(F_DELT_IN, true)),
        "sideDelts" to listOf(Shape(F_DELT_OUT, true)),
        "triceps" to listOf(arm(UPPER_ARM)),
        "forearms" to listOf(arm(FOREARM)),
        "lowerBack" to listOf(Shape("M87 133C91 135 96 135 99 133L99 157C93 159 86 158 83 155C84 147 85 140 87 133Z", true)),
        "glutes" to listOf(Shape("M78 160C65 164 63 191 73 206C83 215 95 212 99 201L99 163C93 160 85 159 78 160Z", true)),
        "hamstrings" to listOf(Shape("M75 213C68 229 71 246 79 259C85 262 91 262 94 259C93 244 93 228 93 215C87 216 81 216 75 213Z", true)),
        "adductors" to listOf(Shape("M95 214C97 214 98 213 99 213L99 252C98 254 97 256 96 257C95 242 95 228 95 214Z", true)),
        "calves" to listOf(Shape("M78 265C72 285 76 317 84 343C87 345 91 345 93 343C99 320 99 288 95 265C89 268 83 268 78 265Z", true)),
    ),
    outline = listOf(
        Shape("M100 13C109 13 115 21 115 31C115 41 109 49 100 49C91 49 85 41 85 31C85 21 91 13 100 13Z", false),
        Shape("M95 48C95 52 94 55 92 57M105 48C105 52 106 55 108 57", false),
        Shape("M92 57C87 60 80 63 72 67", true),
        Shape("M37 201C32 207 34 217 41 219C47 217 49 208 47 201", true, ARM_IN, ARM_SLIM),
        Shape("M79 350C75 361 78 371 85 372C90 372 93 368 93 360L93 350", true),
    ),
    height = 0.92f,
)

private val MIRROR = Matrix().apply { translate(200f, 0f); scale(-1f, 1f) }

private fun build(sh: Shape): List<Path> {
    val (d, mirror, dx) = Triple(sh.d, sh.mirror, sh.dx)
    val base = PathParser().parsePathString(d).toPath().apply {
        if (sh.sx != 1f) transform(Matrix().apply { translate(ARM_CX * (1 - sh.sx), 0f); scale(sh.sx, 1f) })
        if (dx != 0f) transform(offsetMatrix(dx))
    }
    return if (mirror) listOf(base, Path().apply { addPath(base); transform(MIRROR) }) else listOf(base)
}

/** One drawable region: its paths in view space (x offset applied) plus a hit-test Region. */
private class Built(val key: String, val paths: List<Path>, val hit: Region)

private fun offsetMatrix(x: Float) = Matrix().apply { translate(x, 0f) }

private fun buildView(parts: Map<String, List<Shape>>, x: Float, squash: Matrix): List<Built> = parts.map { (key, shapes) ->
    val paths = shapes.flatMap(::build).map { p -> Path().apply { addPath(p); transform(squash); transform(offsetMatrix(x)) } }
    val region = Region()
    val clip = Region(0, 0, 420, 400)
    for (p in paths) {
        val r = Region()
        r.setPath(p.asAndroidPath(), clip)
        region.op(r, Region.Op.UNION)
    }
    Built(key, paths, region)
}

private class BuiltFigure(f: Figure) {
    /** A shorter figure is moved up to the same top margin and its box trimmed, so it sits centred like the full-height one. */
    private val lift = (MID_Y - 12f) * (1 - f.height)
    private val squash = Matrix().apply { translate(0f, MID_Y * (1 - f.height) - lift); scale(1f, f.height) }
    /** FRONT / BACK labels sit just under the feet. */
    val labelY = MID_Y + (392f - MID_Y) * f.height - lift
    val viewH = labelY + 8f
    val outline = f.outline.flatMap(::build).onEach { it.transform(squash) }
    val regions = buildView(f.front, 0f, squash) + buildView(f.back, 220f, squash)
}

/** Region keys drawn on a figure (front + back), for tests. */
internal fun drawnRegions(female: Boolean): Set<String> = (if (female) FEMALE else MALE).let { it.front.keys + it.back.keys }

private val MALE_BUILT by lazy { BuiltFigure(MALE) }
private val FEMALE_BUILT by lazy { BuiltFigure(FEMALE) }

@Composable
fun BodyMap(scores: Map<String, RegionScore>, selected: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier, female: Boolean = false) {
    // Gentle "breathing" glow, skipped when the user turned animations off in system settings.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val animate = remember {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val pulse = if (animate) rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.86f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe",
    ) else androidx.compose.runtime.rememberUpdatedState(1f)
    val fig = if (female) FEMALE_BUILT else MALE_BUILT
    val all = fig.regions

    Canvas(
        modifier.fillMaxWidth().aspectRatio(420f / fig.viewH).pointerInput(female) {
            detectTapGestures { pos ->
                val s = size.width / 420f
                val x = (pos.x / s).toInt()
                val y = (pos.y / s).toInt()
                all.firstOrNull { it.hit.contains(x, y) }?.let { onPick(it.key) }
            }
        },
    ) {
        val s = size.width / 420f
        val outlineStroke = Stroke(width = 1f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF666666.toInt(); textSize = 11f * s; letterSpacing = 0.2f; textAlign = Paint.Align.CENTER }

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save(); nc.scale(s, s)
            // outline-only parts for both views
            for (x in listOf(0f, 220f)) {
                for (p in fig.outline) {
                    val q = Path().apply { addPath(p); transform(offsetMatrix(x)) }
                    nc.drawPath(q.asAndroidPath(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF6A6A6A.toInt(); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
                    })
                }
            }
            for (b in all) {
                val sc = scores[b.key]
                val isSel = b.key == selected
                if (sc != null) {
                    val (r, g, bl) = scoreRGB(sc.score, female)
                    val k = scoreStrength(sc.score)
                    val breathe = pulse.value
                    fun argb(a: Float) = android.graphics.Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), r, g, bl)
                    // layered glow: wide translucent strokes that fade outwards (no blur filters, so it renders identically everywhere)
                    val layers = 8
                    for (i in layers - 1 downTo 0) {
                        val width = (2.2f + i * 2.4f) * (1f + 0.45f * k)
                        val alpha = 0.2f * (1f - i / layers.toFloat()) * (0.8f + 0.6f * k) * breathe
                        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE; strokeWidth = width; color = argb(alpha)
                            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
                        }
                        for (p in b.paths) nc.drawPath(p.asAndroidPath(), halo)
                    }
                    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = argb(0.3f + 0.28f * k) }
                    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = if (isSel) 2.4f else 1f; strokeJoin = Paint.Join.ROUND
                        color = if (isSel) android.graphics.Color.WHITE else argb(1f)
                    }
                    for (p in b.paths) { nc.drawPath(p.asAndroidPath(), fill); nc.drawPath(p.asAndroidPath(), edge) }
                } else {
                    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.argb(9, 255, 255, 255) }
                    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = if (isSel) 2.4f else 1f; strokeJoin = Paint.Join.ROUND
                        color = if (isSel) android.graphics.Color.WHITE else android.graphics.Color.argb(72, 255, 255, 255)
                    }
                    for (p in b.paths) { nc.drawPath(p.asAndroidPath(), fill); nc.drawPath(p.asAndroidPath(), edge) }
                }
            }
            nc.restore()
            nc.drawText("FRONT", 100f * s, fig.labelY * s, labelPaint)
            nc.drawText("BACK", 320f * s, fig.labelY * s, labelPaint)
        }
    }
}
