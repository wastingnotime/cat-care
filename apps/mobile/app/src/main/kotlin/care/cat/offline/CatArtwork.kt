package care.cat.offline

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

val Paper = Color(0xfff8f7f2)
val Ink = Color(0xff243c32)
val Forest = Color(0xff285345)
val Muted = Color(0xff5c6b60)
val Sage = Color(0xffe9eee4)
val Line = Color(0xffdfe5da)
val Rust = Color(0xff954c38)

@Composable
fun CatAvatar(coatId: String, modifier: Modifier = Modifier, describe: Boolean = true) {
    val coat = CatCoat.fromId(coatId)
    val description = tr(R.string.coat_description, tr(coat.label))
    Canvas(if (describe) modifier.semantics { contentDescription = description } else modifier) {
        val factor = size.minDimension / 100f
        withTransform({
            translate((size.width - 100 * factor) / 2, (size.height - 100 * factor) / 2)
            scale(factor, factor, Offset.Zero)
        }) {
            oval(Sage, 0f, 0f, 100f, 100f)
            val ears = if (coat == CatCoat.SIAMESE) Color(0xff594c48) else Color(coat.color)
            drawPath(
                Path().apply {
                    moveTo(16f, 43f)
                    lineTo(16f, 12f)
                    lineTo(42f, 46f)
                    close()
                },
                ears,
            )
            drawPath(
                Path().apply {
                    moveTo(58f, 43f)
                    lineTo(84f, 12f)
                    lineTo(86f, 46f)
                    close()
                },
                ears,
            )
            oval(Color(coat.color), 12f, 25f, 88f, 91f)
            when (coat) {
                CatCoat.CALICO -> {
                    oval(Color(0xff303534), 16f, 28f, 45f, 56f)
                    oval(Color(0xffd78338), 57f, 30f, 85f, 66f)
                }
                CatCoat.ORANGE_TABBY,
                CatCoat.BROWN_TABBY -> {
                    val stripe =
                        if (coat == CatCoat.ORANGE_TABBY) Color(0xff9e5e26) else Color(0xff4f4338)
                    drawLine(stripe, Offset(39f, 31f), Offset(44f, 44f), 4f, StrokeCap.Round)
                    drawLine(stripe, Offset(50f, 29f), Offset(50f, 43f), 4f, StrokeCap.Round)
                    drawLine(stripe, Offset(61f, 31f), Offset(56f, 44f), 4f, StrokeCap.Round)
                }
                CatCoat.TUXEDO -> oval(Color(0xfff4f0e6), 32f, 60f, 68f, 88f)
                CatCoat.SIAMESE -> oval(Color(0xff594c48), 29f, 52f, 71f, 89f)
                else -> Unit
            }
            val eyes =
                when (coat) {
                    CatCoat.BLACK,
                    CatCoat.TUXEDO -> Color(0xffd9c36b)
                    CatCoat.SIAMESE -> Color(0xff4c91a3)
                    else -> Ink
                }
            oval(eyes, 29f, 54f, 37f, 62f)
            oval(eyes, 63f, 54f, 71f, 62f)
            oval(Color(0xffc48b8b), 45f, 66f, 55f, 72f)
            val mouth = if (coat == CatCoat.BLACK) Color(0xffd8d0bd) else Ink
            drawLine(mouth, Offset(50f, 72f), Offset(50f, 77f), 2f)
            drawLine(mouth, Offset(50f, 77f), Offset(44f, 80f), 2f)
            drawLine(mouth, Offset(50f, 77f), Offset(56f, 80f), 2f)
        }
    }
}

@Composable
fun CatIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val factor = minOf(size.width / 260f, size.height / 180f)
        withTransform({
            translate((size.width - 260 * factor) / 2, (size.height - 180 * factor) / 2)
            scale(factor, factor, Offset.Zero)
        }) {
            oval(Color(0xffdce7d7), 37f, 12f, 220f, 174f)
            oval(Color(0xffc6d6c1), 38f, 143f, 224f, 165f)
            oval(Color(0xffd6ab80), 87f, 65f, 221f, 155f)
            drawPath(
                Path().apply {
                    moveTo(199f, 111f)
                    cubicTo(248f, 149f, 186f, 174f, 143f, 147f)
                },
                Color(0xffb78054),
                style = Stroke(22f, cap = StrokeCap.Round),
            )
            drawPath(
                Path().apply {
                    moveTo(52f, 104f)
                    lineTo(45f, 44f)
                    lineTo(83f, 67f)
                    lineTo(110f, 60f)
                    lineTo(137f, 40f)
                    lineTo(141f, 108f)
                    close()
                },
                Color(0xffe4bd96),
            )
            oval(Color(0xffe4bd96), 46f, 68f, 146f, 145f)
            fun line(color: Long, width: Float, vararg points: Float) {
                drawPath(
                    Path().apply {
                        moveTo(points[0], points[1])
                        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                    },
                    Color(color),
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
            line(0xffbc8d65, 5f, 56f, 61f, 62f, 78f)
            line(0xffbc8d65, 5f, 128f, 59f, 126f, 76f)
            line(0xff334e40, 3f, 64f, 104f, 71f, 108f, 78f, 104f)
            line(0xff334e40, 3f, 109f, 104f, 116f, 108f, 123f, 104f)
            oval(Color(0xffaf745b), 89f, 113f, 99f, 119f)
            line(0xff334e40, 2f, 94f, 119f, 94f, 123f, 88f, 126f)
            line(0xff334e40, 2f, 94f, 123f, 100f, 126f)
            line(0xffbc8d65, 2f, 54f, 117f, 34f, 113f)
            line(0xffbc8d65, 2f, 55f, 123f, 36f, 126f)
            line(0xffbc8d65, 2f, 133f, 117f, 151f, 113f)
            oval(Color(0xffedd0af), 64f, 138f, 107f, 155f)
            line(0xff75957b, 3f, 29f, 84f, 29f, 96f)
            line(0xff75957b, 3f, 23f, 90f, 35f, 90f)
            oval(Color(0xffc99171), 205f, 39f, 214f, 48f)
        }
    }
}

private fun DrawScope.oval(color: Color, left: Float, top: Float, right: Float, bottom: Float) =
    drawOval(color, Offset(left, top), Size(right - left, bottom - top))
