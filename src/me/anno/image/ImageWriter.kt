package me.anno.image

import me.anno.image.BoxBlur.gaussianBlur
import me.anno.image.colormap.ColorMap
import me.anno.image.colormap.LinearColorMap
import me.anno.image.raw.BIImage
import me.anno.image.raw.IntImage
import me.anno.io.files.FileReference
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
import me.anno.utils.hpc.HeavyProcessing.processBalanced2d
import me.anno.utils.types.AABBs.avgX
import me.anno.utils.types.AABBs.avgY
import me.anno.utils.types.AABBs.deltaX
import me.anno.utils.types.AABBs.deltaY
import org.joml.AABBf
import org.joml.Vector2f
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.*

@Suppress("unused")
object ImageWriter {

    val tileSize = 8

    // todo maybe we should add noise (optionally) to make the visual results even nicer

    fun getFile(name: String): FileReference {
        val name2 = if (name.endsWith("png") || name.endsWith("jpg")) name else "$name.png"
        val file = desktop.getChild(name2)
        file.getParent()?.tryMkdirs()
        return file
    }

    fun writeImage(name: String, img: BufferedImage) {
        val file = getFile(name)
        use(file.outputStream()) {
            ImageIO.write(img, if (name.endsWith(".jpg")) "jpg" else "png", it)
        }
    }

    fun writeImage(name: String, img: Image) {
        val file = getFile(name)
        use(file.outputStream()) {
            img.write(getFile(name))
        }
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
        writeImage(name, IntImage(w, h, imgData, false))
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
        writeImage(name, IntImage(w, h, imgData, false))
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
        writeImage(name, img)
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
        processBalanced2d(0, 0, w, h, tileSize, minPerThread) { x0, y0, x1, y1 ->
            for (y in y0 until y1) {
                var i = x0 + y * w
                for (x in x0 until x1) {
                    values[i] = getRGB(x, y, i)
                    i++
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
        processBalanced2d(0, 0, w, h, tileSize, minPerThread / (tileSize * tileSize)) { x0, y0, x1, y1 ->
            for (y in y0 until y1) {
                var i = x0 + y * w
                for (x in x0 until x1) {
                    val xf = x.toFloat()
                    val yf = y.toFloat()
                    val k = i * samples
                    for (j in 0 until samples) {
                        values[k + j] = getValue(
                            xf + MSAAx8[j * 2],
                            yf + MSAAx8[j * 2 + 1]
                        )
                    }
                    i++
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
        processBalanced2d(0, 0, w, h, tileSize, max(minPerThread / (tileSize * tileSize), 1)) { x0, y0, x1, y1 ->
            for (y in y0 until y1) {
                var i = y * w + x0
                for (x in x0 until x1) {
                    buffer.setElem(i, getRGB(x, y, i))
                    i++
                }
            }
        }
        writeImage(name, img)
    }

    fun writeImageInt(
        w: Int, h: Int, alpha: Boolean,
        name: String,
        pixels: IntArray
    ) {
        val img = BufferedImage(w, h, if (alpha) 2 else 1)
        val buffer = img.raster.dataBuffer
        for (i in 0 until w * h) {
            buffer.setElem(i, pixels[i])
        }
        writeImage(name, img)
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
        map: ColorMap = LinearColorMap.default,
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

    fun writeTriangles(size: Int, name: String, points: List<Vector2f>, indices: IntArray) {

        val bounds = AABBf()
        for (p in points) {
            bounds.union(p.x, p.y, 0f)
        }

        val s = size / max(bounds.deltaX(), bounds.deltaY())

        val ox = bounds.avgX() - (size / 2f) / s
        val oy = bounds.avgY() - (size / 2f) / s

        val bi = BufferedImage(size, size, 1)
        val gfx = bi.graphics as Graphics2D
        val random = Random(1234L)
        gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        // draw all points
        for (p in points) {
            gfx.color = Color(0x777777 or random.nextInt() or (255 shl 24))
            val ax = ((p.x - ox) * s).toInt()
            val ay = ((p.y - oy) * s).toInt()
            gfx.drawOval(ax, ay, 1, 1)
        }
        // draw all triangles
        for (triIndex in 0 until indices.size / 3) {

            gfx.color = Color(0x777777 or random.nextInt() or (255 shl 24))

            val i = triIndex * 3

            val a = points[indices[i]]
            val b = points[indices[i + 1]]
            val c = points[indices[i + 2]]

            val ax = ((a.x - ox) * s).toInt()
            val ay = ((a.y - oy) * s).toInt()
            val bx = ((b.x - ox) * s).toInt()
            val by = ((b.y - oy) * s).toInt()
            val cx = ((c.x - ox) * s).toInt()
            val cy = ((c.y - oy) * s).toInt()

            gfx.drawLine(ax, ay, bx, by)
            gfx.drawLine(bx, by, cx, cy)
            gfx.drawLine(cx, cy, ax, ay)

            gfx.color = Color(gfx.color.rgb and 0x77ffffff, true)
            gfx.fill(Polygon(intArrayOf(ax, bx, cx), intArrayOf(ay, by, cy), 3))

            val px = (ax + bx + cx) / 3
            val py = (ay + by + cy) / 3

            gfx.fillOval(px - 2, py - 2, 5, 5)

        }
        BIImage(bi).write(desktop.getChild(name))
    }

}