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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.yubico.yubioath.MainActivity;
import com.yubico.yubioath.R;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.YubiKeyNeo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/30/13
 * Time: 10:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class AboutFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener {
    private Toast toast;
    private int clearCounter = 5;

    private KeyManager keyManager;

    public static AboutFragment newInstance(KeyManager keyManager) {
        AboutFragment fragment = new AboutFragment();
        fragment.setKeyManager(keyManager);
        return fragment;
    }

    private void setKeyManager(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_fragment, container, false);
        String version = "unknown";
        try {
            PackageInfo pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            //Do nothing
        }

        TextView aboutText = (TextView)view.findViewById(R.id.aboutView);
        aboutText.setText(Html.fromHtml(getString(R.string.about_text, version)));
        aboutText.setMovementMethod(LinkMovementMethod.getInstance());

        Button clearDataButton = (Button)view.findViewById(R.id.clearData);
        clearDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;
                if(--clearCounter > 0) {
                    message = getString(R.string.clear_countdown, clearCounter);
                } else {
                    keyManager.clearAll();
                    clearCounter = 5;
                    message = getString(R.string.data_cleared);
                }
                if(toast != null) {
                    toast.cancel();
                }
                toast = Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT);
                toast.show();
            }
        });

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
        long timestamp = System.currentTimeMillis() / 1000 / 30;
        final List<Map<String, String>> codes = neo.getCodes(timestamp);
        getView().post(new Runnable() {
            @Override
            public void run() {
                final SwipeListFragment fragment = new SwipeListFragment();
                fragment.getCurrent().showCodes(codes);
                ((MainActivity) getActivity()).openFragment(fragment);
            }
        });
    }
}
