package com.yubico.yubikit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.yubico.yubikit.transport.YubiKeyBackend;
import com.yubico.yubikit.transport.nfc.NfcBackend;
import com.yubico.yubikit.transport.nfc.NfcDeviceManager;
import com.yubico.yubikit.transport.usb.UsbBackend;
import com.yubico.yubikit.transport.usb.UsbDeviceManager;

import java.util.concurrent.SynchronousQueue;

import androidx.annotation.Nullable;

public final class DeviceManager {
    private final Context context;
    private final Handler handler;
    private final UsbDeviceManager usbDeviceManager;
    private final NfcDeviceManager nfcDeviceManager;

    public DeviceManager(Activity activity) {
        this(activity, null, null);
    }

    public DeviceManager(Activity activity, Handler handler) {
        this(activity, handler, null);
    }

    public DeviceManager(Activity activity, @Nullable Handler handler, @Nullable NfcDeviceManager.DispatchConfiguration nfcDispatchConfig) {
        this.context = activity;
        this.handler = handler != null ? handler : new Handler(YkIoWorker.getLooper());
        usbDeviceManager = new UsbDeviceManager(activity, this.handler);
        nfcDeviceManager = new NfcDeviceManager(activity, this.handler, nfcDispatchConfig);
    }

    @SuppressWarnings("unchecked")
    public <T extends YubiKeyBackend> void setOnYubiKeyHandler(Class<T> filter, boolean requirePermissionUsb, @Nullable YubiKeyBackend.BackendHandler<? super T> handler) {
        if (filter.isAssignableFrom(UsbBackend.class)) {
            usbDeviceManager.setOnDevice(requirePermissionUsb, (YubiKeyBackend.BackendHandler<? super UsbBackend>) handler);
        }
        if (filter.isAssignableFrom(NfcBackend.class)) {
            nfcDeviceManager.setOnDevice((YubiKeyBackend.BackendHandler<? super NfcBackend>) handler);
        }
    }

    public <T extends YubiKeyBackend> void setOnYubiKeyHandler(Class<T> filter, @Nullable YubiKeyBackend.BackendHandler<? super T> handler) {
        setOnYubiKeyHandler(filter, true, handler);
    }

    public void setOnYubiKeyHandler(@Nullable YubiKeyBackend.BackendHandler<? super YubiKeyBackend> handler) {
        setOnYubiKeyHandler(YubiKeyBackend.class, true, handler);
    }

    public boolean interceptIntent(Intent intent) {
        return nfcDeviceManager.interceptIntent(intent);
    }

    public void triggerOnDevice() {
        usbDeviceManager.triggerOnDevice();
    }

    private enum YkIoWorker {
        INSTANCE;

        private final Looper looper;

        YkIoWorker() {
            final SynchronousQueue<Looper> looperQueue = new SynchronousQueue<>();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    Looper.prepare();
                    try {
                        looperQueue.put(Looper.myLooper());
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    Looper.loop();
                }
            });
            t.start();
            try {
                looper = looperQueue.take();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        public static Looper getLooper() {
            return INSTANCE.looper;
        }
    }
}
