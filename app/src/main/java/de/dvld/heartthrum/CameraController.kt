package de.dvld.heartthrum

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/** Back camera + torch, emits per-frame mean RGB of the center region.
 *  No frames are stored or leave the device. */
class CameraController(private val context: Context) {

    var onSample: ((time: Double, r: Double, g: Double, b: Double) -> Unit)? = null

    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun start(owner: LifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get()
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { analyze(it) }
            provider?.unbindAll()
            camera = provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            camera?.cameraControl?.enableTorch(true)
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        camera?.cameraControl?.enableTorch(false)
        unlockExposure()
        provider?.unbindAll()
        camera = null
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun lockExposure() {
        val control = camera?.cameraControl ?: return
        Camera2CameraControl.from(control).captureRequestOptions =
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                .build()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun unlockExposure() {
        val control = camera?.cameraControl ?: return
        Camera2CameraControl.from(control).captureRequestOptions =
            CaptureRequestOptions.Builder().build()
    }

    private fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buf = plane.buffer
            val stride = plane.rowStride
            val ps = plane.pixelStride
            val w = image.width
            val h = image.height
            var r = 0.0; var g = 0.0; var b = 0.0; var n = 0
            var y = h / 4
            while (y < 3 * h / 4) {
                val row = y * stride
                var x = w / 4
                while (x < 3 * w / 4) {
                    val o = row + x * ps
                    r += (buf.get(o).toInt() and 0xFF)
                    g += (buf.get(o + 1).toInt() and 0xFF)
                    b += (buf.get(o + 2).toInt() and 0xFF)
                    n++
                    x += 4
                }
                y += 4
            }
            val t = image.imageInfo.timestamp / 1e9
            if (n > 0) onSample?.invoke(t, r / n / 255.0, g / n / 255.0, b / n / 255.0)
        } finally {
            image.close()
        }
    }
}
