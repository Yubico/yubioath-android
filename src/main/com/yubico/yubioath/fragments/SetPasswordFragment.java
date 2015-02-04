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

import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.yubico.yubioath.MainActivity;
import com.yubico.yubioath.R;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.YubiKeyNeo;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/26/13
 * Time: 1:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class SetPasswordFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener, View.OnClickListener {
    private byte[] id = null;
    private KeyManager keyManager;
    private boolean needsPassword = false;
    private SwipeDialog swipeDialog;
    private boolean needsId = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.set_password_fragment, container, false);
        view.findViewById(R.id.cancelPassword).setOnClickListener(this);
        view.findViewById(R.id.savePassword).setOnClickListener(this);

        swipeDialog = new SwipeDialog();
        swipeDialog.setOnCancel(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                ((MainActivity) getActivity()).openFragment(new SwipeListFragment());
            }
        });
        swipeDialog.show(getFragmentManager(), "dialog");

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancelPassword:
                closeKeyboard();
                ((MainActivity) getActivity()).openFragment(new SwipeListFragment());
                break;
            case R.id.savePassword:
                closeKeyboard();
                if (needsPassword) {
                    String oldPass = ((EditText) getView().findViewById(R.id.editOldPassword)).getText().toString();
                    keyManager.storeSecret(id, KeyManager.calculateSecret(oldPass, id, false), false);
                    keyManager.storeSecret(id, KeyManager.calculateSecret(oldPass, id, true), false);
                }
                EditText newPassword = (EditText) getView().findViewById(R.id.editNewPassword);
                EditText verifyPassword = (EditText) getView().findViewById(R.id.editVerifyPassword);
                if (newPassword.getText().toString().equals(verifyPassword.getText().toString())) {
                    swipeDialog.show(getFragmentManager(), "dialog");
                } else {
                    newPassword.setText("");
                    verifyPassword.setText("");
                    Toast.makeText(getActivity(), R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void closeKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (getActivity().getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getApplicationWindowToken(), 0);
        }
    }

    @Override
    public void onPasswordMissing(KeyManager keyManager, byte[] id, boolean missing) {
        if (!swipeDialog.isAdded()) {
            Toast.makeText(getActivity(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        swipeDialog.dismiss();

        if (needsId) {
            this.id = id;
            this.keyManager = keyManager;
            needsPassword = true;
            needsId = false;
            ((EditText) getView().findViewById(R.id.editDisplayName)).setText(R.string.yubikey_neo);
            getView().findViewById(R.id.requiresPassword).setVisibility(View.VISIBLE);
        } else {
            //Wrong (old) password, try again.
            EditText editOldPassword = (EditText) getView().findViewById(R.id.editOldPassword);
            editOldPassword.setText("");
            editOldPassword.requestFocus();
            Toast.makeText(getActivity(), R.string.wrong_password, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException {
        if (!swipeDialog.isAdded()) {
            Toast.makeText(getActivity(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        swipeDialog.dismiss();

        if (needsId) {
            id = neo.getId();
            ((EditText) getView().findViewById(R.id.editDisplayName)).setText(neo.getDisplayName(getString(R.string.yubikey_neo)));
            needsId = false;
        } else {
            String label = ((EditText) getView().findViewById(R.id.editDisplayName)).getText().toString().trim();
            String newPass = ((EditText) getView().findViewById(R.id.editNewPassword)).getText().toString();
            boolean remember = ((CheckBox) getView().findViewById(R.id.rememberPassword)).isChecked();
            neo.setDisplayName(label);
            try {
                neo.setLockCode(newPass, remember);
                ((MainActivity) getActivity()).openFragment(new SwipeListFragment());
                Toast.makeText(getActivity(), R.string.password_updated, Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("yubioath", "Set password failed, retry", e);
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        swipeDialog.show(getFragmentManager(), "dialog");
                        Toast.makeText(getActivity(), R.string.tag_error_retry, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}
