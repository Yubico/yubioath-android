package com.yubico.yubikitold.transport.nfc;

import android.app.Activity;
import android.nfc.Tag;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import com.yubico.yubikitold.transport.OnYubiKeyListener;

public class NfcDeviceManager {
    private final Activity activity;
    private final Handler handler;
    private final NfcDispatcher dispatcher;

    private OnYubiKeyListener listener;
    private Tag pendingTag = null;

    public NfcDeviceManager(Activity activity, final Handler handler, @Nullable NfcDispatcher dispatcher) {
        this.activity = activity;
        this.handler = handler;
        if (dispatcher == null) {
            if (Build.VERSION.SDK_INT < 19) {
                throw new IllegalStateException("The DefaultNfcDispatcher requires API level >= 19!");
            }
            this.dispatcher = new DefaultNfcDispatcher(activity);
        } else {
            this.dispatcher = dispatcher;
        }
        this.dispatcher.setOnTagHandler(new NfcDispatcher.OnTagHandler() {
            @Override
            public void onTag(final Tag tag) {
                final OnYubiKeyListener nfcDeviceListener = NfcDeviceManager.this.listener;
                if (nfcDeviceListener != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("yubikit", "On tag: " + nfcDeviceListener);
                            nfcDeviceListener.onYubiKey(new NfcTransport(tag));
                        }
                    });
                } else {
                    pendingTag = tag;
                }
            }
        });
    }

    public void setOnYubiKeyListener(@Nullable final OnYubiKeyListener listener) {
        Log.d("yubikit", "Set NFC listener: " + listener);
        if (this.listener != null && !activity.isFinishing()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed()) {
                dispatcher.disable();
            }
        }
        this.listener = listener;
        if (listener != null) {
            dispatcher.enable();
            final Tag tag = pendingTag;
            if (tag != null) {
                pendingTag = null;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onYubiKey(new NfcTransport(tag));
                    }
                });
            }
        }
    }

    public interface NfcDispatcher {
        void setOnTagHandler(@Nullable OnTagHandler handler);

        void enable();

        void disable();

        interface OnTagHandler {
            void onTag(Tag tag);
        }
    }
}
