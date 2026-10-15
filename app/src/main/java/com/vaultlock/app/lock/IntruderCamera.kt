package com.vaultlock.app.lock

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vaultlock.app.app
import com.vaultlock.app.util.Permissions
import java.io.File
import java.util.concurrent.Executors

class IntruderCamera(private val activity: FragmentActivity) {

    private val executor = Executors.newSingleThreadExecutor()
    private var capture: ImageCapture? = null
    private var preparing = false
    private var pending: String? = null
    private var captured = false

    fun prepare() {
        if (capture != null || preparing || !Permissions.hasCamera(activity)) return
        preparing = true
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            preparing = false
            runCatching {
                val provider = future.get()
                if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) return@runCatching
                val useCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
                capture = useCase
                pending?.let {
                    pending = null
                    take(it)
                }
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    fun take(packageName: String) {
        if (captured || !Permissions.hasCamera(activity)) return
        val useCase = capture
        if (useCase == null) {
            pending = packageName
            prepare()
            return
        }
        captured = true
        val file = File(activity.cacheDir, "intruder_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        val repository = activity.app.intruders
        useCase.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                repository.store(file, packageName)
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
            }
        })
    }

    fun reset() {
        captured = false
        pending = null
    }
}
