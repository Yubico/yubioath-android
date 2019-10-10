package com.yubico.yubikitold;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.yubico.yubikitold.transport.OnYubiKeyListener;
import com.yubico.yubikitold.transport.nfc.NfcDeviceManager;
import com.yubico.yubikitold.transport.usb.UsbDeviceManager;

import java.util.concurrent.SynchronousQueue;

/**
 * Manages the connection to YubiKeys over both NFC and USB.
 * <p>
 * This class is typically instantiated from within your Activity, and should be used from within
 * that Activity's lifecycle.
 */
public final class YubiKitManager {
    private final Handler handler;
    private final UsbDeviceManager usbDeviceManager;
    private final NfcDeviceManager nfcDeviceManager;
    private OnYubiKeyListener listener = null;
    private boolean paused = true;

    public YubiKitManager(Activity activity) {
        this(activity, null, null);
    }

    public YubiKitManager(Activity activity, Handler handler) {
        this(activity, handler, null);
    }

    /**
     * Instantiates a YubiKitManager for the given Activity.
     *
     * @param activity      The Activity to connect the YubiKitManager to.
     * @param handler       Optional Handler used to dispatch YubiKey events on. This should NOT be using the Main Thread.
     * @param nfcDispatcher Optional NfcDispatcher to use instead of the default implementation for NFC communication.
     */
    public YubiKitManager(Activity activity, @Nullable Handler handler, @Nullable com.yubico.yubikitold.transport.nfc.NfcDeviceManager.NfcDispatcher nfcDispatcher) {
        if (handler == null) {
            handler = new Handler(YkIoWorker.getLooper());
        }
        this.handler = handler;
        usbDeviceManager = new com.yubico.yubikitold.transport.usb.UsbDeviceManager(activity, handler);
        nfcDeviceManager = new com.yubico.yubikitold.transport.nfc.NfcDeviceManager(activity, handler, nfcDispatcher);
    }

    /**
     * Get the Handler used for event dispatch.
     *
     * @return The Handler instance provided when creating the YubiKitManager.
     */
    public Handler getHandler() {
        return handler;
    }

    /**
     * Get the UsbDeviceHandler for direct manipulation.
     *
     * @return The UsbDeviceHandler which communicates with YubiKeys over USB.
     */
    public UsbDeviceManager getUsbDeviceManager() {
        return usbDeviceManager;
    }

    /**
     * Get the NfcDeviceHandler for direct manipulation.
     *
     * @return The NfcDeviceHandler which communicates with YubiKeys over NFC.
     */
    public NfcDeviceManager getNfcDeviceManager() {
        return nfcDeviceManager;
    }

    /**
     * Set a listener used to react to YubiKey connection/disconnection events.
     *
     * @param listener An OnYubiKeyListener to set, or null to unset the listener and stop listening.
     */
    public void setOnYubiKeyListener(@Nullable OnYubiKeyListener listener) {
        this.listener = listener;
        if (!paused) {
            resume();
        }
    }

    /**
     * Call this to stop listening for YubiKey events. This method should be called prior to the Activity pausing in a listener is set, for example in the onPause() method.
     */
    public void pause() {
        paused = true;
        usbDeviceManager.setOnYubiKeyListener(null);
        nfcDeviceManager.setOnYubiKeyListener(null);
    }

    /**
     * Call this to resume listening for YubiKey events after the Activity has been paused, for example in the onResume() method.
     */
    public void resume() {
        paused = false;
        usbDeviceManager.setOnYubiKeyListener(listener);
        nfcDeviceManager.setOnYubiKeyListener(listener);
    }

    /**
     * Causes the OnYubiKeyListener currently set to be invoked with the currently connected YubiKey,
     * if one is connected.
     */
    public void triggerOnYubiKey() {
        usbDeviceManager.triggerOnYubiKey();
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
            }, "YubiKit IO thread");
            t.start();
            try {
                looper = looperQueue.take();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Looper getLooper() {
            return INSTANCE.looper;
        }
    }
}
