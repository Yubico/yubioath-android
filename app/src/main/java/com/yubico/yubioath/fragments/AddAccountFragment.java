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

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.yubico.yubioath.MainActivity;
import com.yubico.yubioath.R;
import com.yubico.yubioath.exc.StorageFullException;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.YubiKeyNeo;
import org.apache.commons.codec.binary.Base32;

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
public class AddAccountFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener, View.OnClickListener {
    private static final String CODE_URI = "codeUri";
    private String name;
    private byte[] key;
    private byte oath_type;
    private byte algorithm_type;
    private int digits;
    private int counter;
    private SwipeDialog swipeDialog;
    private Boolean manualMode;

    public static AddAccountFragment newInstance(String uri) {
        Bundle bundle = new Bundle();
        bundle.putString(CODE_URI, uri);
        AddAccountFragment fragment = new AddAccountFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view;
        Bundle args = getArguments();

        if (args == null) {
            manualMode = true;

            view = inflater.inflate(R.layout.add_code_manual_fragment, container, false);
            view.findViewById(R.id.manual_back).setOnClickListener(this);
            view.findViewById(R.id.manual_add).setOnClickListener(this);
        } else {
            manualMode = false;

            Uri uri = Uri.parse(getArguments().getString(CODE_URI).trim());

            view = inflater.inflate(R.layout.add_code_scan_fragment, container, false);
            if (parseUri(uri)) {
                ((TextView) view.findViewById(R.id.name)).setText(name);
            } else {
                Toast.makeText(getActivity(), R.string.invalid_barcode, Toast.LENGTH_LONG).show();
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        ((MainActivity) getActivity()).openFragment(new SwipeListFragment());
                    }
                });
            }
        }

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.manual_back:
                SetPasswordFragment.closeKeyboard(getActivity());

                ((MainActivity) getActivity()).openFragment(new SwipeListFragment());

                break;
            case R.id.manual_add:
                String name = ((EditText) getActivity().findViewById(R.id.credential_name)).getText().toString();
                String secret = ((EditText) getActivity().findViewById(R.id.credential_secret)).getText().toString();
                String type = ((Spinner) getActivity().findViewById(R.id.credential_type)).getSelectedItemId() == 0 ? "totp" : "hotp";

                if (name.length() == 0 || secret.length() == 0) {
                    Toast.makeText(getActivity(), R.string.credential_manual_error, Toast.LENGTH_LONG).show();
                } else {
                    SetPasswordFragment.closeKeyboard(getActivity());

                    Uri uri = Uri.parse("otpauth://" + type + "/" + name + "?secret=" + secret);
                    parseUri(uri);

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

    protected boolean parseUri(Uri uri) {
        String scheme = uri.getScheme();
        if (!uri.isHierarchical() || scheme == null || !scheme.equals("otpauth")) {
            return false;
        }

        String secret = uri.getQueryParameter("secret");
        if (secret == null || secret.isEmpty()) {
            return false;
        }
        Base32 base32 = new Base32();
        if (!base32.isInAlphabet(secret.toUpperCase())) {
            return false;
        }
        key = base32.decode(secret.toUpperCase());

        String path = uri.getPath(); // user name is stored in path...
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        if (path.length() > 64) {
            path = path.substring(0, 64);
        }
        name = path;

        String typeString = uri.getHost(); // type stored in host, totp/hotp
        if (typeString.equals("totp")) {
            oath_type = YubiKeyNeo.TOTP_TYPE;
        } else if (typeString.equals("hotp")) {
            oath_type = YubiKeyNeo.HOTP_TYPE;
        } else {
            return false;
        }

        String algorithm = uri.getQueryParameter("algorithm");
        if (algorithm == null || algorithm.isEmpty() || algorithm.toUpperCase().equals("SHA1")) {
            algorithm_type = YubiKeyNeo.HMAC_SHA1;
        } else if (algorithm.toUpperCase().equals("SHA256")) {
            algorithm_type = YubiKeyNeo.HMAC_SHA256;
        } else {
            return false;
        }

        String digit_string = uri.getQueryParameter("digits");
        if (digit_string == null || digit_string.isEmpty()) {
            digits = 6;
        } else {
            try {
                digits = Integer.parseInt(digit_string);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        String counter_string = uri.getQueryParameter("counter");
        if (counter_string == null || counter_string.isEmpty()) {
            counter = 0;
        } else {
            try {
                counter = Integer.parseInt(counter_string);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void onPasswordMissing(KeyManager keyManager, byte[] id, boolean missing) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (swipeDialog != null) {
            swipeDialog.dismiss();
        }
        if (prev != null) {
            ft.remove(prev);
        }
        DialogFragment dialog = RequirePasswordDialog.newInstance(keyManager, id, missing);
        dialog.show(ft, "dialog");
    }

    @Override
    public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException {
        if (manualMode) {
            if (swipeDialog == null) {
                return;
            }

            swipeDialog.dismiss();
        } else {
            name = ((EditText) getView().findViewById(R.id.name)).getText().toString();
        }

        try {
            neo.storeCode(name, key, (byte) (oath_type | algorithm_type), digits, counter);
            long timestamp = System.currentTimeMillis() / 1000 / 30;
            final List<Map<String, String>> codes = neo.getCodes(timestamp);
            Toast.makeText(getActivity(), R.string.prog_success, Toast.LENGTH_LONG).show();
            getView().post(new Runnable() {
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
