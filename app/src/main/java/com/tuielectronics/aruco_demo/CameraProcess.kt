package com.tuielectronics.aruco_demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Marker3DPose(
    val id: Int,
    val rvecX: Double, val rvecY: Double, val rvecZ: Double,
    val tvecX: Double, val tvecY: Double, val tvecZ: Double,
    val corners: List<PointF>
)

class CameraProcess(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val callback: Callback
) {
    companion object {
        const val TAG = "CameraProcess"
        // Độ phân giải xử lý (Tối ưu hóa: 640x480 để giảm lag)
//        const val CAMERA_WIDTH = 1280
//        const val CAMERA_HEIGHT = 720
        const val CAMERA_WIDTH = 640
        const val CAMERA_HEIGHT = 480
        // Kích thước Bitmap trung gian (Phải khớp với CAMERA_WIDTH/HEIGHT)
//        const val BITMAP_PREVIEW_WIDTH = 1280
//        const val BITMAP_PREVIEW_HEIGHT = 720
        const val BITMAP_PREVIEW_WIDTH = 640
        const val BITMAP_PREVIEW_HEIGHT = 480
        // Tốc độ cập nhật UI (30ms = 33 FPS)
        const val MAX_PREVIEW_FPS_MS = 30L
        const val MARKER_DATA_SIZE = 15

        init {
            System.loadLibrary("imageProcess")
        }
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    var bitmapOpenCVPreview: Bitmap? = null
    private var bitmapOpenCVRaw: Bitmap? = null
    private var pixelsOpenCV: IntArray? = null
    private var isProcessing = false
    private var lastImageRefreshTime = 0L

    init {
        initializeCamera()
        initializeBuffers()
    }

    // --- NATIVE JNI METHODS ---
    external fun openCVProcess(
        width: Int, height: Int,
        NV21FrameData: ByteArray,
        px: IntArray,
        px_width: Int,
        px_height: Int
    ): DoubleArray

    external fun setCameraParameters(
        cameraMatrix: DoubleArray, distCoeffs: DoubleArray, markerLength: Double
    )

    external fun setOverlayImage(imageData: ByteArray, width: Int, height: Int)

    // --- INITIALIZATION ---
    private fun initializeCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun initializeBuffers() {
        pixelsOpenCV = IntArray(BITMAP_PREVIEW_WIDTH * BITMAP_PREVIEW_HEIGHT)
        bitmapOpenCVRaw = Bitmap.createBitmap(BITMAP_PREVIEW_WIDTH, BITMAP_PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
        bitmapOpenCVPreview = Bitmap.createBitmap(BITMAP_PREVIEW_HEIGHT, BITMAP_PREVIEW_WIDTH, Bitmap.Config.ARGB_8888)
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        cameraProvider.unbindAll()

        val preview = Preview.Builder()
            .setTargetResolution(Size(CAMERA_WIDTH, CAMERA_HEIGHT))
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(CAMERA_WIDTH, CAMERA_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis!!
            )
            Log.d(TAG, "Camera bound successfully - Resolution: $CAMERA_WIDTH x $CAMERA_HEIGHT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    // --- OVERLAY LOGIC ---
    fun loadAndSetOverlayImage(resourceId: Int) {
        var bitmap: Bitmap? = null
        try {
            bitmap = BitmapFactory.decodeResource(context.resources, resourceId)

            if (bitmap == null) {
                Log.e(TAG, "LỖI: Không thể decode resource ID $resourceId.")
                return
            }

            val data = getByteArrayFromBitmap(bitmap)

            if (data != null) {
                setOverlayImage(data, bitmap.width, bitmap.height)
                Log.d(TAG, "Overlay image sent to JNI: ${bitmap.width}x${bitmap.height}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading/sending overlay image: ${e.message}", e)
        } finally {
            bitmap?.recycle()
        }
    }

    private fun getByteArrayFromBitmap(bitmap: Bitmap): ByteArray? {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }


    // --- IMAGE PROCESSING ---
    private fun processImage(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val nv21Data = yuv420888ToNv21YPlane(imageProxy)

                if (nv21Data != null) {
                    val arucoPositionArray = pixelsOpenCV?.let {
                        openCVProcess(
                            imageProxy.width,
                            imageProxy.height,
                            nv21Data,
                            it,
                            BITMAP_PREVIEW_WIDTH,
                            BITMAP_PREVIEW_HEIGHT
                        )
                    }

                    if (arucoPositionArray?.isNotEmpty() == true) {
                        val poseList = processArucoData(arucoPositionArray)
                        callback.updateMarkerPose(poseList)
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastImageRefreshTime >= MAX_PREVIEW_FPS_MS) {
                        lastImageRefreshTime = currentTime
                        updatePreviewBitmap()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
            } finally {
                imageProxy.close()
                isProcessing = false
            }
        }
    }

    private fun processArucoData(arucoPositionArray: DoubleArray): List<Marker3DPose> {
        val poseList = mutableListOf<Marker3DPose>()
        if (arucoPositionArray.size % MARKER_DATA_SIZE != 0) {
            Log.e(TAG, "Invalid data size from JNI: ${arucoPositionArray.size}")
            return poseList
        }

        var i = 0
        while (i < arucoPositionArray.size) {
            val id = arucoPositionArray[i].toInt()

            val rvecX = arucoPositionArray[i + 1]
            val rvecY = arucoPositionArray[i + 2]
            val rvecZ = arucoPositionArray[i + 3]
            val tvecX = arucoPositionArray[i + 4]
            val tvecY = arucoPositionArray[i + 5]
            val tvecZ = arucoPositionArray[i + 6]

            val corners = listOf(
                PointF(arucoPositionArray[i + 7].toFloat(), arucoPositionArray[i + 8].toFloat()),
                PointF(arucoPositionArray[i + 9].toFloat(), arucoPositionArray[i + 10].toFloat()),
                PointF(arucoPositionArray[i + 11].toFloat(), arucoPositionArray[i + 12].toFloat()),
                PointF(arucoPositionArray[i + 13].toFloat(), arucoPositionArray[i + 14].toFloat())
            )

            poseList.add(Marker3DPose(id, rvecX, rvecY, rvecZ, tvecX, tvecY, tvecZ, corners))
            i += MARKER_DATA_SIZE
        }
        return poseList
    }

    @OptIn(ExperimentalGetImage::class)
    private fun yuv420888ToNv21YPlane(image: ImageProxy): ByteArray? {
        val mediaImage = image.image ?: return null

        val yPlane = mediaImage.planes[0]
        val yBuffer: ByteBuffer = yPlane.buffer
        val ySize = yBuffer.remaining()
        val nv21Data = ByteArray(ySize)

        yBuffer.get(nv21Data)
        return nv21Data
    }

    private fun updatePreviewBitmap() {
        val pixels = pixelsOpenCV ?: return
        val rawBitmap = bitmapOpenCVRaw ?: return

        rawBitmap.setPixels(pixels, 0, BITMAP_PREVIEW_WIDTH, 0, 0, BITMAP_PREVIEW_WIDTH, BITMAP_PREVIEW_HEIGHT)

        val matrix = Matrix().apply {
            preRotate(90f)
        }

        bitmapOpenCVPreview = Bitmap.createBitmap(
            rawBitmap, 0, 0,
            rawBitmap.width, rawBitmap.height, matrix, true
        )

        EventBus.getDefault().post(UIEvent("camera", "refresh", null, null))
    }

    fun release() {
        cameraExecutor.shutdown()
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()

        bitmapOpenCVRaw?.recycle()
        bitmapOpenCVPreview?.recycle()

        bitmapOpenCVRaw = null
        bitmapOpenCVPreview = null
        pixelsOpenCV = null
    }

    interface Callback {
        fun updateMarkerPose(poses: List<Marker3DPose>)
    }
}