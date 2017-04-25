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

package com.yubico.yubioath.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.yubico.yubioath.MainActivity
import com.yubico.yubioath.R
import com.yubico.yubioath.exc.StorageFullException
import com.yubico.yubioath.model.CredentialData
import com.yubico.yubioath.model.KeyManager
import com.yubico.yubioath.model.OathType
import com.yubico.yubioath.model.YubiKeyOath
import kotlinx.android.synthetic.main.add_code_manual_fragment.*
import kotlinx.android.synthetic.main.add_code_manual_fragment.view.*
import kotlinx.android.synthetic.main.add_code_scan_fragment.*
import kotlinx.android.synthetic.main.add_code_scan_fragment.view.*
import org.apache.commons.codec.binary.Base32
import org.jetbrains.anko.*

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/28/13
 * Time: 10:16 AM
 * To change this template use File | Settings | File Templates.
 */
class AddAccountFragment : Fragment(), MainActivity.OnYubiKeyListener {
    companion object {
        const private val CODE_URI = "codeUri"

        fun newInstance(uri: String): AddAccountFragment {
            val bundle = Bundle()
            bundle.putString(CODE_URI, uri)
            val fragment = AddAccountFragment()
            fragment.arguments = bundle
            return fragment
        }
    }

    private var swipeDialog: SwipeDialog? = null
    private var manualMode: Boolean = true
    private var data: CredentialData? = null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        try {
            data = arguments?.getString(CODE_URI)?.trim()?.let { CredentialData.from_uri(it) }
        } catch (e: IllegalArgumentException) {
            Log.e("yubioath", "Exception parsing URI", e)
            with(activity as MainActivity) {
                longToast(R.string.invalid_barcode)
                runOnUiThread {
                    openFragment(SwipeListFragment())
                }
            }
        }

        return data?.let {
            manualMode = false

            inflater!!.inflate(R.layout.add_code_scan_fragment, container, false).apply {
                qr_credential_name.setText(it.name)
                scan_back.setOnClickListener { onClick(it) }
                scan_add.setOnClickListener { onClick(it) }
            }
        } ?: inflater!!.inflate(R.layout.add_code_manual_fragment, container, false).apply {
            manual_back.setOnClickListener { onClick(it) }
            manual_add.setOnClickListener { onClick(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).checkForUsbDevice()
    }

    private fun onClick(v: View) {
        when (v.id) {
            R.id.manual_back, R.id.scan_back -> {
                SetPasswordFragment.closeKeyboard(activity)

                (activity as MainActivity).openFragment(SwipeListFragment())
            }
            R.id.manual_add -> {
                val name = credential_name.text.toString()
                val secret = credential_secret.text.toString().toUpperCase()
                val type = if (credential_type.selectedItemId == 0.toLong()) OathType.TOTP else OathType.HOTP

                if (name.isEmpty() || secret.isEmpty()) {
                    activity.longToast(R.string.credential_manual_error)
                } else {
                    SetPasswordFragment.closeKeyboard(activity)

                    val base32 = Base32()
                    val key = when {
                        base32.isInAlphabet(secret) -> base32.decode(secret)
                        else -> throw IllegalArgumentException("Secret must be base32 encoded")
                    }
                    data = CredentialData(key, name, type)

                    swipeDialog = SwipeDialog().apply {
                        setOnCancel { (activity as MainActivity).openFragment(SwipeListFragment()) }
                        show(this@AddAccountFragment.fragmentManager, "dialog")
                    }
                    (activity as MainActivity).checkForUsbDevice()
                }
            }
            R.id.scan_add -> {
                val name = qr_credential_name.text.toString()
                if (name.isEmpty()) {
                    activity.longToast(R.string.credential_invalid_name)
                } else {
                    SetPasswordFragment.closeKeyboard(activity)
                    data?.name = name
                    swipeDialog = SwipeDialog().apply {
                        setOnCancel { (activity as MainActivity).openFragment(SwipeListFragment()) }
                        show(this@AddAccountFragment.fragmentManager, "dialog")
                    }
                    (activity as MainActivity).checkForUsbDevice()
                }
            }
        }
    }

    override fun onPasswordMissing(manager: KeyManager, id: ByteArray, missing: Boolean) {
        val ft = fragmentManager.beginTransaction()
        val prev = fragmentManager.findFragmentByTag("dialog")
        if (swipeDialog != null) {
            swipeDialog!!.dismiss()
        }
        if (prev != null) {
            ft.remove(prev)
        }
        val dialog = RequirePasswordDialog.newInstance(manager, id, missing)
        dialog.show(ft, "dialog")
    }

    override fun onYubiKey(oath: YubiKeyOath) {
        if (swipeDialog == null) {
            return
        }

        with(activity as MainActivity) {
            data?.apply {
                try {
                    when(oathType) {
                        OathType.HOTP -> oath.storeHotp(name, key, algorithm, digits, counter)
                        OathType.TOTP -> oath.storeTotp(name, key, algorithm, digits)
                    }
                    longToast(R.string.prog_success)
                    runOnUiThread {
                        val fragment = SwipeListFragment()
                        fragment.current.onYubiKey(oath)
                        openFragment(fragment)
                    }
                } catch (e: StorageFullException) {
                    longToast(R.string.storage_full)
                } catch (e: Exception) {
                    longToast(R.string.tag_error_retry)
                } finally {
                    swipeDialog?.dismiss()
                }
            }
        }
    }
}
