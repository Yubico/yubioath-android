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

package com.yubico.yubioath.ui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import com.yubico.yubioath.R
import com.yubico.yubioath.client.KeyManager
import kotlinx.android.synthetic.main.dialog_require_password.view.*

class RequirePasswordDialog : DialogFragment() {
    companion object {
        const private val DEVICE_ID = "deviceId"
        const private val DEVICE_SALT = "deviceSalt"
        const private val MISSING = "missing"

        internal fun newInstance(keyManager: KeyManager, deviceId: String, salt: ByteArray, missing: Boolean): RequirePasswordDialog {
            return RequirePasswordDialog().apply {
                arguments = Bundle().apply {
                    putString(DEVICE_ID, deviceId)
                    putByteArray(DEVICE_SALT, salt)
                    putBoolean(MISSING, missing)
                }
                setKeyManager(keyManager)
            }
        }
    }

    private lateinit var keyManager: KeyManager

    private fun setKeyManager(manager: KeyManager) {
        keyManager = manager
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return arguments!!.let {
            val deviceId = it.getString(DEVICE_ID)
            val salt = it.getByteArray(DEVICE_SALT)
            val missing = it.getBoolean(MISSING)
            AlertDialog.Builder(activity).apply {
                val view = LayoutInflater.from(context).inflate(R.layout.dialog_require_password, null)
                setView(view)
                setTitle(if (missing) R.string.password_required else R.string.password_wrong)
                setPositiveButton(R.string.ok, {_, _ ->
                    val password = view.editPassword.text.toString().trim()
                    val remember = view.rememberPassword.isChecked
                    keyManager.clearKeys(deviceId)
                    keyManager.addKey(deviceId, KeyManager.calculateSecret(password, salt, false), remember)
                    keyManager.addKey(deviceId, KeyManager.calculateSecret(password, salt, true), remember)
                })
                setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
            }.create()
        }
    }
}
