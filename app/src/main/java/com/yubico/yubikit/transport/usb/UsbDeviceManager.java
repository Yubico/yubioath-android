package com.yubico.yubikit.transport.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.yubico.yubikit.transport.OnYubiKeyListener;

import androidx.annotation.Nullable;

public final class UsbDeviceManager {
    private final static String ACTION_USB_PERMISSION = "com.yubico.yubikit.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver;
    private final PendingIntent pendingUsbPermissionIntent;
    private final Context context;
    private final android.os.Handler handler;
    private final PollUsbRunnable pollUsbRunnable;
    private final UsbManager usbManager;
    private transient OnYubiKeyListener usbDeviceListener = null;
    private transient boolean requirePermission = true;
    private transient boolean hasUsb = false;

    public UsbDeviceManager(Context context, android.os.Handler handler) {
        this.context = context;
        this.handler = handler;

        pendingUsbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        usbReceiver = new UsbBroadcastReceiver();
        pollUsbRunnable = new PollUsbRunnable();
    }

    public void setRequirePermission(boolean requirePermission) {
        this.requirePermission = requirePermission;
    }

    public void setOnYubiKeyListener(final @Nullable OnYubiKeyListener listener) {
        Log.d("yubikit", "Set USB listener: " + listener);
        if (this.usbDeviceListener != null || listener != null) {
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

    public void triggerOnYubiKey() {
        setOnYubiKeyListener(usbDeviceListener);
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
        OnYubiKeyListener listener = usbDeviceListener;
        if (listener != null) {
            listener.onYubiKey(new UsbTransport(usbManager, device));
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
            OnYubiKeyListener listener = usbDeviceListener;
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
                        listener.onYubiKey(null);
                    }
                }
                handler.postDelayed(this, 500);
            }
        }
    }
}
