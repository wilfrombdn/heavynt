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

private class Figure(val front: Map<String, List<Shape>>, val back: Map<String, List<Shape>>, val outline: List<Shape>)

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
        "upperChest" to listOf(Shape("M98 66C88 64 78 65 73 69C71 73 71 77 72 81C80 79 90 79 98 81Z", true)),
        "chest" to listOf(Shape("M98 83C90 81 80 81 72.5 83C73 88 74 92 77 96C85 102 94 102 98 100Z", true)),
        "shoulders" to listOf(Shape(DELT_IN, true)),
        "sideDelts" to listOf(Shape(DELT_OUT, true)),
        "biceps" to listOf(Shape(UPPER_ARM, true)),
        "forearms" to listOf(Shape(FOREARM, true)),
        "abs" to listOf(Shape(ABS, true)),
        "obliques" to listOf(Shape("M72 103C69 121 72 141 80 159L82 159C83 141 83 123 82 107C78 107 75 106 72 103Z", true)),
        "quads" to listOf(Shape("M81 166C70 188 68 228 77 256C84 260 93 259 97 255C101 232 101 196 98 168C92 166 86 165 81 166Z", true)),
        "calves" to listOf(Shape(CALF_F, true)),
    ),
    back = linkedMapOf(
        "traps" to listOf(Shape("M100 52C92 56 84 60 76 66C80 71 85 76 90 80C94 82 97 83 100 84C103 83 106 82 110 80C115 76 120 71 124 66C116 60 108 56 100 52Z", false)),
        "upperBack" to listOf(Shape("M79 70C83 76 88 81 93 84L99 87L99 106C91 101 84 94 78 85C78 80 78 75 79 70Z", true)),
        "lats" to listOf(Shape("M76 89C72 106 74 125 82 137C90 139 96 137 98 135L98 110C89 104 81 97 76 89Z", true)),
        "rearDelts" to listOf(Shape(DELT_IN, true)),
        "sideDelts" to listOf(Shape(DELT_OUT, true)),
        "triceps" to listOf(Shape(UPPER_ARM, true)),
        "forearms" to listOf(Shape(FOREARM, true)),
        "lowerBack" to listOf(Shape(LOWER_BACK, true)),
        "glutes" to listOf(Shape("M82 166C72 172 70 190 78 203C88 209 96 205 99 197L99 168C94 166 88 165 82 166Z", true)),
        "hamstrings" to listOf(Shape("M79 208C72 226 73 244 78 258C85 261 93 260 97 257C101 242 100 224 98 208C92 210 85 210 79 208Z", true)),
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
        "upperChest" to listOf(Shape("M98 69C92 67 87 67 83 70C82 73 82 76 83 79C88 78 93 78 98 80Z", true)),
        "chest" to listOf(Shape("M98 84C92 82 85 83 81 87C78 94 79 102 85 106C90 109 95 107 98 102Z", true)),
        "shoulders" to listOf(Shape(F_DELT_IN, true)),
        "sideDelts" to listOf(Shape(F_DELT_OUT, true)),
        "biceps" to listOf(arm(UPPER_ARM)),
        "forearms" to listOf(arm(FOREARM)),
        "abs" to listOf(Shape("M98 113C94 113 91 114 90 117C89 128 89 142 91 151C93 155 96 156 98 155Z", true)),
        "obliques" to listOf(Shape("M82 110C81 118 83 126 85 132C84 142 80 151 76 159C80 160 84 160 88 158C87 145 87 128 88 114C86 113 84 112 82 110Z", true)),
        "quads" to listOf(Shape("M74 164C65 182 68 226 79 256C86 261 93 260 97 255C100 232 100 198 98 168C91 164 82 162 74 164Z", true)),
        "calves" to listOf(Shape("M78 265C73 286 77 318 84 345C87 347 91 347 93 345C98 320 99 288 95 265C89 268 83 268 78 265Z", true)),
    ),
    back = linkedMapOf(
        "traps" to listOf(Shape("M100 58C95 60 90 62 86 66C89 71 93 76 100 80C107 76 111 71 114 66C110 62 105 60 100 58Z", false)),
        "upperBack" to listOf(Shape("M87 71C89 75 93 79 99 82L99 100C94 96 90 91 87 85C86 80 86 75 87 71Z", true)),
        "lats" to listOf(Shape("M84 89C82 102 83 116 87 128C91 131 96 131 98 130L98 104C92 99 87 95 84 89Z", true)),
        "rearDelts" to listOf(Shape(F_DELT_IN, true)),
        "sideDelts" to listOf(Shape(F_DELT_OUT, true)),
        "triceps" to listOf(arm(UPPER_ARM)),
        "forearms" to listOf(arm(FOREARM)),
        "lowerBack" to listOf(Shape("M88 134C92 136 96 136 99 134L99 156C93 158 87 157 84 154C85 147 86 140 88 134Z", true)),
        "glutes" to listOf(Shape("M78 160C63 164 61 192 72 207C82 217 95 213 99 201L99 163C93 160 85 159 78 160Z", true)),
        "hamstrings" to listOf(Shape("M75 213C68 229 71 246 79 259C86 263 93 262 97 258C100 243 100 225 98 213C90 216 82 216 75 213Z", true)),
        "calves" to listOf(Shape("M78 265C72 285 76 317 84 343C87 345 91 345 93 343C99 320 99 288 95 265C89 268 83 268 78 265Z", true)),
    ),
    outline = listOf(
        Shape("M100 16C107 16 112 23 112 31C112 40 107 46 100 46C93 46 88 40 88 31C88 23 93 16 100 16Z", false),
        Shape("M95 45C95 50 94 54 92 57M105 45C105 50 106 54 108 57", false),
        Shape("M92 57C87 60 80 63 72 67", true),
        Shape("M37 201C32 207 34 217 41 219C47 217 49 208 47 201", true, ARM_IN, ARM_SLIM),
        Shape("M79 350C75 361 78 371 85 372C90 372 93 368 93 360L93 350", true),
    ),
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

private fun buildView(parts: Map<String, List<Shape>>, x: Float): List<Built> = parts.map { (key, shapes) ->
    val paths = shapes.flatMap(::build).map { p -> Path().apply { addPath(p); transform(offsetMatrix(x)) } }
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
    val outline = f.outline.flatMap(::build)
    val regions = buildView(f.front, 0f) + buildView(f.back, 220f)
}

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
        modifier.fillMaxWidth().aspectRatio(420f / 400f).pointerInput(female) {
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
                    val (r, g, bl) = scoreRGB(sc.score)
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
            nc.drawText("FRONT", 100f * s, 392f * s, labelPaint)
            nc.drawText("BACK", 320f * s, 392f * s, labelPaint)
        }
    }
}
