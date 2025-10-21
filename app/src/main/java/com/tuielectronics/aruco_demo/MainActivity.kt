package com.tuielectronics.aruco_demo

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import androidx.core.graphics.scale
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity(), CameraProcess.Callback {
    private companion object {
        const val TAG = "[main]"
        const val PERMISSION_REQUEST_CODE = 1

        // ⚠️ THAM SỐ HIỆU CHUẨN CAMERA (VÍ DỤ)
        // Cần khớp với độ phân giải trong CameraProcess.kt (640x480)
        val CAMERA_MATRIX = doubleArrayOf(
            700.0, 0.0, 320.0, // fx, 0, cx (cx = 640/2)
            0.0, 700.0, 240.0,  // 0, fy, cy (cy = 480/2)
            0.0, 0.0, 1.0
        )
        val DISTORTION_COEFFS = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0)
        const val MARKER_LENGTH = 0.05 // 5 cm = 0.05 mét
    }

    private lateinit var cameraFrameLayout: FrameLayout
    private lateinit var imageViewCameraPreview: ImageView
    private lateinit var navView: BottomNavigationView
    private var cameraProcess: CameraProcess? = null
    private var permissionGranted = false

    private val navigationItemSelectedListener =
        BottomNavigationView.OnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    Log.d(TAG, "navigation_home pressed")
                    toggleCamera(false)
                    true
                }
                R.id.navigation_dashboard -> {
                    Log.d(TAG, "navigation_dashboard pressed")
                    toggleCamera(true)
                    true
                }
                else -> false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupWindowFlags()
        checkPermissions()
    }

    private fun initViews() {
        navView = findViewById(R.id.nav_view)
        cameraFrameLayout = findViewById(R.id.content_camera)
        imageViewCameraPreview = findViewById(R.id.image_view_camera_preview)

        navView.setOnNavigationItemSelectedListener(navigationItemSelectedListener)
        imageViewCameraPreview.z = 10f
    }

    private fun setupWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun checkPermissions() {
        permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        if (!permissionGranted) {
            Log.d(TAG, "Permission request")
            requestAppPermission()
        } else {
            initializeCamera()
        }
    }

    private fun initializeCamera() {
        val previewView: PreviewView = findViewById(R.id.preview_view)
        cameraProcess = CameraProcess(this, previewView, this, this)

        if (previewView.parent == null) {
            cameraFrameLayout.addView(previewView)
        }

        // --- GỌI HÀM SET OVERLAY IMAGE VÀ CAMERA PARAMETERS ---
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Gửi ảnh overlay
                // ⚠️ THAY THẾ R.drawable.ic_launcher_background BẰNG ID RESOURCE THỰC TẾ
                cameraProcess?.loadAndSetOverlayImage(R.drawable.test)

                // 2. Gửi tham số camera
                cameraProcess?.setCameraParameters(CAMERA_MATRIX, DISTORTION_COEFFS, MARKER_LENGTH)
                Log.d(TAG, "JNI parameters sent successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send parameters to JNI: ${e.message}")
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUIEvent(event: UIEvent) {
        if (event.type == "camera" && event.topic == "refresh") {
            val bitmap = cameraProcess?.bitmapOpenCVPreview ?: return

            lifecycleScope.launch {
                val scaledBitmap = bitmap.scale(imageViewCameraPreview.width, imageViewCameraPreview.height, true)
                imageViewCameraPreview.setImageBitmap(scaledBitmap)
            }
        }
    }

    override fun updateMarkerPose(poses: List<Marker3DPose>) {
        if (poses.isNotEmpty()) {
            val firstPose = poses.first()

            Log.d(TAG, "--- Marker ${firstPose.id} Pose Update ---")
            Log.d(TAG, "  rvec (X,Y,Z): (%.4f, %.4f, %.4f)".format(firstPose.rvecX, firstPose.rvecY, firstPose.rvecZ))
            Log.d(TAG, "  tvec (X,Y,Z): (%.4f, %.4f, %.4f) meters".format(firstPose.tvecX, firstPose.tvecY, firstPose.tvecZ))
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProcess?.release()
    }

    override fun onBackPressed() {
        createQuitDialog()
    }

    private fun createQuitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Really Exit?")
            .setMessage("Are you sure you want to exit?")
            .setNegativeButton(android.R.string.no, null)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                finish()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask()
                } else {
                    finish()
                }
            }
            .create()
            .show()
    }

    private fun requestAppPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allPermissionsGranted) {
                permissionGranted = true
                initializeCamera()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Failed")
            .setMessage("Camera permission required to run this App")
            .setOnCancelListener { finishApp() }
            .setOnDismissListener { finishApp() }
            .setPositiveButton(android.R.string.yes) { _, _ -> finishApp() }
            .create()
            .show()
    }

    private fun finishApp() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        }
    }

    private fun toggleCamera(open: Boolean) {
        if (!open) {
            cameraProcess?.release()
            cameraProcess = null
        } else if (cameraProcess == null) {
            initializeCamera()
        }
    }
}