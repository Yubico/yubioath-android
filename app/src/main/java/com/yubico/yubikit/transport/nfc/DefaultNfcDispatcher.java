package com.yubico.yubikit.transport.nfc;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@RequiresApi(19)
public class DefaultNfcDispatcher implements NfcDeviceManager.NfcDispatcher {
    private final Activity activity;
    private final NfcAdapter adapter;

    private OnTagHandler handler;

    public DefaultNfcDispatcher(Activity activity) {
        this.activity = activity;
        adapter = NfcAdapter.getDefaultAdapter(activity);
        if (adapter == null) {
            throw new IllegalStateException("NFC adapter not available!");
        }
    }

    @Override
    public void setOnTagHandler(@Nullable OnTagHandler handler) {
        this.handler = handler;
    }

    @Override
    public void enable() {
        adapter.enableReaderMode(activity, new NfcAdapter.ReaderCallback() {
            @Override
            public void onTagDiscovered(Tag tag) {
                handler.onTag(tag);
            }
        }, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B, new Bundle());
    }

    @Override
    public void disable() {
        adapter.disableReaderMode(activity);
    }
}
