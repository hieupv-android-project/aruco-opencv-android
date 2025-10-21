//package com.tuielectronics.aruco_demo
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.ImageFormat
//import android.graphics.Matrix
//import android.graphics.PointF
//import android.graphics.Rect
//import android.util.Log
//import android.util.Size
//import androidx.annotation.OptIn
//import androidx.camera.core.Camera
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ExperimentalGetImage
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.ImageProxy
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.LifecycleOwner
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import org.greenrobot.eventbus.EventBus
//import java.io.ByteArrayOutputStream
//import java.nio.ByteBuffer
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//
//data class Marker3DPose(
//    val id: Int,
//    val rvecX: Double, val rvecY: Double, val rvecZ: Double,
//    val tvecX: Double, val tvecY: Double, val tvecZ: Double,
//    val corners: List<PointF>
//)
//
//class CameraProcess(
//    private val context: Context,
//    private val previewView: PreviewView,
//    private val lifecycleOwner: LifecycleOwner,
//    private val callback: Callback
//) {
//    companion object {
//        const val TAG = "CameraProcess"
//        const val CAMERA_WIDTH = 1280
//        const val CAMERA_HEIGHT = 720
//        const val BITMAP_PREVIEW_WIDTH = 1280
//        const val BITMAP_PREVIEW_HEIGHT = 720
//        const val MAX_PREVIEW_FPS_MS = 10L
//        const val MARKER_DATA_SIZE = 15
//
//        init {
//            System.loadLibrary("imageProcess")
//        }
//    }
//
//    private var cameraProvider: ProcessCameraProvider? = null
//    private var camera: Camera? = null
//    private var imageAnalysis: ImageAnalysis? = null
//    private lateinit var cameraExecutor: ExecutorService
//    var bitmapOpenCVPreview: Bitmap? = null
//    private var bitmapOpenCVRaw: Bitmap? = null
//    private var pixelsOpenCV: IntArray? = null
//    private var isProcessing = false
//    private var lastImageRefreshTime = 0L
//
//    init {
//        initializeCamera()
//        initializeBuffers()
//    }
//
//    // --- NATIVE JNI METHODS ---
//    external fun openCVProcess(
//        width: Int, height: Int,
//        NV21FrameData: ByteArray,
//        px: IntArray,
//        px_width: Int,
//        px_height: Int
//    ): DoubleArray
//
//    external fun setCameraParameters(
//        cameraMatrix: DoubleArray, distCoeffs: DoubleArray, markerLength: Double
//    )
//
//    external fun setOverlayImage(imageData: ByteArray, width: Int, height: Int)
//
//    // --- INITIALIZATION ---
//    private fun initializeCamera() {
//        cameraExecutor = Executors.newSingleThreadExecutor()
//
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
//        cameraProviderFuture.addListener({
//            cameraProvider = cameraProviderFuture.get()
//            bindCameraUseCases()
//        }, ContextCompat.getMainExecutor(context))
//    }
//
//    private fun initializeBuffers() {
//        pixelsOpenCV = IntArray(BITMAP_PREVIEW_WIDTH * BITMAP_PREVIEW_HEIGHT)
//        bitmapOpenCVRaw = Bitmap.createBitmap(BITMAP_PREVIEW_WIDTH, BITMAP_PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
//        bitmapOpenCVPreview = Bitmap.createBitmap(BITMAP_PREVIEW_HEIGHT, BITMAP_PREVIEW_WIDTH, Bitmap.Config.ARGB_8888)
//    }
//
//    private fun bindCameraUseCases() {
//        val cameraProvider = cameraProvider ?: return
//        cameraProvider.unbindAll()
//
//        val preview = Preview.Builder()
//            .setTargetResolution(Size(CAMERA_WIDTH, CAMERA_HEIGHT))
//            .build()
//            .also {
//                it.setSurfaceProvider(previewView.surfaceProvider)
//            }
//
//        imageAnalysis = ImageAnalysis.Builder()
//            .setTargetResolution(Size(CAMERA_WIDTH, CAMERA_HEIGHT))
//            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
//            .build()
//            .also {
//                it.setAnalyzer(cameraExecutor) { imageProxy ->
//                    processImage(imageProxy)
//                }
//            }
//
//        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//        try {
//            camera = cameraProvider.bindToLifecycle(
//                lifecycleOwner,
//                cameraSelector,
//                preview,
//                imageAnalysis!!
//            )
//            Log.d(TAG, "Camera bound successfully - Resolution: $CAMERA_WIDTH x $CAMERA_HEIGHT")
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to bind camera use cases", e)
//        }
//    }
//
//    // --- OVERLAY LOGIC ---
//    // Trong class CameraProcess.kt
//
//    fun loadAndSetOverlayImage(resourceId: Int) {
//        var bitmap: Bitmap? = null // Khai báo là nullable
//        try {
//            // 1. Decode resource
//            bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
//
//            if (bitmap == null) {
//                Log.e(TAG, "LỖI: Không thể decode resource ID $resourceId. File có thể không tồn tại hoặc bị hỏng.")
//                return // Thoát khỏi hàm nếu decode thất bại
//            }
//
//            // 3. Xử lý và gửi (Chỉ chạy nếu bitmap không null)
//            val data = getByteArrayFromBitmap(bitmap)
//
//            if (data != null) {
//                // Gọi hàm JNI để gửi dữ liệu ảnh
//                setOverlayImage(data, bitmap.width, bitmap.height)
//                Log.d(TAG, "Overlay image sent to JNI: ${bitmap.width}x${bitmap.height}")
//            }
//        } catch (e: Exception) {
//            // Log lỗi chung, bao gồm cả các lỗi liên quan đến file I/O
//            Log.e(TAG, "Error loading/sending overlay image: ${e.message}", e)
//        } finally {
//            // 4. Recycle bitmap (Chỉ khi nó không null)
//            bitmap?.recycle()
//        }
//    }
//
//    private fun getByteArrayFromBitmap(bitmap: Bitmap): ByteArray? {
//        val stream = ByteArrayOutputStream()
//        // Sử dụng PNG để giữ alpha channel và dễ giải mã trong C++
//        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
//        return stream.toByteArray()
//    }
//
//
//    // --- IMAGE PROCESSING ---
//    private fun processImage(imageProxy: ImageProxy) {
//        if (isProcessing) {
//            imageProxy.close()
//            return
//        }
//
//        isProcessing = true
//
//        CoroutineScope(Dispatchers.Default).launch {
//            try {
//                val nv21Data = yuv420888ToNv21YPlane(imageProxy)
//
//                if (nv21Data != null) {
//                    val arucoPositionArray = pixelsOpenCV?.let {
//                        openCVProcess(
//                            imageProxy.width,
//                            imageProxy.height,
//                            nv21Data,
//                            it,
//                            BITMAP_PREVIEW_WIDTH,
//                            BITMAP_PREVIEW_HEIGHT
//                        )
//                    }
//
//                    if (arucoPositionArray?.isNotEmpty() == true) {
//                        val poseList = processArucoData(arucoPositionArray)
//                        callback.updateMarkerPose(poseList)
//                    }
//
//                    val currentTime = System.currentTimeMillis()
//                    if (currentTime - lastImageRefreshTime >= MAX_PREVIEW_FPS_MS) {
//                        lastImageRefreshTime = currentTime
//                        updatePreviewBitmap()
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error processing image", e)
//            } finally {
//                imageProxy.close()
//                isProcessing = false
//            }
//        }
//    }
//
//    private fun processArucoData(arucoPositionArray: DoubleArray): List<Marker3DPose> {
//        val poseList = mutableListOf<Marker3DPose>()
//        if (arucoPositionArray.size % MARKER_DATA_SIZE != 0) {
//            Log.e(TAG, "Invalid data size from JNI: ${arucoPositionArray.size}")
//            return poseList
//        }
//
//        var i = 0
//        while (i < arucoPositionArray.size) {
//            val id = arucoPositionArray[i].toInt()
//
//            val rvecX = arucoPositionArray[i + 1]
//            val rvecY = arucoPositionArray[i + 2]
//            val rvecZ = arucoPositionArray[i + 3]
//            val tvecX = arucoPositionArray[i + 4]
//            val tvecY = arucoPositionArray[i + 5]
//            val tvecZ = arucoPositionArray[i + 6]
//
//            val corners = listOf(
//                PointF(arucoPositionArray[i + 7].toFloat(), arucoPositionArray[i + 8].toFloat()),
//                PointF(arucoPositionArray[i + 9].toFloat(), arucoPositionArray[i + 10].toFloat()),
//                PointF(arucoPositionArray[i + 11].toFloat(), arucoPositionArray[i + 12].toFloat()),
//                PointF(arucoPositionArray[i + 13].toFloat(), arucoPositionArray[i + 14].toFloat())
//            )
//
//            poseList.add(Marker3DPose(id, rvecX, rvecY, rvecZ, tvecX, tvecY, tvecZ, corners))
//            i += MARKER_DATA_SIZE
//        }
//        return poseList
//    }
//
//
//    @OptIn(ExperimentalGetImage::class)
//    private fun yuv420888ToNv21YPlane(image: ImageProxy): ByteArray? {
//        val mediaImage = image.image ?: return null
//
//        val yPlane = mediaImage.planes[0]
//        val yBuffer: ByteBuffer = yPlane.buffer
//        val ySize = yBuffer.remaining()
//        val nv21Data = ByteArray(ySize)
//
//        yBuffer.get(nv21Data)
//        return nv21Data
//    }
//
//    private fun updatePreviewBitmap() {
//        val pixels = pixelsOpenCV ?: return
//        val rawBitmap = bitmapOpenCVRaw ?: return
//
//        rawBitmap.setPixels(pixels, 0, BITMAP_PREVIEW_WIDTH, 0, 0, BITMAP_PREVIEW_WIDTH, BITMAP_PREVIEW_HEIGHT)
//
//        val matrix = Matrix().apply {
//            preRotate(90f)
//        }
//
//        bitmapOpenCVPreview = Bitmap.createBitmap(
//            rawBitmap, 0, 0,
//            rawBitmap.width, rawBitmap.height, matrix, true
//        )
//
//        EventBus.getDefault().post(UIEvent("camera", "refresh", null, null))
//    }
//
//    fun release() {
//        cameraExecutor.shutdown()
//        imageAnalysis?.clearAnalyzer()
//        cameraProvider?.unbindAll()
//
//        bitmapOpenCVRaw?.recycle()
//        bitmapOpenCVPreview?.recycle()
//
//        bitmapOpenCVRaw = null
//        bitmapOpenCVPreview = null
//        pixelsOpenCV = null
//    }
//
//    interface Callback {
//        fun updateMarkerPose(poses: List<Marker3DPose>)
//    }
//}









//package com.tuielectronics.aruco_demo
//
//import android.Manifest
//import android.app.AlertDialog
//import android.content.pm.PackageManager
//import android.os.Build
//import android.os.Bundle
//import android.util.Log
//import android.view.WindowManager
//import android.widget.FrameLayout
//import android.widget.ImageView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.lifecycleScope
//import com.google.android.material.bottomnavigation.BottomNavigationView
//import kotlinx.coroutines.launch
//import org.greenrobot.eventbus.EventBus
//import org.greenrobot.eventbus.Subscribe
//import org.greenrobot.eventbus.ThreadMode
//import androidx.core.graphics.scale
//import androidx.camera.view.PreviewView
//import kotlinx.coroutines.Dispatchers
//
//class MainActivity : AppCompatActivity(), CameraProcess.Callback {
//    private companion object {
//        const val TAG = "[main]"
//        const val PERMISSION_REQUEST_CODE = 1
//
//        // ⚠️ THAM SỐ HIỆU CHUẨN CAMERA (VÍ DỤ)
//        val CAMERA_MATRIX = doubleArrayOf(
//            1000.0, 0.0, 640.0,
//            0.0, 1000.0, 360.0,
//            0.0, 0.0, 1.0
//        )
//        val DISTORTION_COEFFS = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0)
//        const val MARKER_LENGTH = 0.05 // 5 cm = 0.05 mét
//    }
//
//    private lateinit var cameraFrameLayout: FrameLayout
//    private lateinit var imageViewCameraPreview: ImageView
//    private lateinit var navView: BottomNavigationView
//    private var cameraProcess: CameraProcess? = null
//    private var permissionGranted = false
//
//    private val navigationItemSelectedListener =
//        BottomNavigationView.OnNavigationItemSelectedListener { item ->
//            when (item.itemId) {
//                R.id.navigation_home -> {
//                    Log.d(TAG, "navigation_home pressed")
//                    toggleCamera(false)
//                    true
//                }
//                R.id.navigation_dashboard -> {
//                    Log.d(TAG, "navigation_dashboard pressed")
//                    toggleCamera(true)
//                    true
//                }
//                else -> false
//            }
//        }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        initViews()
//        setupWindowFlags()
//        checkPermissions()
//    }
//
//    private fun initViews() {
//        navView = findViewById(R.id.nav_view)
//        cameraFrameLayout = findViewById(R.id.content_camera)
//        imageViewCameraPreview = findViewById(R.id.image_view_camera_preview)
//
//        navView.setOnNavigationItemSelectedListener(navigationItemSelectedListener)
//        imageViewCameraPreview.z = 10f
//    }
//
//    private fun setupWindowFlags() {
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//    }
//
//    private fun checkPermissions() {
//        permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
//                    PackageManager.PERMISSION_GRANTED
//        } else true
//
//        if (!permissionGranted) {
//            Log.d(TAG, "Permission request")
//            requestAppPermission()
//        } else {
//            initializeCamera()
//        }
//    }
//
//    private fun initializeCamera() {
//        val previewView: PreviewView = findViewById(R.id.preview_view)
//        cameraProcess = CameraProcess(this, previewView, this, this)
//
//        if (previewView.parent == null) {
//            cameraFrameLayout.addView(previewView)
//        }
//
//        // --- GỌI HÀM SET OVERLAY IMAGE VÀ CAMERA PARAMETERS ---
//        lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                // 1. Gửi ảnh overlay
//                // ⚠️ THAY THẾ R.drawable.ic_launcher_background bằng ID resource thực tế của bạn
//                cameraProcess?.loadAndSetOverlayImage(R.drawable.test)
//
//                // 2. Gửi tham số camera
//                cameraProcess?.setCameraParameters(CAMERA_MATRIX, DISTORTION_COEFFS, MARKER_LENGTH)
//                Log.d(TAG, "JNI parameters sent successfully.")
//            } catch (e: Exception) {
//                Log.e(TAG, "Failed to send parameters to JNI: ${e.message}")
//            }
//        }
//    }
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    fun onUIEvent(event: UIEvent) {
//        if (event.type == "camera" && event.topic == "refresh") {
//            val bitmap = cameraProcess?.bitmapOpenCVPreview ?: return
//
//            lifecycleScope.launch {
//                val scaledBitmap = bitmap.scale(imageViewCameraPreview.width, imageViewCameraPreview.height, false)
//                imageViewCameraPreview.setImageBitmap(scaledBitmap)
//            }
//        }
//    }
//
//    override fun updateMarkerPose(poses: List<Marker3DPose>) {
//        if (poses.isNotEmpty()) {
//            val firstPose = poses.first()
//
//            // Xử lý dữ liệu rvec và tvec để gắn model 3D (nếu cần)
//            Log.d(TAG, "--- Marker ${firstPose.id} Pose Update ---")
//            Log.d(TAG, "  rvec (X,Y,Z): (%.4f, %.4f, %.4f)".format(firstPose.rvecX, firstPose.rvecY, firstPose.rvecZ))
//            Log.d(TAG, "  tvec (X,Y,Z): (%.4f, %.4f, %.4f) meters".format(firstPose.tvecX, firstPose.tvecY, firstPose.tvecZ))
//        }
//    }
//
//    override fun onStart() {
//        super.onStart()
//        EventBus.getDefault().register(this)
//    }
//
//    override fun onStop() {
//        super.onStop()
//        EventBus.getDefault().unregister(this)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraProcess?.release()
//    }
//
//    override fun onBackPressed() {
//        createQuitDialog()
//    }
//
//    private fun createQuitDialog() {
//        AlertDialog.Builder(this)
//            .setTitle("Really Exit?")
//            .setMessage("Are you sure you want to exit?")
//            .setNegativeButton(android.R.string.no, null)
//            .setPositiveButton(android.R.string.yes) { _, _ ->
//                finish()
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    finishAndRemoveTask()
//                } else {
//                    finish()
//                }
//            }
//            .create()
//            .show()
//    }
//
//    private fun requestAppPermission() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE)
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == PERMISSION_REQUEST_CODE) {
//            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
//            if (allPermissionsGranted) {
//                permissionGranted = true
//                initializeCamera()
//            } else {
//                showPermissionDeniedDialog()
//            }
//        }
//    }
//
//    private fun showPermissionDeniedDialog() {
//        AlertDialog.Builder(this)
//            .setTitle("Permission Failed")
//            .setMessage("Camera permission required to run this App")
//            .setOnCancelListener { finishApp() }
//            .setOnDismissListener { finishApp() }
//            .setPositiveButton(android.R.string.yes) { _, _ -> finishApp() }
//            .create()
//            .show()
//    }
//
//    private fun finishApp() {
//        finish()
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            finishAndRemoveTask()
//        }
//    }
//
//    private fun toggleCamera(open: Boolean) {
//        if (!open) {
//            cameraProcess?.release()
//            cameraProcess = null
//        } else if (cameraProcess == null) {
//            initializeCamera()
//        }
//    }
//}