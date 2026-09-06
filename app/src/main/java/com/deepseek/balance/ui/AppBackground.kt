package com.deepseek.balance.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap.Config.ARGB_8888
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// 根层背景（官方 BackdropDemoScaffold 的"壁纸"同角色）：
// 有自定义图片 → 显示图片（ContentScale.Crop，官方同款），玻璃卡片折射真壁纸；
// 未设置 / 解码失败 → 回退默认环境光斑 AmbientBackground。
// modifier 由调用方挂 layerBackdrop(ambientBackdrop)：无论哪个分支，玻璃卡片折射的都是同一录制层。
@Composable
internal fun AppBackground(
    backgroundImagePath: String,
    modifier: Modifier = Modifier
) {
    if (backgroundImagePath.isBlank()) {
        AmbientBackground(modifier)
        return
    }

    val context = LocalContext.current
    // 异步解码：不在主线程解大图；路径变化（换图）时自动重解码
    val bitmap by produceState<ImageBitmap?>(initialValue = null, backgroundImagePath) {
        value = withContext(Dispatchers.IO) {
            decodeBackgroundBitmap(backgroundImagePath)
        }
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        // 加载中 / 解码失败：先显示默认光斑，避免闪黑屏
        AmbientBackground(modifier)
    }
}

// 把照片选择器返回的 Uri 复制到 filesDir/backgrounds/（每次用唯一文件名）。
// 不依赖系统授予的临时读取权限，重启后依然可用；返回文件绝对路径，失败返回 null。
//
// 注意：不能用「固定文件名覆盖写」。背景层用 produceState(路径) 触发重新解码，
// key 是路径字符串——覆盖同名文件时路径不变，produceState 不会重跑，
// 内存里还是旧位图 → 表现为「再次选图无法替换」。必须每次换新路径。
internal fun copyUriToBackgroundFile(context: Context, uri: Uri): String? = try {
    val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // 清理旧背景图，避免文件堆积
    val file = File(dir, "background_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    if (file.length() > 0) file.absolutePath else null
} catch (e: Exception) {
    null
}

// 删除背景图文件（恢复默认背景时调用，避免 filesDir 残留）
internal fun deleteBackgroundFile(path: String) {
    if (path.isBlank()) return
    try {
        File(path).delete()
    } catch (_: Exception) {
    }
}

// 按长边 ≤2048px 降采样解码，避免高分辨率壁纸（4K/12K）全尺寸解码导致内存暴涨
private fun decodeBackgroundBitmap(path: String): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = ARGB_8888
        }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
}
