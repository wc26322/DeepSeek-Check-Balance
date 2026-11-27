package com.deepseek.balance.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropLuminanceTest {

    private val delta = 0.001f

    @Test
    fun uniformWhiteProducesFullLuminanceAndNoChroma() {
        val pixels = IntArray(4 * 4) { 0xFFFFFFFF.toInt() }
        val map = luminanceMapFromPixels(pixels, width = 4, height = 4, columns = 2, rows = 2)
        val sample = map.sample(0f, 0f, 1f, 1f)
        assertEquals(1f, sample.luminance, delta)
        assertEquals(0f, sample.chroma, delta)
    }

    @Test
    fun uniformBlackProducesZeroLuminance() {
        val pixels = IntArray(4 * 4) { 0xFF000000.toInt() }
        val map = luminanceMapFromPixels(pixels, width = 4, height = 4, columns = 2, rows = 2)
        val sample = map.sample(0f, 0f, 1f, 1f)
        assertEquals(0f, sample.luminance, delta)
        assertEquals(0f, sample.chroma, delta)
    }

    @Test
    fun pureRedUsesRec709WeightAndFullChroma() {
        val pixels = intArrayOf(0xFFFF0000.toInt())
        val map = luminanceMapFromPixels(pixels, width = 1, height = 1, columns = 1, rows = 1)
        val sample = map.sample(0f, 0f, 1f, 1f)
        assertEquals(0.2126f, sample.luminance, delta)
        assertEquals(1f, sample.chroma, delta)
    }

    @Test
    fun samplingLeftAndRightHalvesSeparatesDarkAndLight() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val map = luminanceMapFromPixels(pixels, width = 2, height = 1, columns = 2, rows = 1)
        assertEquals(0f, map.sample(0f, 0f, 0.5f, 1f).luminance, delta)
        assertEquals(1f, map.sample(0.5f, 0f, 1f, 1f).luminance, delta)
        assertEquals(0.5f, map.sample(0f, 0f, 1f, 1f).luminance, delta)
    }

    @Test
    fun sampleClampsOutOfRangeFractions() {
        val pixels = IntArray(2 * 2) { 0xFFFFFFFF.toInt() }
        val map = luminanceMapFromPixels(pixels, width = 2, height = 2, columns = 2, rows = 2)
        val clamped = map.sample(-1f, -1f, 2f, 2f)
        assertEquals(1f, clamped.luminance, delta)
    }

    @Test
    fun ambientWithoutBlobsMatchesBaseColor() {
        val spec = AmbientBackgroundSpec(baseColor = Rgb(0.1f, 0.2f, 0.3f), blobs = emptyList())
        val map = luminanceMapFromAmbient(spec, heightToWidthRatio = 2f, alphaScale = 1f, columns = 4, rows = 4)
        val expected = 0.2126f * 0.1f + 0.7152f * 0.2f + 0.0722f * 0.3f
        assertEquals(expected, map.sample(0f, 0f, 1f, 1f).luminance, delta)
    }

    @Test
    fun ambientWhiteBlobCenterIsBrighterThanEdge() {
        val spec = AmbientBackgroundSpec(
            baseColor = Rgb(0f, 0f, 0f),
            blobs = listOf(
                AmbientBlobSpec(
                    centerXFraction = 0.5f,
                    centerYFraction = 0.5f,
                    radiusFraction = 0.4f,
                    color = Rgb(1f, 1f, 1f),
                    alpha = 1f,
                ),
            ),
        )
        val map = luminanceMapFromAmbient(spec, heightToWidthRatio = 1f, alphaScale = 1f, columns = 20, rows = 20)
        val center = map.sample(0.4f, 0.4f, 0.6f, 0.6f).luminance
        val corner = map.sample(0f, 0f, 0.1f, 0.1f).luminance
        assertTrue(center > corner)
    }

    @Test
    fun ambientAlphaScaleDarkensBlobs() {
        val spec = AmbientBackgroundSpec(
            baseColor = Rgb(0.5f, 0.5f, 0.5f),
            blobs = listOf(
                AmbientBlobSpec(0.5f, 0.5f, 1f, Rgb(1f, 1f, 1f), 0.8f),
            ),
        )
        val bright = luminanceMapFromAmbient(spec, 1f, alphaScale = 1f, columns = 8, rows = 8)
            .sample(0.6f, 0.6f, 0.7f, 0.7f).luminance
        val dim = luminanceMapFromAmbient(spec, 1f, alphaScale = 0.5f, columns = 8, rows = 8)
            .sample(0.6f, 0.6f, 0.7f, 0.7f).luminance
        assertTrue(dim < bright)
    }

    @Test
    fun adaptiveContentColorUsesDarkTextOnLightBackground() {
        assertEquals(Color(DARK_CONTENT_REFERENCE_ARGB), adaptiveContentColor(0.9f))
    }

    @Test
    fun adaptiveContentColorUsesLightTextOnDarkBackground() {
        assertEquals(Color(LIGHT_CONTENT_REFERENCE_ARGB), adaptiveContentColor(0.1f))
    }

    @Test
    fun adaptiveSecondaryContentColorMakesPrimaryTranslucent() {
        assertEquals(
            Color(DARK_CONTENT_REFERENCE_ARGB).copy(alpha = 0.72f),
            adaptiveSecondaryContentColor(0.9f),
        )
    }
}