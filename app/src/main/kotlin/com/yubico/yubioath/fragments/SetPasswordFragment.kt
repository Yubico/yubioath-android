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

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.yubico.yubioath.MainActivity
import com.yubico.yubioath.R
import com.yubico.yubioath.model.KeyManager
import com.yubico.yubioath.model.YubiKeyOath
import kotlinx.android.synthetic.main.set_password_fragment.view.*
import org.jetbrains.anko.*
import java.io.IOException

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/26/13
 * Time: 1:49 PM
 * To change this template use File | Settings | File Templates.
 */
class SetPasswordFragment : Fragment(), MainActivity.OnYubiKeyNeoListener {
    private lateinit var deviceId: ByteArray
    private var keyManager: KeyManager? = null
    private var needsPassword = false
    private lateinit var swipeDialog: SwipeDialog
    private var needsId = true

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        swipeDialog = SwipeDialog().apply {
            setOnCancel { (activity as MainActivity).openFragment(SwipeListFragment()) }
            show(this@SetPasswordFragment.fragmentManager, "dialog")
        }

        return inflater!!.inflate(R.layout.set_password_fragment, container, false).apply {
            cancelPassword.setOnClickListener { onClick(it) }
            savePassword.setOnClickListener { onClick(it)}
        }
    }

    private fun onClick(v: View) {
        when (v.id) {
            R.id.cancelPassword -> {
                closeKeyboard(activity)
                (activity as MainActivity).openFragment(SwipeListFragment())
            }
            R.id.savePassword -> {
                closeKeyboard(activity)
                view?.apply {
                    if (needsPassword) {
                        val oldPass = editOldPassword.text.toString()
                        keyManager!!.storeSecret(deviceId, KeyManager.calculateSecret(oldPass, deviceId, false), false)
                        keyManager!!.storeSecret(deviceId, KeyManager.calculateSecret(oldPass, deviceId, true), false)
                    }
                    if (editNewPassword.text.toString() == editVerifyPassword.text.toString()) {
                        swipeDialog.show(fragmentManager, "dialog")
                    } else {
                        editNewPassword.setText("")
                        editVerifyPassword.setText("")
                        activity.toast(R.string.password_mismatch)
                    }
                }
            }
        }
    }

    override fun onPasswordMissing(manager: KeyManager, id: ByteArray, missing: Boolean) {
        if (!swipeDialog.isAdded) {
            activity.toast(R.string.input_required)
            return
        }

        swipeDialog.dismiss()

        if (needsId) {
            deviceId = id
            keyManager = manager
            needsPassword = true
            needsId = false
            view!!.requiresPassword.visibility = View.VISIBLE
        } else {
            //Wrong (old) password, try again.
            view?.apply {
                editOldPassword.setText("")
                editOldPassword.requestFocus()
            }
            activity.longToast(R.string.wrong_password)
        }
    }

    override fun onYubiKeyNeo(oath: YubiKeyOath) {
        if (!swipeDialog.isAdded) {
            activity.toast(R.string.input_required)
            return
        }

        swipeDialog.dismiss()

        if (needsId) {
            deviceId = oath.id
            needsId = false
        } else view?.let {
            val newPass = it.editNewPassword.text.toString()
            val remember = it.rememberPassword.isChecked
            try {
                oath.setLockCode(newPass, remember)
                (activity as MainActivity).openFragment(SwipeListFragment())
                activity.toast(R.string.password_updated)
            } catch (e: IOException) {
                Log.e("yubioath", "Set password failed, retry", e)
                activity.runOnUiThread {
                    swipeDialog.show(fragmentManager, "dialog")
                    activity.toast(R.string.tag_error_retry)
                }
            }

        }
    }

    companion object {
        fun closeKeyboard(activity: Activity) {
            activity.currentFocus?.let {
                activity.inputMethodManager.hideSoftInputFromWindow(it.applicationWindowToken, 0)
            }
        }
    }
}
