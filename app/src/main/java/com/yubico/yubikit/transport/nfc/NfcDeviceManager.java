package com.yubico.yubikit.transport.nfc;

import android.app.Activity;
import android.content.Intent;
import android.nfc.Tag;
import android.os.Handler;
import android.util.Log;

import com.yubico.yubikit.transport.YubiKeyBackend;

import androidx.annotation.Nullable;
import nordpol.android.OnDiscoveredTagListener;
import nordpol.android.TagDispatcher;
import nordpol.android.TagDispatcherBuilder;

public class NfcDeviceManager {
    private final Handler handler;
    private final TagDispatcher tagDispatcher;

    private YubiKeyBackend.BackendHandler<? super NfcBackend> listener;
    private Tag pendingTag = null;

    public NfcDeviceManager(Activity activity, final Handler handler, @Nullable DispatchConfiguration dispatchConfiguration) {
        this.handler = handler;

        TagDispatcherBuilder builder = new TagDispatcherBuilder(activity, new OnDiscoveredTagListener() {
            @Override
            public void tagDiscovered(final Tag tag) {
                final YubiKeyBackend.BackendHandler<? super NfcBackend> nfcDeviceListener = NfcDeviceManager.this.listener;
                if (nfcDeviceListener != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("yubikit", "On tag: " + nfcDeviceListener);
                            nfcDeviceListener.onYubiKeyBackend(new NfcBackend(tag));
                        }
                    });
                } else {
                    pendingTag = tag;
                }
            }
        });
        if (dispatchConfiguration != null) {
            dispatchConfiguration.configure(builder);
        }
        tagDispatcher = builder.build();
    }

    public void setOnDevice(@Nullable final YubiKeyBackend.BackendHandler<? super NfcBackend> nfcDeviceListener) {
        Log.d("yubikit", "Set NFC listener: " + nfcDeviceListener);
        if (this.listener != null) {
            tagDispatcher.disableExclusiveNfc();
        }
        this.listener = nfcDeviceListener;
        if (nfcDeviceListener != null) {
            tagDispatcher.enableExclusiveNfc();
            final Tag tag = pendingTag;
            if (tag != null) {
                pendingTag = null;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("yubikit", "On tag: " + nfcDeviceListener);
                        nfcDeviceListener.onYubiKeyBackend(new NfcBackend(tag));
                    }
                });
            }
        }
    }

    public boolean interceptIntent(Intent intent) {
        return tagDispatcher.interceptIntent(intent);
    }

    public interface DispatchConfiguration {
        void configure(TagDispatcherBuilder builder);
    }
}
