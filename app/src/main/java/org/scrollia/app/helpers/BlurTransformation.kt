/**
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scrollia.app.helpers

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Coil transformation that blurs a bitmap by downscaling it and applying two
 * sliding-window box blur passes (a cheap gaussian approximation). Works on
 * all API levels, unlike RenderEffect which requires API 31+.
 */
class BlurTransformation(private val radius: Int = 10, private val downscale: Int = 4) :
    Transformation {

    override val cacheKey: String = "BlurTransformation(radius=$radius,downscale=$downscale)"

    override suspend fun transform(bitmap: Bitmap, size: Size): Bitmap {
        val width = (bitmap.width / downscale).coerceAtLeast(1)
        val height = (bitmap.height / downscale).coerceAtLeast(1)
        var scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        repeat(2) { scaled = boxBlur(scaled, radius) }
        return scaled
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = IntArray(w * h)
        blurPass(pixels, result, w, h, radius, horizontal = true)
        blurPass(result, pixels, w, h, radius, horizontal = false)
        return Bitmap.createBitmap(pixels, w, h, src.config ?: Bitmap.Config.ARGB_8888)
    }

    private fun blurPass(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val div = radius * 2 + 1
        val outerLimit = if (horizontal) h else w
        val innerLimit = if (horizontal) w else h
        for (outer in 0 until outerLimit) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[index(outer, i.coerceIn(0, innerLimit - 1), w, horizontal)]
                a += p ushr 24
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            for (inner in 0 until innerLimit) {
                dst[index(outer, inner, w, horizontal)] =
                    (a / div shl 24) or (r / div shl 16) or (g / div shl 8) or (b / div)
                val added = src[index(outer, (inner + radius + 1).coerceAtMost(innerLimit - 1), w, horizontal)]
                val removed = src[index(outer, (inner - radius).coerceAtLeast(0), w, horizontal)]
                a += (added ushr 24) - (removed ushr 24)
                r += (added shr 16 and 0xFF) - (removed shr 16 and 0xFF)
                g += (added shr 8 and 0xFF) - (removed shr 8 and 0xFF)
                b += (added and 0xFF) - (removed and 0xFF)
            }
        }
    }

    private fun index(outer: Int, inner: Int, width: Int, horizontal: Boolean): Int =
        if (horizontal) outer * width + inner else inner * width + outer
}
