package com.yubico.yubioath.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import android.widget.BaseAdapter
import com.pixplicity.sharp.Sharp
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

    private val iconManager = IconManager(context)
    private val inflater = LayoutInflater.from(context)
    var creds: Map<Credential, Code?> = initialCreds
        private set(value) {
            field = value.toSortedMap(
                    compareBy<Credential> { if (isPinned(it)) 0 else 1 }
                            .thenBy { it.issuer?.toLowerCase() ?: it.name.toLowerCase() }
                            .thenBy { it.name.toLowerCase() }
            )
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

    fun isPinned(credential: Credential): Boolean = credentialStorage.getBoolean("$IS_PINNED/${credential.deviceId}/${credential.key}", false)

    fun setPinned(credential: Credential, value: Boolean) {
        credentialStorage.edit().putBoolean("$IS_PINNED/${credential.deviceId}/${credential.key}", value).apply()
        creds = creds  //Force re-sort
        notifyDataSetChanged()
    }

    fun hasIcon(credential: Credential): Boolean = iconManager.hasIcon(credential)

    fun setIcon(credential: Credential, icon: Bitmap) = iconManager.setIcon(credential, icon)
    fun setIcon(credential: Credential, icon: Drawable) = iconManager.setIcon(credential, icon)

    fun removeIcon(credential: Credential) = iconManager.removeIcon(credential)

    private fun Code?.formatValue(): String = when (this?.value?.length) {
        null -> context.getString(R.string.press_for_code)
        8 -> value.slice(0..3) + " " + value.slice(4..7) //1234 5678
        7 -> value.slice(0..3) + " " + value.slice(4..6) //1234 567
        6 -> value.slice(0..2) + " " + value.slice(3..5) //123 456
        in 9..20 -> value
        else -> "${value.substring(0, 12)}…"
    }

    private suspend fun notifyNextTimeout(credentials: Map<Credential, Code?>) {
        val now = System.currentTimeMillis()
        val nextTimeout = credentials.map { (cred, code) ->
            if (code != null) when (cred.type) {
                OathType.TOTP -> code.validUntil
                OathType.HOTP -> code.validFrom + 5000  // Redraw HOTP codes after 5 seconds as they can be re-calculated.
                null -> Long.MAX_VALUE
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
                icon.imageBitmap = iconManager.getIcon(credential)

                issuerView.run {
                    visibility = if (credential.issuer != null) {
                        text = credential.name
                        View.VISIBLE
                    } else View.GONE
                }

                pinIcon.visibility = if (isPinned(credential)) View.VISIBLE else View.GONE

                if (credential.issuer != null) {
                    issuerView.text = credential.issuer
                    issuerView.visibility = View.VISIBLE
                } else {
                    issuerView.visibility = View.GONE
                }
                labelView.text = credential.name

                codeView.text = code.formatValue()
                codeView.isEnabled = code.valid()

                icon.setOnClickListener { actionHandler.select(position) }
                readButton.setOnClickListener { actionHandler.calculate(credential) }
                copyButton.setOnClickListener { code?.let { actionHandler.copy(it) } }
                readButton.visibility = if (credential.type == OathType.HOTP && code.canRefresh() || credential.touch && !code.valid()) View.VISIBLE else View.GONE
                copyButton.visibility = if (code != null) View.VISIBLE else View.GONE

                timeoutBar.apply {
                    if (credential.hasTimer()) {
                        visibility = View.VISIBLE
                        if (code != null && code.valid()) {
                            if (animation == null || animation.hasEnded() || timeoutAt != code.validUntil) {
                                val now = System.currentTimeMillis()
                                timeoutAt = code.validUntil
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
                            timeoutAt = 0
                        }
                    } else {
                        clearAnimation()
                        visibility = View.GONE
                        timeoutAt = 0
                    }
                }
            }
        }
    }

    override fun getItem(position: Int): Map.Entry<Credential, Code?> = creds.entries.toList()[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = creds.size

    private class CodeAdapterViewHolder(view: View) {
        val icon = view.credential_icon!!
        val pinIcon = view.pin_icon!!
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
        var timeoutAt: Long = 0
    }

    interface ActionHandler {
        fun select(position: Int)
        fun calculate(credential: Credential)
        fun copy(code: Code)
    }
}