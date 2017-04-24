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

import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.yubico.yubioath.MainActivity
import com.yubico.yubioath.R
import com.yubico.yubioath.model.KeyManager
import com.yubico.yubioath.model.YubiKeyOath
import kotlinx.android.synthetic.main.about_fragment.view.*

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/30/13
 * Time: 10:38 AM
 * To change this template use File | Settings | File Templates.
 */
class AboutFragment : Fragment(), MainActivity.OnYubiKeyListener {
    private var toastInstance: Toast? = null
    private var clearCounter = 5

    private lateinit var keyManager: KeyManager

    private fun setKeyManager(keyManager: KeyManager) {
        this.keyManager = keyManager
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.about_fragment, container, false).apply {
            val version: String = try {
                activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                "unknown"
            }

            with(aboutView) {
                text = Html.fromHtml(getString(R.string.about_text, version))
                movementMethod = LinkMovementMethod.getInstance()
            }

            with(clearData) {
                setOnClickListener {
                    val message: String
                    if (--clearCounter > 0) {
                        message = getString(R.string.clear_countdown, clearCounter)
                    } else {
                        keyManager.clearAll()
                        clearCounter = 5
                        message = getString(R.string.data_cleared)
                    }
                    toastInstance?.cancel()
                    toastInstance = Toast.makeText(activity, message, Toast.LENGTH_SHORT).apply { show() }
                }
            }

        }
    }

    override fun onPasswordMissing(manager: KeyManager, id: ByteArray, missing: Boolean) {
        val ft = fragmentManager.beginTransaction()
        val prev = fragmentManager.findFragmentByTag("dialog")
        prev?.let { ft.remove(it) }
        val dialog = RequirePasswordDialog.newInstance(manager, id, missing)
        dialog.show(ft, "dialog")
    }

    override fun onYubiKey(oath: YubiKeyOath) {
        activity.runOnUiThread {
            val fragment = SwipeListFragment()
            fragment.current.onYubiKey(oath)
            (activity as MainActivity).openFragment(fragment)
        }
    }

    companion object {
        fun newInstance(keyManager: KeyManager): AboutFragment {
            val fragment = AboutFragment()
            fragment.setKeyManager(keyManager)
            return fragment
        }
    }
}
