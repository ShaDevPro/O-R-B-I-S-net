package com.sha.orbis.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Custom cradle shape for the bottom navigation bar with an elegant concave scoop notch
 * hugging the central elevated circular '+' button.
 */
class CradleBottomBarShape(
    private val fabDiameter: Dp = 58.dp,
    private val cradleMargin: Dp = 10.dp,
    private val shoulderRadius: Dp = 18.dp,
    private val cradleDepth: Dp = 38.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val fabRadiusPx = with(density) { (fabDiameter / 2).toPx() }
        val marginPx = with(density) { cradleMargin.toPx() }
        val shoulderPx = with(density) { shoulderRadius.toPx() }
        val depthPx = with(density) { cradleDepth.toPx() }

        val cradleRPx = fabRadiusPx + marginPx
        val cx = size.width / 2f
        val leftStart = cx - cradleRPx - shoulderPx
        val rightEnd = cx + cradleRPx + shoulderPx

        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(leftStart, 0f)

            // Cubic bezier 1: left shoulder dipping into the notch
            cubicTo(
                x1 = cx - cradleRPx, y1 = 0f,
                x2 = cx - cradleRPx - 2f, y2 = depthPx * 0.35f,
                x3 = cx - cradleRPx + shoulderPx * 0.5f, y3 = depthPx * 0.70f
            )

            // Cubic bezier 2: curving along the bottom of the scoop
            cubicTo(
                x1 = cx - cradleRPx + shoulderPx * 1.1f, y1 = depthPx,
                x2 = cx - fabRadiusPx * 0.5f, y2 = depthPx,
                x3 = cx, y3 = depthPx
            )

            // Cubic bezier 3: curving up from bottom of scoop to right dip
            cubicTo(
                x1 = cx + fabRadiusPx * 0.5f, y1 = depthPx,
                x2 = cx + cradleRPx - shoulderPx * 1.1f, y2 = depthPx,
                x3 = cx + cradleRPx - shoulderPx * 0.5f, y3 = depthPx * 0.70f
            )

            // Cubic bezier 4: rising out of notch to right shoulder
            cubicTo(
                x1 = cx + cradleRPx + 2f, y1 = depthPx * 0.35f,
                x2 = cx + cradleRPx, y2 = 0f,
                x3 = rightEnd, y3 = 0f
            )

            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        return Outline.Generic(path)
    }
}
