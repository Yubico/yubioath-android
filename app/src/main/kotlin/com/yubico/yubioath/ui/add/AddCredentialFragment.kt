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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import com.yubico.yubioath.MainActivityOld
import com.yubico.yubioath.R
import com.yubico.yubioath.exc.StorageFullException
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.fragments.RequirePasswordDialog
import com.yubico.yubioath.fragments.SetPasswordFragment
import com.yubico.yubioath.fragments.SwipeDialog
import com.yubico.yubioath.fragments.SwipeListFragment
import com.yubico.yubioath.protocol.Algorithm
import kotlinx.android.synthetic.main.fragment_add_credential.*
import org.apache.commons.codec.binary.Base32
import org.jetbrains.anko.longToast

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/28/13
 * Time: 10:16 AM
 * To change this template use File | Settings | File Templates.
 */
class AddCredentialFragment : Fragment() {
    private val viewModel: AddCredentialViewModel by lazy { ViewModelProviders.of(activity).get(AddCredentialViewModel::class.java) }
    private var swipeDialog: SwipeDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_add_credential, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        credential_type.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Log.d("yubioath", "Item selected: $id")
                credential_period.isEnabled = id == 0L
            }
        }

        credential_period.setSelectAllOnFocus(true)

        viewModel.data?.apply {
            issuer?.let { credential_issuer.setText(it) }
            credential_account.setText(name)
            credential_secret.setText(encodedSecret)
            credential_type.setSelection(when(oathType) {
                OathType.TOTP -> 0
                OathType.HOTP -> 1
            })
            credential_period.setText(period.toString())
            credential_algo.setSelection(when(algorithm) {
                Algorithm.SHA1 -> 0
                Algorithm.SHA256 -> 1
                Algorithm.SHA512 -> 2
            })
        }
    }

    override fun onResume() {
        super.onResume()
    }

    @Deprecated("Refactor away")
    private fun onClick(v: View) {
        when (v.id) {
            R.id.manual_back, R.id.scan_back -> {
                SetPasswordFragment.closeKeyboard(activity)

                (activity as MainActivityOld).openFragment(SwipeListFragment())
            }
            R.id.manual_add -> {
                val name = credential_account.text.toString()
                val secret = credential_secret.text.toString().toUpperCase()
                val type = if (credential_type.selectedItemId == 0.toLong()) OathType.TOTP else OathType.HOTP

                if (name.isEmpty() || secret.isEmpty()) {
                    activity.longToast(R.string.credential_incomplete)
                } else {
                    SetPasswordFragment.closeKeyboard(activity)

                    val base32 = Base32()
                    val key = when {
                        base32.isInAlphabet(secret) -> base32.decode(secret)
                        else -> { activity.longToast(R.string.credential_invalid_secret); return }
                    }
                    viewModel.data = CredentialData(key, null, name, type)

                    swipeDialog = SwipeDialog().apply {
                        setOnCancel { (activity as MainActivityOld).openFragment(SwipeListFragment()) }
                        show(this@AddCredentialFragment.fragmentManager, "dialog")
                    }
                    (activity as MainActivityOld).checkForUsbDevice()
                }
            }
            R.id.scan_add -> {
                val name = credential_account.text.toString()
                if (name.isEmpty()) {
                    activity.longToast(R.string.credential_invalid_name)
                } else {
                    SetPasswordFragment.closeKeyboard(activity)
                    viewModel.data?.name = name
                    swipeDialog = SwipeDialog().apply {
                        setOnCancel { (activity as MainActivityOld).openFragment(SwipeListFragment()) }
                        show(this@AddCredentialFragment.fragmentManager, "dialog")
                    }
                    (activity as MainActivityOld).checkForUsbDevice()
                }
            }
        }
    }

    @Deprecated("Refactor away")
    private fun onPasswordMissing(manager: KeyManager, id: ByteArray, missing: Boolean) {
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

    @Deprecated("Refactor away")
    private fun onYubiKey(oath: OathClient) {
        if (swipeDialog == null) {
            return
        }

        with(activity as MainActivityOld) {
            viewModel.data?.let {
                try {
                    oath.addCredential(it)
                    longToast(R.string.prog_success)
                    val fragment = SwipeListFragment()
                    //fragment.current.onYubiKey(oath)
                    openFragment(fragment)
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
