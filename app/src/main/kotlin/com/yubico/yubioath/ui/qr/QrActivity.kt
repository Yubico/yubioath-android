package com.yubico.yubioath.ui.qr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.yubico.yubioath.R

const val QR_DATA = "QR_DATA"
private const val PERMISSION_CAMERA = 1

class QrActivity : AppCompatActivity() {
    private val cameraPreview: CameraPreview by lazy { findViewById<CameraPreview>(R.id.preview) }
    private var cameraSource: CameraSource? = null

    private val qrProcessor = object : Detector.Processor<Barcode> {
        override fun release() {}

        override fun receiveDetections(detections: Detector.Detections<Barcode>) {
            detections.detectedItems.let {
                if (it.size() > 0) {
                    setResult(Activity.RESULT_OK, Intent().apply { putExtra(QR_DATA, it.valueAt(0)) })
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr)

        findViewById<Button>(R.id.cancel).setOnClickListener { finish() }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        if (code == ConnectionResult.SUCCESS) {
            cameraSource?.let { cameraPreview.cameraSource = it }
        } else {
            GoogleApiAvailability.getInstance().getErrorDialog(this, code, 5).apply {
                setOnDismissListener { finish() }
            }.show()
        }
    }

    override fun onPause() {
        super.onPause()
        cameraSource?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_CAMERA) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                initCamera()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun initCamera() {
        val barcodeDetector = BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE).build()
                .apply { setProcessor(qrProcessor) }
        if (barcodeDetector.isOperational) {
            cameraSource = CameraSource.Builder(this, barcodeDetector).setAutoFocusEnabled(true).build()
        } else {
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_LONG).show()
        }
    }
}
