package com.tuielectronics.aruco_demo

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class ArucoAnalyzer(
    private val frameProcessor: (ByteArray, Int, Int) -> Unit // Lambda để gọi JNI
) : ImageAnalysis.Analyzer {

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // CameraX thường cung cấp ảnh định dạng YUV_420_888.
            // Plane[0] là Y plane, tương ứng với ảnh grayscale (CV_8UC1)
            // tương tự như NV21 Y-plane mà code C++ của bạn đang sử dụng.

            val yBuffer = mediaImage.planes[0].buffer
            val ySize = yBuffer.remaining()
            val nv21Data = ByteArray(ySize)
            yBuffer.get(nv21Data)

            val width = mediaImage.width
            val height = mediaImage.height

            // Gửi dữ liệu ảnh Y (NV21 Y-plane) tới C++
            frameProcessor(nv21Data, width, height)
        }

        // Luôn đóng ImageProxy để giải phóng buffer cho frame tiếp theo
        imageProxy.close()
    }
}