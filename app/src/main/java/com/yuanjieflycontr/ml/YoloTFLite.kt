package com.yuanjieflycontr.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

data class DetResult(val box: RectF, val score: Float, val cls: Int)

class YoloTFLite(private val context: Context, private val assetModelPath: String) : ObjectDetector {
    private val interpreter: Interpreter by lazy {
        val opts = Interpreter.Options()
        Interpreter(loadModelFile(context, assetModelPath), opts)
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val afd = context.assets.openFd(assetPath)
        FileInputStream(afd.fileDescriptor).use { fis ->
            val fc = fis.channel
            return fc.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    override fun detect(bitmap: Bitmap, confThreshold: Float, iouThreshold: Float): List<DetResult> {
        val inputTensor = interpreter.getInputTensor(0)
        val inShape = inputTensor.shape()
        val inSize = if (inShape.size >= 3) max(inShape[inShape.size - 1], inShape[inShape.size - 2]) else 640
        val input = preprocess(bitmap, inSize)
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outN = if (outputShape.size == 3) outputShape[1].coerceAtLeast(outputShape[2]) else outputShape.last()
        val outC = if (outputShape.size == 3) outputShape.last().coerceAtLeast(outputShape[1]) else 85
        val out = Array(1) { Array(outN) { FloatArray(outC) } }
        interpreter.run(input, out)
        val preds = mutableListOf<FloatArray>()
        for (i in 0 until outN) {
            val row = out[0][i]
            preds.add(row)
        }
        val results = decode(preds, bitmap.width, bitmap.height, inSize, confThreshold)
        return nonMaxSuppression(results, iouThreshold)
    }

    private data class Prep(val buffer: ByteBuffer, val scale: Float, val dx: Float, val dy: Float, val size: Int)

    private fun preprocess(src: Bitmap, inputSize: Int): ByteBuffer {
        val prep = letterbox(src, inputSize)
        return prep.buffer
    }

    private fun letterbox(src: Bitmap, inputSize: Int): Prep {
        val scale = min(inputSize.toFloat() / src.width, inputSize.toFloat() / src.height)
        val nw = (src.width * scale).toInt()
        val nh = (src.height * scale).toInt()
        val dx = ((inputSize - nw) / 2f)
        val dy = ((inputSize - nh) / 2f)
        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val square = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(square)
        val left = dx
        val top = dy
        c.drawBitmap(resized, left, top, null)
        val bb = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        bb.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        square.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            bb.putFloat(r)
            bb.putFloat(g)
            bb.putFloat(b)
            i++
        }
        bb.rewind()
        return Prep(bb, scale, dx, dy, inputSize)
    }

    private fun decode(rows: List<FloatArray>, imgW: Int, imgH: Int, inputSize: Int, confTh: Float): List<DetResult> {
        val results = mutableListOf<DetResult>()
        val scale = min(inputSize.toFloat() / imgW, inputSize.toFloat() / imgH)
        val dx = ((inputSize - imgW * scale) / 2f)
        val dy = ((inputSize - imgH * scale) / 2f)
        for (row in rows) {
            if (row.size < 6) continue
            val x = row[0]
            val y = row[1]
            val w = row[2]
            val h = row[3]
            val obj = row[4]
            var bestCls = -1
            var bestScore = 0f
            var k = 5
            while (k < row.size) {
                val s = row[k]
                if (s > bestScore) {
                    bestScore = s
                    bestCls = k - 5
                }
                k++
            }
            val score = obj * bestScore
            if (score < confTh) continue
            val cx = x
            val cy = y
            val x0 = (cx - w / 2f - dx) / scale
            val y0 = (cy - h / 2f - dy) / scale
            val x1 = (cx + w / 2f - dx) / scale
            val y1 = (cy + h / 2f - dy) / scale
            val rx0 = max(0f, x0)
            val ry0 = max(0f, y0)
            val rx1 = min(imgW - 1f, x1)
            val ry1 = min(imgH - 1f, y1)
            if (rx1 > rx0 && ry1 > ry0) {
                results.add(DetResult(RectF(rx0, ry0, rx1, ry1), score, bestCls))
            }
        }
        return results
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interL = max(a.left, b.left)
        val interT = max(a.top, b.top)
        val interR = min(a.right, b.right)
        val interB = min(a.bottom, b.bottom)
        val inter = max(0f, interR - interL) * max(0f, interB - interT)
        val ua = a.width() * a.height() + b.width() * b.height() - inter
        return if (ua <= 0f) 0f else inter / ua
    }

    private fun nonMaxSuppression(list: List<DetResult>, iouTh: Float): List<DetResult> {
        val sorted = list.sortedByDescending { it.score }.toMutableList()
        val keep = mutableListOf<DetResult>()
        while (sorted.isNotEmpty()) {
            val a = sorted.removeAt(0)
            keep.add(a)
            val it = sorted.iterator()
            while (it.hasNext()) {
                val b = it.next()
                if (a.cls == b.cls && iou(a.box, b.box) > iouTh) it.remove()
            }
        }
        return keep
    }
}
