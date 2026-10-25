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

private class Shape(val d: String, val mirror: Boolean, val dx: Float = 0f)

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

// Narrower shoulders (arms sit 3 units further in), a narrower waist and wider hips.
private const val ARM_IN = 3f

private val FEMALE = Figure(
    front = linkedMapOf(
        "upperChest" to listOf(Shape("M98 67C90 65 84 66 79 70C78 74 78 77 79 80C85 79 91 79 98 81Z", true)),
        "chest" to listOf(Shape("M98 83C91 82 85 82 79 83C74 90 74 101 80 106C87 110 94 108 98 103Z", true)),
        "shoulders" to listOf(Shape(DELT_IN, true, ARM_IN)),
        "sideDelts" to listOf(Shape(DELT_OUT, true, ARM_IN)),
        "biceps" to listOf(Shape(UPPER_ARM, true, ARM_IN)),
        "forearms" to listOf(Shape(FOREARM, true, ARM_IN)),
        "abs" to listOf(Shape(rr(85, 110, 13, 13, 4) + rr(85, 126, 13, 13, 4) + rr(85, 142, 13, 13, 4), true)),
        "obliques" to listOf(Shape("M77 109C75 119 76 128 78 136C76 145 76 153 78 160L82 160C83 143 83 126 82 112C80 112 78 111 77 109Z", true)),
        "quads" to listOf(Shape("M78 166C66 188 66 228 77 256C84 260 93 259 97 255C101 232 101 196 98 168C91 165 84 164 78 166Z", true)),
        "calves" to listOf(Shape(CALF_F, true)),
    ),
    back = linkedMapOf(
        "traps" to listOf(Shape("M100 54C93 57 86 61 80 66C84 71 88 75 92 79C95 81 97 82 100 83C103 82 105 81 108 79C112 75 116 71 120 66C114 61 107 57 100 54Z", false)),
        "upperBack" to listOf(Shape("M82 70C85 75 89 79 93 82L99 85L99 104C92 99 86 93 81 85C81 80 81 75 82 70Z", true)),
        "lats" to listOf(Shape("M80 89C77 104 78 122 84 134C90 137 96 136 98 134L98 108C90 102 84 96 80 89Z", true)),
        "rearDelts" to listOf(Shape(DELT_IN, true, ARM_IN)),
        "sideDelts" to listOf(Shape(DELT_OUT, true, ARM_IN)),
        "triceps" to listOf(Shape(UPPER_ARM, true, ARM_IN)),
        "forearms" to listOf(Shape(FOREARM, true, ARM_IN)),
        "lowerBack" to listOf(Shape("M85 138C90 140 96 140 99 138L99 160C92 162 87 161 84 158C83 151 83 144 85 138Z", true)),
        "glutes" to listOf(Shape("M80 163C67 170 66 193 76 206C87 213 96 208 99 198L99 166C93 163 86 162 80 163Z", true)),
        "hamstrings" to listOf(Shape("M77 210C70 228 72 245 78 258C85 261 93 260 97 257C101 242 100 224 98 210C91 212 84 212 77 210Z", true)),
        "calves" to listOf(Shape(CALF_B, true)),
    ),
    outline = listOf(
        Shape("M100 14C108 14 114 21 114 30C114 40 108 46 100 46C92 46 86 40 86 30C86 21 92 14 100 14Z", false),
        // hair falling to the shoulders
        Shape("M100 9C88 9 81 18 82 31C82 42 80 52 76 58C82 60 87 57 89 52M100 9C112 9 119 18 118 31C118 42 120 52 124 58C118 60 113 57 111 52", false),
        Shape("M94 46C94 51 93 54 91 57M106 46C106 51 107 54 109 57", false),
        Shape("M91 57C84 60 74 62 65 67", true),
        Shape("M37 201C32 207 34 217 41 219C47 217 49 208 47 201", true, ARM_IN),
        Shape("M77 350C73 362 76 372 84 373C90 373 94 369 94 360L94 350", true),
    ),
)

private val MIRROR = Matrix().apply { translate(200f, 0f); scale(-1f, 1f) }

private fun build(sh: Shape): List<Path> = build(sh.d, sh.mirror, sh.dx)

private fun build(d: String, mirror: Boolean, dx: Float): List<Path> {
    val base = PathParser().parsePathString(d).toPath().apply { if (dx != 0f) transform(offsetMatrix(dx)) }
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
