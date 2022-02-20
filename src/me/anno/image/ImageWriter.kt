package me.anno.image

import me.anno.image.BoxBlur.gaussianBlur
import me.anno.image.colormap.ColorMap
import me.anno.image.colormap.LinearColorMap
import me.anno.image.raw.IntImage
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.mix
import me.anno.maths.Maths.mixARGB
import me.anno.maths.Maths.unmix
import me.anno.ui.base.components.Padding
import me.anno.utils.Color.a
import me.anno.utils.Color.b
import me.anno.utils.Color.g
import me.anno.utils.Color.r
import me.anno.utils.Color.rgba
import me.anno.utils.LOGGER
import me.anno.utils.OS.desktop
import me.anno.utils.files.Files.use
import me.anno.utils.hpc.HeavyProcessing.processBalanced
import org.joml.Vector2f
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.*

@Suppress("unused")
object ImageWriter {

    // todo maybe we should add noise (optionally) to make the visual results even nicer

    fun getFile(name: String): FileReference {
        val name2 = if (name.endsWith("png") || name.endsWith("jpg")) name else "$name.png"
        return getReference(desktop, name2)
    }

    inline fun writeRGBImageByte3(
        w: Int,
        h: Int,
        name: String,
        minPerThread: Int,
        crossinline getRGB: (x: Int, y: Int, i: Int) -> Triple<Byte, Byte, Byte>
    ) {
        writeRGBImageInt(w, h, name, minPerThread) { x, y, i ->
            val (r, g, b) = getRGB(x, y, i)
            rgba(r, g, b, -1)
        }
    }

    inline fun writeRGBImageInt3(
        w: Int,
        h: Int,
        name: String,
        minPerThread: Int,
        crossinline getRGB: (x: Int, y: Int, i: Int) -> Triple<Int, Int, Int>
    ) {
        writeRGBImageInt(w, h, name, minPerThread) { x, y, i ->
            val (r, g, b) = getRGB(x, y, i)
            rgba(r, g, b, 255)
        }
    }

    inline fun writeRGBImageInt(
        w: Int,
        h: Int,
        name: String,
        minPerThread: Int,
        crossinline getRGB: (x: Int, y: Int, i: Int) -> Int
    ) {
        writeImageInt(w, h, false, name, minPerThread, getRGB)
    }

    inline fun writeRGBAImageInt(
        w: Int,
        h: Int,
        name: String,
        minPerThread: Int,
        crossinline getRGB: (x: Int, y: Int, i: Int) -> Int
    ) {
        writeImageInt(w, h, true, name, minPerThread, getRGB)
    }

    fun writeImageFloat(
        w: Int, h: Int, name: String,
        normalize: Boolean, values: FloatArray
    ) = writeImageFloat(w, h, name, normalize, LinearColorMap.default, values)

    fun writeImageFloat(
        w: Int, h: Int, name: String,
        normalize: Boolean,
        colorMap: ColorMap,
        values: FloatArray
    ) {
        val cm = if (normalize) colorMap.normalized(values) else colorMap
        val imgData = IntArray(w * h)
        for (i in 0 until w * h) {
            imgData[i] = cm.getColor(values[i])
        }
        val file = desktop.getChild(name)
        file.getParent()?.mkdirs()
        use(file.outputStream()) {
            val img = IntImage(w, h, imgData, false)
            img.write(getFile(name))
        }
    }

    fun writeImageFloatWithOffsetAndStride(
        w: Int, h: Int,
        offset: Int, stride: Int,
        name: String,
        normalize: Boolean,
        colorMap: ColorMap,
        values: FloatArray
    ) {
        val cm = if (normalize) colorMap.normalized(values) else colorMap
        val imgData = IntArray(w * h)
        var j = offset
        for (y in 0 until h) {
            val i0 = y * h
            val i1 = i0 + h
            for (i in i0 until i1) {
                imgData[i] = cm.getColor(values[j++])
            }
            j += stride - h
        }
        val file = desktop.getChild(name)
        file.getParent()?.mkdirs()
        use(file.outputStream()) {
            val img = IntImage(w, h, imgData, false)
            img.write(getFile(name))
        }
    }

    fun writeImageFloatMSAA(
        w: Int, h: Int, name: String,
        normalize: Boolean,
        colorMap: ColorMap,
        samples: Int,
        values: FloatArray
    ) {
        val cm = if (normalize) colorMap.normalized(values) else colorMap
        val img = BufferedImage(w, h, if (colorMap.hasAlpha) 2 else 1)
        val buffer = img.raster.dataBuffer
        val alpha = if (colorMap.hasAlpha) 0 else (0xff shl 24)
        if (samples <= 1) {
            for (i in 0 until w * h) {
                buffer.setElem(i, alpha or cm.getColor(values[i]))
            }
        } else {
            for (i in 0 until w * h) {
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                val j = i * samples
                for (sample in 0 until samples) {
                    val color = cm.getColor(values[j + sample])
                    r += color.r()
                    g += color.g()
                    b += color.b()
                    a += color.a()
                }
                val color = alpha or rgba(r / samples, g / samples, b / samples, a / samples)
                buffer.setElem(i, color)
            }
        }
        val file = getFile(name)
        file.getParent()?.mkdirs()
        use(file.outputStream()) {
            ImageIO.write(img, if (name.endsWith(".jpg")) "jpg" else "png", it)
        }
    }


    inline fun writeImageFloat(
        w: Int, h: Int, name: String,
        minPerThread: Int,
        normalize: Boolean,
        crossinline getRGB: (x: Int, y: Int, i: Int) -> Float
    ) {
        return writeImageFloat(w, h, name, minPerThread, normalize, LinearColorMap.default, getRGB)
    }

    inline fun writeImageFloat(
        w: Int, h: Int, name: String,
        minPerThread: Int,
        normalize: Boolean,
        colorMap: ColorMap,
        crossinline getRGB: (x: Int, y: Int, i: Int) -> Float
    ) {
        val values = FloatArray(w * h)
        if (minPerThread < 0) {// multi-threading is forbidden
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = x + y * w
                    values[i] = getRGB(x, y, i)
                }
            }
        } else {
            processBalanced(0, w * h, minPerThread) { i0, i1 ->
                for (i in i0 until i1) {
                    val x = i % w
                    val y = i / w
                    values[i] = getRGB(x, y, i)
                }
            }
        }
        return writeImageFloat(w, h, name, normalize, colorMap, values)
    }

    val MSAAx8 = floatArrayOf(
        0.058824f, 0.419608f,
        0.298039f, 0.180392f,
        0.180392f, 0.819608f,
        0.419608f, 0.698039f,
        0.580392f, 0.298039f,
        0.941176f, 0.058824f,
        0.698039f, 0.941176f,
        0.819608f, 0.580392f
    )

    inline fun writeImageFloatMSAA(
        w: Int, h: Int, name: String,
        minPerThread: Int,
        normalize: Boolean,
        crossinline getValue: (x: Float, y: Float) -> Float
    ) = writeImageFloatMSAA(w, h, name, minPerThread, normalize, LinearColorMap.default, getValue)

    inline fun writeImageFloatMSAA(
        w: Int, h: Int, name: String,
        minPerThread: Int,
        normalize: Boolean,
        colorMap: ColorMap,
        crossinline getValue: (x: Float, y: Float) -> Float
    ) {
        val samples = 8
        val values = FloatArray(w * h * samples)
        if (minPerThread < 0) {// multi-threading is forbidden
            for (y in 0 until h) {
                val yf = y.toFloat()
                for (x in 0 until w) {
                    val k = (x + y * w) * samples
                    val xf = x.toFloat()
                    for (j in 0 until samples) {
                        values[k + j] = getValue(
                            xf + MSAAx8[j * 2],
                            yf + MSAAx8[j * 2 + 1]
                        )
                    }
                }
            }
        } else {
            processBalanced(0, w * h, minPerThread) { i0, i1 ->
                for (i in i0 until i1) {
                    val x = i % w
                    val y = i / w
                    val xf = x.toFloat()
                    val yf = y.toFloat()
                    val k = i * samples
                    for (j in 0 until samples) {
                        values[k + j] = getValue(
                            xf + MSAAx8[j * 2],
                            yf + MSAAx8[j * 2 + 1]
                        )
                    }
                }
            }
        }
        return writeImageFloatMSAA(w, h, name, normalize, colorMap, samples, values)
    }

    fun getColor(x: Float): Int {
        if (x.isNaN()) return 0x0000ff
        val v = min((abs(x) * 255).toInt(), 255)
        return if (x < 0f) {
            0x10000
        } else {
            0x10101
        } * v
    }

    fun getColor(v: Float, minColor: Int, zeroColor: Int, maxColor: Int, nanColor: Int): Int {
        return when {
            v.isFinite() -> mixARGB(zeroColor, if (v < 0) minColor else maxColor, abs(v))
            v.isNaN() -> nanColor
            v < 0f -> minColor
            else -> maxColor
        }
    }

    inline fun writeImageInt(
        w: Int, h: Int, alpha: Boolean, name: String,
        minPerThread: Int,
        crossinline getRGB: (x: Int, y: Int, i: Int) -> Int
    ) {
        val img = BufferedImage(w, h, if (alpha) 2 else 1)
        val buffer = img.raster.dataBuffer
        if (minPerThread !in 0 until w * h) {// multi-threading is forbidden
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = x + y * w
                    buffer.setElem(i, getRGB(x, y, i))
                }
            }
        } else {
            processBalanced(0, w * h, minPerThread) { i0, i1 ->
                for (i in i0 until i1) {
                    val x = i % w
                    val y = i / w
                    buffer.setElem(i, getRGB(x, y, i))
                }
            }
        }
        use(getFile(name).outputStream()) {
            ImageIO.write(img, if (name.endsWith(".jpg")) "jpg" else "png", it)
        }
    }

    fun writeImageInt(
        w: Int, h: Int, alpha: Boolean,
        name: String,
        pixels: IntArray
    ) {
        val img = BufferedImage(w, h, if (alpha) 2 else 1)
        val buffer = img.raster.dataBuffer
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = x + y * w
                buffer.setElem(i, pixels[i])
            }
        }
        use(getFile(name).outputStream()) {
            ImageIO.write(img, if (name.endsWith(".jpg")) "jpg" else "png", it)
        }
    }

    private fun addPoint(image: FloatArray, w: Int, x: Int, y: Int, v: Float) {
        if (x in 0 until w && y >= 0) {
            val index = x + y * w
            if (index < image.size) {
                image[index] += v
            }
        }
    }

    fun writeImageCurve(
        wr: Int, hr: Int, minColor: Int, maxColor: Int,
        thickness: Int,
        points: List<Vector2f>, name: String
    ) {
        val w = wr + 2 * thickness
        val h = hr + 2 * thickness
        val image = FloatArray(w * h)
        // do all line sections
        var ctr = 0f
        val t0 = System.nanoTime()
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val distance = p0.distance(p1)
            val steps = ceil(distance)
            val invSteps = 1f / steps
            val stepSize = distance * invSteps
            ctr += steps
            for (j in 0 until steps.toInt()) {
                // add point
                val f = j * invSteps
                val x = mix(p0.x, p1.x, f) + thickness
                val y = mix(p0.y, p1.y, f) + thickness
                val xi = floor(x)
                val yi = floor(y)
                val fx = x - xi
                val fy = y - yi
                val gx = 1f - fx
                val gy = 1f - fy
                val s0 = stepSize * gx
                val s1 = stepSize * fx
                val w0 = s0 * gy
                val w1 = s0 * fy
                val w2 = s1 * gy
                val w3 = s1 * fy
                val ix = xi.toInt()
                val iy = yi.toInt()
                addPoint(image, w, ix, iy, w0)
                addPoint(image, w, ix, iy + 1, w1)
                addPoint(image, w, ix + 1, iy, w2)
                addPoint(image, w, ix + 1, iy + 1, w3)
            }
        }
        // bokeh-blur would be nicer, and correcter,
        // but this is a pretty good trade-off between visuals and performance :)
        gaussianBlur(image, w, h, thickness)
        val scale = 1f / (thickness * thickness * sqrt(thickness.toFloat()))
        for (i in 0 until w * h) image[i] *= scale
        val t1 = System.nanoTime()
        // nano seconds per pixel
        // ~ 24ns/px for everything, including copy;
        // for 2048² pixels, and thickness = 75
        LOGGER.info("${(t1 - t0).toFloat() / (w * h)}ns/px")
        val cm = LinearColorMap(0, minColor, maxColor)
        writeImageFloatWithOffsetAndStride(wr, hr, thickness * (w + 1), w, name, false, cm, image)
    }

    fun writeImageProfile(
        values: FloatArray, h: Int,
        name: String,
        normalize: Boolean,
        map: ColorMap = LinearColorMap.default,
        background: Int = -1,
        foreground: Int = 0xff shl 24,
        alpha: Boolean = false,
        padding: Padding = Padding((values.size + h) / 50 + 2)
    ) {
        val map2 = if (normalize) map.normalized(values) else map
        writeImageProfile(values, h, name, map2, background, foreground, alpha, padding)
    }

    fun writeImageProfile(
        values: FloatArray, h: Int,
        name: String,
        map: ColorMap,
        background: Int = -1,
        foreground: Int = 0xff shl 24,
        alpha: Boolean = false,
        padding: Padding = Padding((values.size + h) / 50 + 2)
    ) {
        // todo different backgrounds for positive & negative values?
        // todo on steep sections, use more points
        // todo option to make the colored line thicker
        // to do write x and y axis notations? (currently prefer no)
        // define padding
        // todo support stretching (interpolation/averaging) of values?
        val w = values.size
        val w2 = w + padding.width
        val h2 = h + padding.height
        val pixels = IntArray(w2 * h2)
        val min = map.min
        val max = map.max
        // to do better (memory aligned) writes?
        val my = h - 1
        pixels.fill(background)
        for (x in 0 until w) {
            val offset = x + padding.left + padding.top * w2
            val v = values[x]
            if (v in min..max) {
                val color = map.getColor(v)
                val cy = clamp((unmix(max, min, v) * h).toInt(), 0, my)
                // draw background until there ... done automatically
                // draw colored pixel
                pixels[offset + cy * w2] = color
                // draw foreground from there
                for (y in cy + 1 until h) {
                    pixels[offset + y * w2] = foreground
                }
            }
        }
        writeImageInt(w2, h2, alpha, name, pixels)
    }

}