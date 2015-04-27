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

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.yubico.yubioath.MainActivity;
import com.yubico.yubioath.R;
import com.yubico.yubioath.exc.StorageFullException;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.UriParser;
import com.yubico.yubioath.model.YubiKeyNeo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/28/13
 * Time: 10:16 AM
 * To change this template use File | Settings | File Templates.
 */
public class AddCodeManualFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener, View.OnClickListener {
    private UriParser u = new UriParser();
    private SwipeDialog swipeDialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.add_code_manual_fragment, container, false);
        view.findViewById(R.id.manual_back).setOnClickListener(this);
        view.findViewById(R.id.manual_add).setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.manual_back:
                closeKeyboard();

                ((MainActivity) getActivity()).openFragment(new SwipeListFragment());

                break;
            case R.id.manual_add:
                String name = ((EditText) getActivity().findViewById(R.id.credential_name)).getText().toString();
                String secret = ((EditText) getActivity().findViewById(R.id.credential_secret)).getText().toString();
                String type = ((Spinner) getActivity().findViewById(R.id.credential_type)).getSelectedItemId() == 0 ? "totp" : "hotp";

                if (name.length() == 0 || secret.length() == 0) {
                    Toast.makeText(getActivity(), R.string.credential_manual_error, Toast.LENGTH_LONG).show();
                } else {
                    closeKeyboard();

                    Uri uri = Uri.parse("otpauth://" + type + "/" + name + "?secret=" + secret);
                    u.parseUri(uri);

                    swipeDialog = new SwipeDialog();
                    swipeDialog.setOnCancel(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            ((MainActivity) getActivity()).openFragment(new SwipeListFragment());
                        }
                    });
                    swipeDialog.show(getFragmentManager(), "dialog");
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
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        DialogFragment dialog = RequirePasswordDialog.newInstance(keyManager, id, missing);
        dialog.show(ft, "dialog");
    }

    @Override
    public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException {
        if (swipeDialog == null) {
            return;
        }

        swipeDialog.dismiss();

        try {
            neo.storeCode(u.name, u.key, (byte) (u.oath_type | u.algorithm_type), u.digits, u.counter);
            long timestamp = System.currentTimeMillis() / 1000 / 30;
            final List<Map<String, String>> codes = neo.getCodes(timestamp);
            Toast.makeText(getActivity(), R.string.prog_success, Toast.LENGTH_LONG).show();
            getActivity().getWindow().getDecorView().getRootView().post(new Runnable() {
                @Override
                public void run() {
                    final SwipeListFragment fragment = new SwipeListFragment();
                    fragment.getCurrent().showCodes(codes);
                    ((MainActivity) getActivity()).openFragment(fragment);
                }
            });
        } catch (StorageFullException e) {
            Toast.makeText(getActivity(), R.string.storage_full, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getActivity(), R.string.tag_error_retry, Toast.LENGTH_LONG).show();
        }
    }
}
