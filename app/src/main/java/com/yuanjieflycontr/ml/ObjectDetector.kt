package com.yuanjieflycontr.ml

import android.graphics.Bitmap

interface ObjectDetector {
    fun detect(bitmap: Bitmap, confThreshold: Float, iouThreshold: Float): List<DetResult>

    fun close() {}
}
