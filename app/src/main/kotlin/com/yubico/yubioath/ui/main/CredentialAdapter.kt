package com.yubico.yubioath.ui.main

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import android.widget.BaseAdapter
import com.yubico.yubioath.R
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.protocol.OathType
import kotlinx.android.synthetic.main.view_credential.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.imageBitmap

class CredentialAdapter(private val context: Context, private val actionHandler: ActionHandler, initialCreds: Map<Credential, Code?> = mapOf()) : BaseAdapter() {
    companion object {
        private const val CREDENTIAL_STORAGE = "CREDENTIAL_STORAGE"
        private const val IS_PINNED = "IS_PINNED"
    }

    private val credentialStorage = context.getSharedPreferences(CREDENTIAL_STORAGE, Context.MODE_PRIVATE)


    private val colors = context.resources.obtainTypedArray(R.array.icon_colors).let {
        (0 until it.length()).map { i -> ColorStateList.valueOf(it.getColor(i, 0)) }
    }

    private val inflater = LayoutInflater.from(context)
    var creds: Map<Credential, Code?> = initialCreds
        private set(value) {
            field = value.toSortedMap(compareBy<Credential> { if(isPinned(it)) 0 else 1 }.thenBy { it.issuer }.thenBy { it.name })
        }

    private var notifyTimeout: Job? = null

    val setCredentials = fun(credentials: Map<Credential, Code?>, searchFilter: String) = launch(UI) {
        creds = credentials.filterKeys { it.key.contains(searchFilter, true) }
        notifyDataSetChanged()
        notifyNextTimeout(credentials)
    }

    private fun Code?.valid(): Boolean = this != null && validUntil > System.currentTimeMillis()
    private fun Code?.canRefresh(): Boolean = this == null || validFrom + 5000 < System.currentTimeMillis()

    private fun Credential.hasTimer(): Boolean = type == OathType.TOTP && period != 30

    private val Credential.color: ColorStateList
        get() = colors[Math.abs(key.hashCode()) % colors.size]

    fun isPinned(credential: Credential): Boolean = credentialStorage.getBoolean("$IS_PINNED/${credential.deviceId}/${credential.key}", false)

    fun setPinned(credential: Credential, value:Boolean) {
        credentialStorage.edit().putBoolean("$IS_PINNED/${credential.deviceId}/${credential.key}", value).apply()
        creds = creds  //Force re-sort
        notifyDataSetChanged()
    }

    private fun Code?.formatValue(): String = when (this?.value?.length) {
        null -> context.getString(R.string.press_for_code)
        8 -> value.slice(0..3) + " " + value.slice(4..7) //1234 5678
        7 -> value.slice(0..3) + " " + value.slice(4..6) //1234 567
        6 -> value.slice(0..2) + " " + value.slice(3..5) //123 456
        else -> value
    }

    private suspend fun notifyNextTimeout(credentials: Map<Credential, Code?>) {
        val now = System.currentTimeMillis()
        val nextTimeout = credentials.map { (cred, code) ->
            if (code != null) when (cred.type) {
                OathType.TOTP -> code.validUntil
                OathType.HOTP -> code.validFrom + 5000  // Redraw HOTP codes after 5 seconds as they can be re-calculated.
            } else -1
        }.filter { it > now }.min()

        nextTimeout?.let {
            notifyTimeout?.cancel()
            notifyTimeout = launch(UI) {
                delay(it - now)
                notifyDataSetChanged()
                notifyNextTimeout(creds)
            }
        }
    }

    fun getPosition(credential: Credential): Int = creds.keys.indexOf(credential)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        return (convertView ?: inflater.inflate(R.layout.view_credential, parent, false).apply {
            (this as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            tag = CodeAdapterViewHolder(this)
        }).apply {
            val (credential, code) = try {
                getItem(position)
            } catch (e: IndexOutOfBoundsException) {
                return null
            }
            with(tag as CodeAdapterViewHolder) {
                fab.imageBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 40f
                        textAlign = Paint.Align.CENTER
                        color = ContextCompat.getColor(context, android.R.color.primary_text_dark)
                    }
                    val letter = (if (credential.issuer.isNullOrEmpty()) credential.name else credential.issuer!!).substring(0, 1).toUpperCase()
                    Canvas(this).drawText(letter, 24f, -paint.ascent(), paint)
                }

                fab.backgroundTintList = credential.color
                issuerView.run {
                    visibility = if (credential.issuer != null) {
                        text = credential.name
                        View.VISIBLE
                    } else View.GONE
                }

                pin_icon.visibility = if (isPinned(credential)) View.VISIBLE else View.GONE

                if (credential.issuer != null) {
                    issuerView.text = credential.issuer
                    issuerView.visibility = View.VISIBLE
                } else {
                    issuerView.visibility = View.GONE
                }
                labelView.text = credential.name

                codeView.text = code.formatValue()
                codeView.isEnabled = code.valid()

                fab.setOnClickListener { actionHandler.select(position) }
                readButton.setOnClickListener { actionHandler.calculate(credential) }
                copyButton.setOnClickListener { code?.let { actionHandler.copy(it) } }
                readButton.visibility = if (credential.type == OathType.HOTP && code.canRefresh() || credential.touch && !code.valid()) View.VISIBLE else View.GONE
                copyButton.visibility = if (code != null) View.VISIBLE else View.GONE

                timeoutBar.run {
                    if (credential.hasTimer()) {
                        visibility = View.VISIBLE
                        if (code != null && code.valid()) {
                            if (animation == null || animation.hasEnded()) {
                                val now = System.currentTimeMillis()
                                startAnimation(timeoutAnimation.apply {
                                    duration = code.validUntil - Math.min(now, code.validFrom)
                                    startOffset = Math.min(0, code.validFrom - now)
                                    setAnimationListener(object : Animation.AnimationListener {
                                        override fun onAnimationStart(animation: Animation?) = Unit
                                        override fun onAnimationRepeat(animation: Animation?) = Unit
                                        override fun onAnimationEnd(animation: Animation?) {
                                            notifyDataSetChanged()
                                        }
                                    })
                                })
                            }
                        } else {
                            clearAnimation()
                            progress = 0
                        }
                    } else {
                        clearAnimation()
                        visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun getItem(position: Int): Map.Entry<Credential, Code?> = creds.entries.toList()[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = creds.size

    private class CodeAdapterViewHolder(view: View) {
        val fab = view.credential_fab!!
        val issuerView = view.issuer!!
        val labelView = view.label!!
        val codeView = view.code!!
        val readButton = view.readButton!!
        val copyButton = view.copyButton!!
        val timeoutBar = view.timeoutBar!!
        val timeoutAnimation = object : Animation() {
            init {
                interpolator = LinearInterpolator()
            }

            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                timeoutBar.progress = ((1.0 - interpolatedTime) * 1000).toInt()
            }
        }
    }

    interface ActionHandler {
        fun select(position: Int)
        fun calculate(credential: Credential)
        fun copy(code: Code)
    }
}