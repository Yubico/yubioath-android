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
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
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
public class AddCodeScanFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener {
    private static final String CODE_URI = "codeUri";
    private UriParser u = new UriParser();

    public static AddCodeScanFragment newInstance(String uri) {
        Bundle bundle = new Bundle();
        bundle.putString(CODE_URI, uri);
        AddCodeScanFragment fragment = new AddCodeScanFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.add_code_scan_fragment, container, false);
        Uri uri = Uri.parse(getArguments().getString(CODE_URI));

        u.name = ((EditText) view.findViewById(R.id.name)).getText().toString();

        if (u.parseUri(uri)) {
            ((TextView) view.findViewById(R.id.name)).setText(u.name);
        } else {
            Toast.makeText(getActivity(), R.string.invalid_barcode, Toast.LENGTH_LONG).show();
            view.post(new Runnable() {
                @Override
                public void run() {
                    ((MainActivity) getActivity()).openFragment(new SwipeListFragment());
                }
            });
        }

        return view;
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
        u.name = ((EditText) getActivity().findViewById(R.id.name)).getText().toString();
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
        }
    }
}
