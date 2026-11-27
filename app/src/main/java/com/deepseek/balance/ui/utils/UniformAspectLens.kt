package com.deepseek.balance.ui.utils

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.shapes.RoundedRectangularShape
import org.intellij.lang.annotations.Language

/**
 * 椭圆角圆角矩形：每角独立 rx/ry。
 * 用于非等比缩放（Sx≠Sy）前的圆角预变形——预设 (base/Sx, base/Sy)，
 * 缩放后 X/Y 视觉半径都精确回到 base，四角均匀、严格跟随圆角滑块。
 */
class EllipticalRoundedShape(
    val radiusX: Dp,
    val radiusY: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rx = with(density) { radiusX.toPx() }.coerceIn(0f, size.width / 2f)
        val ry = with(density) { radiusY.toPx() }.coerceIn(0f, size.height / 2f)
        return Outline.Rounded(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = CornerRadius(rx, ry),
                topRight = CornerRadius(rx, ry),
                bottomRight = CornerRadius(rx, ry),
                bottomLeft = CornerRadius(rx, ry),
            )
        )
    }
}

/**
 * 各向异性折射（在 backdrop 库原版 lens 着色器基础上增加两项补偿）：
 *
 * 1. 纵横比补偿：库的 lens() 对四边施加统一像素位移，而胶囊是扁长形状（高约 56dp、
 *    宽约为高的 3 倍）：同样的 24dp 位移，上下边相对扭曲率 ≈86%、左右边仅 ≈31%。
 *    此处把垂直方向位移按 半高/半宽 缩放，使四边相对扭曲率一致。
 * 2. 椭圆角支持：cornerRadiiX/Y 每角独立 rx/ry，配合 EllipticalRoundedShape 的预变形，
 *    非等比缩放下折射带形状与裁切形状始终一致。rx=ry 时 SDF 精确退化为库原版圆形。
 */
fun BackdropEffectScope.uniformAspectLens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).coerceAtLeast(0f)
    }

    val (radiiX, radiiY) = roundedCornerRadii(shape, size, layoutDirection, this) ?: return
    runtimeShaderEffect(
        key = "UniformAspectRefraction",
        shaderString = UNIFORM_ASPECT_REFRACTION_SHADER,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("cornerRadiiX", radiiX)
        setFloatUniform("cornerRadiiY", radiiY)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        setFloatUniform("chromaticAberration", if (chromaticAberration) 1f else 0f)
    }
}

/** 圆角提取：返回每角 (rx数组, ry数组)，顺序 topLeft/topRight/bottomRight/bottomLeft */
private fun roundedCornerRadii(
    shape: Shape,
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density
): Pair<FloatArray, FloatArray>? = when (shape) {
    is EllipticalRoundedShape -> {
        val rx = with(density) { shape.radiusX.toPx() }.coerceIn(0f, size.width / 2f)
        val ry = with(density) { shape.radiusY.toPx() }.coerceIn(0f, size.height / 2f)
        floatArrayOf(rx, rx, rx, rx) to floatArrayOf(ry, ry, ry, ry)
    }

    is RoundedRectangularShape -> {
        val corners = shape.corners(size, layoutDirection, density)
        floatArrayOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft) to
            floatArrayOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
    }

    is AbsoluteRoundedCornerShape -> {
        val maxRadius = size.minDimension / 2f
        val tl = shape.topStart.toPx(size, density).fastCoerceAtMost(maxRadius)
        val tr = shape.topEnd.toPx(size, density).fastCoerceAtMost(maxRadius)
        val br = shape.bottomEnd.toPx(size, density).fastCoerceAtMost(maxRadius)
        val bl = shape.bottomStart.toPx(size, density).fastCoerceAtMost(maxRadius)
        floatArrayOf(tl, tr, br, bl) to floatArrayOf(tl, tr, br, bl)
    }

    is CornerBasedShape -> {
        val maxRadius = size.minDimension / 2f
        val isLtr = layoutDirection == LayoutDirection.Ltr
        val tl = (if (isLtr) shape.topStart.toPx(size, density) else shape.topEnd.toPx(size, density)).fastCoerceAtMost(maxRadius)
        val tr = (if (isLtr) shape.topEnd.toPx(size, density) else shape.topStart.toPx(size, density)).fastCoerceAtMost(maxRadius)
        val br = (if (isLtr) shape.bottomEnd.toPx(size, density) else shape.bottomStart.toPx(size, density)).fastCoerceAtMost(maxRadius)
        val bl = (if (isLtr) shape.bottomStart.toPx(size, density) else shape.bottomEnd.toPx(size, density)).fastCoerceAtMost(maxRadius)
        floatArrayOf(tl, tr, br, bl) to floatArrayOf(tl, tr, br, bl)
    }

    else -> null
}

private const val ROUNDED_RECT_SDF = """
float2 radiusAt(float2 coord, float4 radiiX, float4 radiiY) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return float2(radiiX.y, radiiY.y);
        else return float2(radiiX.z, radiiY.z);
    } else {
        if (coord.y <= 0.0) return float2(radiiX.x, radiiY.x);
        else return float2(radiiX.w, radiiY.w);
    }
}

// 椭圆圆角矩形 SDF（近似）。rx=ry=r 时精确退化为库原版圆形：
// outside = (length(q/r)-1)*r = length(q)-r
float sdRoundedRect(float2 coord, float2 halfSize, float2 radius) {
    float2 cornerCoord = abs(coord) - (halfSize - radius);
    float2 q = max(cornerCoord, 0.0);
    float outside = (length(q / radius) - 1.0) * min(radius.x, radius.y);
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float2 radius) {
    float2 cornerCoord = abs(coord) - (halfSize - radius);
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        // 椭圆法线 ∝ q/r²（rx=ry 时退化为 q 方向，与库原版一致）；epsilon 防 normalize(0)
        float2 n = max(cornerCoord, 0.0) / (radius * radius) + float2(1e-6);
        return sign(coord) * normalize(n);
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}"""

@Language("AGSL")
private const val UNIFORM_ASPECT_REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadiiX;
uniform float4 cornerRadiiY;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float2 radius = radiusAt(coord, cornerRadiiX, cornerRadiiY);
    // 零圆角（方角）防除零：钳到极小值后 SDF 近似直角矩形
    radius = max(radius, float2(1e-3, 1e-3));

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float2 gradRadius = min(radius * 1.5, halfSize);
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    // 各向异性补偿：垂直位移按 半高/半宽 缩放，四边相对扭曲率一致
    float aspect = halfSize.y / max(halfSize.x, 1.0);
    float2 disp = d * grad;
    disp.y *= aspect;
    float2 refractedCoord = coord + disp;

    if (chromaticAberration < 0.5) {
        return content.eval(refractedCoord);
    }

    float dispersionIntensity = (centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y);
    float2 dispersedCoord = disp * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}"""