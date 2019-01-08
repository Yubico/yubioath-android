package com.yubico.yubikit.transport.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

import com.yubico.yubikit.transport.YubiKeyBackend;

import androidx.annotation.Nullable;

public final class UsbDeviceManager {
    private final static String ACTION_USB_PERMISSION = "com.yubico.yubikey.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver;
    private final PendingIntent pendingUsbPermissionIntent;
    private final Context context;
    private final Handler handler;
    private final PollUsbRunnable pollUsbRunnable;
    private final UsbManager usbManager;
    private transient YubiKeyBackend.BackendHandler<? super UsbBackend> usbDeviceListener = null;
    private transient boolean requirePermission = true;
    private transient boolean hasUsb = false;

    public UsbDeviceManager(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;

        pendingUsbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        usbReceiver = new UsbBroadcastReceiver();
        pollUsbRunnable = new PollUsbRunnable();
    }

    public void setOnDevice(boolean requirePermission, final @Nullable YubiKeyBackend.BackendHandler<? super UsbBackend> listener) {
        Log.d("yubikit", "Set listener: " + listener);
        if (this.usbDeviceListener != null || listener != null) {
            this.requirePermission = requirePermission;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (usbDeviceListener != null) {
                        handler.removeCallbacks(pollUsbRunnable);
                        context.unregisterReceiver(usbReceiver);
                    }
                    usbDeviceListener = listener;
                    if (listener != null) {
                        hasUsb = false;
                        context.registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
                        handler.post(pollUsbRunnable);
                    }
                }
            });
        }
    }

    public void triggerOnDevice() {
        setOnDevice(requirePermission, usbDeviceListener);
    }

    @Nullable
    private UsbDevice findDevice() {
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == 0x1050) {
                return device;
            }
        }
        return null;
    }


    private void onDeviceWithPermissions(UsbDevice device) {
        Log.d("yubikit", "Has permission for:" + device);
        YubiKeyBackend.BackendHandler<? super UsbBackend> listener = usbDeviceListener;
        if (listener != null) {
            listener.onYubiKeyBackend(new UsbBackend(usbManager, device));
        }
    }

    private final class UsbBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true)) {
                    if (device != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                onDeviceWithPermissions(device);
                            }
                        });
                    }
                } else {
                    Log.d("yubikit", "permission denied for device " + device);
                }
            }
        }
    }

    private final class PollUsbRunnable implements Runnable {
        @Override
        public void run() {
            YubiKeyBackend.BackendHandler<? super UsbBackend> listener = usbDeviceListener;
            if (listener != null) {
                UsbDevice device = findDevice();
                if (hasUsb == (device == null)) {
                    hasUsb = device != null;
                    if (hasUsb) {
                        if (!requirePermission || usbManager.hasPermission(device)) {
                            onDeviceWithPermissions(device);
                        } else {
                            Log.d("yubikit", "Device lacks permission, request: " + device);
                            usbManager.requestPermission(device, pendingUsbPermissionIntent);
                        }
                    } else {
                        Log.d("yubikit", "On USB Null");
                        listener.onYubiKeyBackend(null);
                    }
                }
                handler.postDelayed(this, 500);
            }
        }
    }
}
