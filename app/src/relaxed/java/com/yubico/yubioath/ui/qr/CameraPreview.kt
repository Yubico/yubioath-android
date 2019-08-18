package com.yubico.yubioath.ui.qr

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import com.google.android.gms.vision.CameraSource

class CameraPreview(context: Context, attributeSet: AttributeSet? = null) : ViewGroup(context, attributeSet), SurfaceHolder.Callback {
    private val surfaceView = SurfaceView(context).apply {
        addView(this)
        holder.addCallback(this@CameraPreview)
    }
    private var hasSurface = false
    var cameraSource: CameraSource? = null
        @Throws(SecurityException::class)
        set(value) {
            field = value
            if (hasSurface && value != null) {
                value.start(surfaceView.holder)
                requestLayout()
            }
        }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        hasSurface = false
    }

    @Throws(SecurityException::class)
    override fun surfaceCreated(holder: SurfaceHolder?) {
        hasSurface = true
        cameraSource?.start(surfaceView.holder)
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var w = right - left
        var h = bottom - top

        cameraSource?.let {
            val previewSize = it.previewSize
            if (previewSize != null) {
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    h = previewSize.width
                    w = previewSize.height
                } else {
                    w = previewSize.width
                    h = previewSize.height
                }
            }
        }

        var cw = right - left
        var ch = h * cw / w
        if (ch < bottom - top) {
            ch = bottom - top
            cw = w * ch / h
        }
        val offset = (right - left - cw) / 2
        getChildAt(0).layout(offset, 0, offset + cw, ch)
    }
}