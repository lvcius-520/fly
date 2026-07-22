package com.yuanjieflycontr.ml

import android.content.Context
import android.graphics.Bitmap

class YoloNcnn(private val context: Context, private val assetDir: String) : ObjectDetector {
    private var nativeHandle: Long = nativeCreate(
        context.assets,
        "$assetDir/model.ncnn.param",
        "$assetDir/model.ncnn.bin"
    )

    init {
        check(nativeHandle != 0L) { "NCNN 模型初始化失败" }
    }

    override fun detect(bitmap: Bitmap, confThreshold: Float, iouThreshold: Float): List<DetResult> {
        val handle = nativeHandle
        check(handle != 0L) { "NCNN 检测器已释放" }
        val raw = nativeDetect(handle, bitmap, confThreshold, iouThreshold)
        if (raw.isEmpty()) return emptyList()

        val results = ArrayList<DetResult>(raw.size / 6)
        var i = 0
        while (i + 5 < raw.size) {
            results += DetResult(
                box = android.graphics.RectF(raw[i], raw[i + 1], raw[i + 2], raw[i + 3]),
                score = raw[i + 4],
                cls = raw[i + 5].toInt()
            )
            i += 6
        }
        return results
    }

    override fun close() {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeDestroy(handle)
            nativeHandle = 0L
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        close()
    }

    private external fun nativeCreate(
        assetManager: android.content.res.AssetManager,
        paramPath: String,
        binPath: String
    ): Long

    private external fun nativeDetect(
        handle: Long,
        bitmap: Bitmap,
        confThreshold: Float,
        iouThreshold: Float
    ): FloatArray

    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("yolo_ncnn_jni")
        }
    }
}
