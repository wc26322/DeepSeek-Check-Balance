package com.deepseek.balance.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal const val LUMINANCE_MAP_COLUMNS = 32
internal const val LUMINANCE_MAP_ROWS = 64

internal data class Rgb(
    val red: Float,
    val green: Float,
    val blue: Float,
) {
    fun relativeLuminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    fun chroma(): Float = max(red, max(green, blue)) - min(red, min(green, blue))

    companion object {
        fun ofArgb(argb: Int): Rgb = Rgb(
            red = (argb shr 16 and 0xFF) / 255f,
            green = (argb shr 8 and 0xFF) / 255f,
            blue = (argb and 0xFF) / 255f,
        )
    }
}

internal data class LuminanceSample(
    val luminance: Float,
    val chroma: Float,
)

internal data class AmbientBlobSpec(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val radiusFraction: Float,
    val color: Rgb,
    val alpha: Float,
)

internal data class AmbientBackgroundSpec(
    val baseColor: Rgb,
    val blobs: List<AmbientBlobSpec>,
) {
    fun colorAt(
        xFraction: Float,
        yFraction: Float,
        heightToWidthRatio: Float,
        alphaScale: Float,
    ): Rgb {
        var red = baseColor.red
        var green = baseColor.green
        var blue = baseColor.blue
        for (blob in blobs) {
            val dx = xFraction - blob.centerXFraction
            val dy = (yFraction - blob.centerYFraction) * heightToWidthRatio
            val normalizedDistance = hypot(dx, dy) / blob.radiusFraction
            if (normalizedDistance >= 1f) continue
            val blobAlpha = (blob.alpha * alphaScale * (1f - normalizedDistance)).coerceIn(0f, 1f)
            if (blobAlpha <= 0f) continue
            red = blob.color.red * blobAlpha + red * (1f - blobAlpha)
            green = blob.color.green * blobAlpha + green * (1f - blobAlpha)
            blue = blob.color.blue * blobAlpha + blue * (1f - blobAlpha)
        }
        return Rgb(red, green, blue)
    }
}

internal class BackdropLuminanceMap(
    val columns: Int,
    val rows: Int,
    private val luminanceValues: FloatArray,
    private val chromaValues: FloatArray,
) {
    init {
        require(columns > 0 && rows > 0) { "luminance map must be non-empty" }
        require(luminanceValues.size == columns * rows) { "luminance size mismatch" }
        require(chromaValues.size == columns * rows) { "chroma size mismatch" }
    }

    fun sample(
        leftFraction: Float,
        topFraction: Float,
        rightFraction: Float,
        bottomFraction: Float,
    ): LuminanceSample {
        val left = min(leftFraction, rightFraction).coerceIn(0f, 1f)
        val right = max(leftFraction, rightFraction).coerceIn(0f, 1f)
        val top = min(topFraction, bottomFraction).coerceIn(0f, 1f)
        val bottom = max(topFraction, bottomFraction).coerceIn(0f, 1f)

        val startColumn = floor(left * columns).toInt().coerceIn(0, columns - 1)
        val endColumn = ceil(right * columns).toInt().coerceIn(startColumn + 1, columns)
        val startRow = floor(top * rows).toInt().coerceIn(0, rows - 1)
        val endRow = ceil(bottom * rows).toInt().coerceIn(startRow + 1, rows)

        var luminanceSum = 0f
        var chromaSum = 0f
        var count = 0
        for (row in startRow until endRow) {
            for (column in startColumn until endColumn) {
                val index = row * columns + column
                luminanceSum += luminanceValues[index]
                chromaSum += chromaValues[index]
                count++
            }
        }
        if (count == 0) return LuminanceSample(0f, 0f)
        return LuminanceSample(luminanceSum / count, chromaSum / count)
    }
}

internal fun luminanceMapFromPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    columns: Int = LUMINANCE_MAP_COLUMNS,
    rows: Int = LUMINANCE_MAP_ROWS,
): BackdropLuminanceMap {
    require(width > 0 && height > 0) { "pixel source must be non-empty" }
    require(pixels.size >= width * height) { "pixel buffer smaller than width * height" }

    val luminanceValues = FloatArray(columns * rows)
    val chromaValues = FloatArray(columns * rows)
    for (row in 0 until rows) {
        val startY = row * height / rows
        val endY = max(startY + 1, (row + 1) * height / rows)
        for (column in 0 until columns) {
            val startX = column * width / columns
            val endX = max(startX + 1, (column + 1) * width / columns)
            var luminanceSum = 0f
            var chromaSum = 0f
            var count = 0
            for (y in startY until endY) {
                val rowOffset = y * width
                for (x in startX until endX) {
                    val color = Rgb.ofArgb(pixels[rowOffset + x])
                    luminanceSum += color.relativeLuminance()
                    chromaSum += color.chroma()
                    count++
                }
            }
            val index = row * columns + column
            luminanceValues[index] = luminanceSum / count
            chromaValues[index] = chromaSum / count
        }
    }
    return BackdropLuminanceMap(columns, rows, luminanceValues, chromaValues)
}

internal fun luminanceMapFromAmbient(
    spec: AmbientBackgroundSpec,
    heightToWidthRatio: Float,
    alphaScale: Float,
    columns: Int = LUMINANCE_MAP_COLUMNS,
    rows: Int = LUMINANCE_MAP_ROWS,
): BackdropLuminanceMap {
    require(heightToWidthRatio > 0f) { "heightToWidthRatio must be positive" }
    val luminanceValues = FloatArray(columns * rows)
    val chromaValues = FloatArray(columns * rows)
    for (row in 0 until rows) {
        val yFraction = (row + 0.5f) / rows
        for (column in 0 until columns) {
            val xFraction = (column + 0.5f) / columns
            val color = spec.colorAt(xFraction, yFraction, heightToWidthRatio, alphaScale)
            val index = row * columns + column
            luminanceValues[index] = color.relativeLuminance()
            chromaValues[index] = color.chroma()
        }
    }
    return BackdropLuminanceMap(columns, rows, luminanceValues, chromaValues)
}
internal fun Color.toRgb(): Rgb = Rgb(red, green, blue)

internal fun luminanceMapFromImage(
    bitmap: ImageBitmap,
    containerWidth: Float,
    containerHeight: Float,
    columns: Int = LUMINANCE_MAP_COLUMNS,
    rows: Int = LUMINANCE_MAP_ROWS,
): BackdropLuminanceMap {
    val source = bitmap.asAndroidBitmap()
    if (source.width <= 0 || source.height <= 0 || containerWidth <= 0f || containerHeight <= 0f) {
        return luminanceMapFromPixels(IntArray(columns * rows), columns, rows, columns, rows)
    }
    val scale = max(containerWidth / source.width, containerHeight / source.height)
    val visibleWidth = (containerWidth / scale).toInt().coerceIn(1, source.width)
    val visibleHeight = (containerHeight / scale).toInt().coerceIn(1, source.height)
    val left = (source.width - visibleWidth) / 2
    val top = (source.height - visibleHeight) / 2
    val visible = if (left == 0 && top == 0 && visibleWidth == source.width && visibleHeight == source.height) {
        source
    } else {
        Bitmap.createBitmap(source, left, top, visibleWidth, visibleHeight)
    }
    val scaled = if (visible.width == columns && visible.height == rows) {
        visible
    } else {
        Bitmap.createScaledBitmap(visible, columns, rows, true)
    }
    val pixels = IntArray(columns * rows)
    scaled.getPixels(pixels, 0, columns, 0, 0, columns, rows)
    return luminanceMapFromPixels(pixels, columns, rows, columns, rows)
}