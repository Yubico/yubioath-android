package com.yubico.yubioath.ui.main.list

import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_credential.view.*

class CodeAdapterRecyclerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val icon = view.credential_icon!!
    val pinIcon = view.pin_icon!!
    val issuerView = view.issuer!!
    val labelView = view.label!!
    val codeView = view.code!!
    val readButton = view.readButton!!
    val copyButton = view.copyButton!!
    val showButton = view.showButton!!
    val timeoutBar = view.timeoutBar!!
    val timeoutAnimation = object : Animation() {

        init {
            interpolator = LinearInterpolator()
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
            timeoutBar.progress = ((1.0 - interpolatedTime) * 1000).toInt()
        }
    }
    var timeoutAt: Long = 0
}