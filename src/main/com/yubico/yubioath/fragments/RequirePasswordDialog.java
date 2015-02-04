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

package com.yubico.yubioath.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import com.yubico.yubioath.R;
import com.yubico.yubioath.model.KeyManager;

import java.util.Arrays;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/26/13
 * Time: 1:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class RequirePasswordDialog extends DialogFragment {
    private static final String DEVICE_ID = "deviceId";
    private static final String MISSING = "missing";

    static RequirePasswordDialog newInstance(KeyManager keyManager, byte[] id, boolean missing) {
        Bundle bundle = new Bundle();
        bundle.putByteArray(DEVICE_ID, id);
        bundle.putBoolean(MISSING, missing);
        RequirePasswordDialog dialog = new RequirePasswordDialog();
        dialog.setArguments(bundle);
        dialog.setKeyManager(keyManager);
        return dialog;
    }

    private KeyManager keyManager;

    private void setKeyManager(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final byte[] id = getArguments().getByteArray(DEVICE_ID);
        final boolean missing = getArguments().getBoolean(MISSING);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.require_password_dialog, null);
        final EditText editDisplayName = (EditText) view.findViewById(R.id.editDisplayName);
        final EditText editPassword = (EditText) view.findViewById(R.id.editPassword);
        editDisplayName.setText(keyManager.getDisplayName(id, getString(R.string.yubikey_neo)));

        return new AlertDialog.Builder(getActivity())
                .setTitle(missing ? R.string.password_required : R.string.wrong_password)
                .setView(view)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String label = editDisplayName.getText().toString().trim();
                        String password = editPassword.getText().toString().trim();
                        boolean remember = ((CheckBox) view.findViewById(R.id.rememberPassword)).isChecked();
                        keyManager.setDisplayName(id, label);
                        keyManager.storeSecret(id, KeyManager.calculateSecret(password, id, false), remember);
                        keyManager.storeSecret(id, KeyManager.calculateSecret(password, id, true), remember);
                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).create();
    }
}
