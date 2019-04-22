package com.yubico.yubikit.transport.nfc;

import android.app.Activity;
import android.content.Intent;
import android.nfc.Tag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import nordpol.android.OnDiscoveredTagListener;
import nordpol.android.TagDispatcher;
import nordpol.android.TagDispatcherBuilder;

/**
 * NfcDispatcher using the Nordpol library, which supports devices with API level <19 and offers device specific workarounds for greater device compatibility.
 */
public class NordpolNfcDispatcher implements NfcDeviceManager.NfcDispatcher {
    private final Activity activity;
    private final TagDispatcher tagDispatcher;

    private OnTagHandler handler;

    public NordpolNfcDispatcher(@NonNull Activity activity, @Nullable DispatchConfiguration configuration) {
        this.activity = activity;
        TagDispatcherBuilder builder = new TagDispatcherBuilder(activity, new OnDiscoveredTagListener() {
            @Override
            public void tagDiscovered(final Tag tag) {
                handler.onTag(tag);
            }
        });
        if (configuration != null) {
            configuration.configure(builder);
        }
        tagDispatcher = builder.build();
    }

    @Override
    public void setOnTagHandler(@Nullable OnTagHandler handler) {
        this.handler = handler;
    }

    @Override
    public void enable() {
        tagDispatcher.enableExclusiveNfc();
    }

    @Override
    public void disable() {
        tagDispatcher.disableExclusiveNfc();
    }

    /**
     * Must be invoked from Activity.onNewIntent() on Android API <= 19, or when readerMode is disabled.
     *
     * @param intent the Intent passed to onNewIntent.
     * @return true if a tag was discovered.
     */
    public boolean interceptIntent(Intent intent) {
        return tagDispatcher.interceptIntent(intent);
    }

    public interface DispatchConfiguration {
        void configure(TagDispatcherBuilder builder);
    }
}
