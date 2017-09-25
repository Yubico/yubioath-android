/*
 * Copyright (c) 2013, Yubico AB.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package com.yubico.yubioath.ui.add

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.TextView
import com.yubico.yubioath.R
import com.yubico.yubioath.protocol.Algorithm
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.protocol.YkOathApi
import kotlinx.android.synthetic.main.fragment_add_credential.*
import org.apache.commons.codec.binary.Base32
import org.jetbrains.anko.inputMethodManager

class AddCredentialFragment : Fragment() {
    private val viewModel: AddCredentialViewModel by lazy { ViewModelProviders.of(activity).get(AddCredentialViewModel::class.java) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_add_credential, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        credential_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                credential_period.isEnabled = id == 0L
            }
        }

        credential_period.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && credential_period.text.isEmpty()) {
                credential_period.setText(R.string.period_30, TextView.BufferType.NORMAL)
            }
        }

        credential_period.setSelectAllOnFocus(true)

        viewModel.data?.apply {
            issuer?.let { credential_issuer.setText(it) }
            credential_account.setText(name)
            credential_secret.setText(encodedSecret)
            credential_type.setSelection(when (oathType) {
                OathType.TOTP -> 0
                OathType.HOTP -> 1
            })
            credential_period.setText(period.toString())
            credential_digits.setSelection(digits - 6)
            credential_algo.setSelection(when (algorithm) {
                Algorithm.SHA1 -> 0
                Algorithm.SHA256 -> 1
                Algorithm.SHA512 -> 2
            })
        }

        if (credential_issuer.text.isNullOrEmpty()) {
            activity.inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }
    }

    var isEnabled: Boolean = true
        set(value) {
            if (field != value) {
                listOf(credential_issuer,
                        credential_account,
                        credential_secret,
                        credential_type,
                        credential_period,
                        credential_digits,
                        credential_algo,
                        credential_touch).forEach { it.isEnabled = value }
                field = value
            }
        }

    fun validateData(): CredentialData? {
        activity.currentFocus?.let {
            activity.inputMethodManager.hideSoftInputFromWindow(it.applicationWindowToken, 0)
        }

        credential_touch_wrapper.error = null
        credential_algo_wrapper.error = null

        var valid = true

        val encodedSecret = credential_secret.text.toString().replace(" ", "").toUpperCase()
        val decoder = Base32()
        val secret = if (encodedSecret.isNotEmpty() && decoder.isInAlphabet(encodedSecret)) {
            credential_secret_wrapper.error = null
            decoder.decode(encodedSecret)
        } else {
            credential_secret_wrapper.error = getString(R.string.add_credential_secret_invalid)
            valid = false
            byteArrayOf()
        }
        val issuer = credential_issuer.text.toString().let { if (it.isEmpty()) null else it }
        val name = credential_account.text.toString()
        if (name.isEmpty()) {
            credential_account_wrapper.error = getString(R.string.add_credential_account_empty)
            valid = false
        } else {
            credential_account_wrapper.error = null
        }
        val type = when (credential_type.selectedItemId) {
            0L -> OathType.TOTP
            1L -> OathType.HOTP
            else -> throw IllegalArgumentException("Invalid OATH type!")
        }
        val algo = when (credential_algo.selectedItemId) {
            0L -> Algorithm.SHA1
            1L -> Algorithm.SHA256
            2L -> Algorithm.SHA512
            else -> throw IllegalArgumentException("Invalid hash algorithm!")
        }
        val digits = (6 + credential_digits.selectedItemId).toByte()
        val period = credential_period.text.toString().toInt()
        val touch = credential_touch.isChecked

        return if (valid) {
            CredentialData(secret, issuer, name, type, algo, digits, period, touch = touch)
        } else null
    }

    fun validateVersion(data: CredentialData, version: YkOathApi.Version) {
        // These TextInputLayouts don't contain EditTexts, and need two leading spaces for alignment.
        if (data.touch && version.compare(4, 0, 0) < 0) {
            credential_touch_wrapper.error = "  " + getString(R.string.add_credential_touch_version)
        }
        if (data.algorithm == Algorithm.SHA512 && version.compare(4, 3, 1) < 0) {
            credential_algo_wrapper.error = "  " + getString(R.string.add_credential_algo_512)
        }
    }
}
