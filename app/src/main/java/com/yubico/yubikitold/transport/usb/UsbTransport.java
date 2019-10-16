package com.yubico.yubikitold.transport.usb;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.yubico.yubikitold.transport.YubiKeyTransport;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UsbTransport implements YubiKeyTransport {
    private static final Set<Integer> PRODUCT_IDS_CCID = new HashSet<>(Arrays.asList(0x0111, 0x0112, 0x0115, 0x0116, 0x0404, 0x0405, 0x0406, 0x0407));
    private static final Set<Integer> PRODUCT_IDS_OTP = new HashSet<>(Arrays.asList(0x0010, 0x00110, 0x0111, 0x0114, 0x0116, 0x0401, 0x0403, 0x0405, 0x0407, 0x0410));
    private static final Set<Integer> PRODUCT_IDS_FIDO = new HashSet<>(Arrays.asList(0x0113, 0x0114, 0x0115, 0x0116, 0x0120, 0x0402, 0x0403, 0x0406, 0x0407, 0x0410));

    private final UsbManager usbManager;
    private final UsbDevice usbDevice;

    public UsbTransport(UsbManager usbManager, UsbDevice usbDevice) {
        this.usbManager = usbManager;
        this.usbDevice = usbDevice;
    }

    public boolean hasPermission() {
        return usbManager.hasPermission(usbDevice);
    }

    public boolean hasCcid() {
        return PRODUCT_IDS_CCID.contains(usbDevice.getProductId());
    }

    public boolean hasOtp() {
        return PRODUCT_IDS_OTP.contains(usbDevice.getProductId());
    }

    public boolean hasFido() {
        return PRODUCT_IDS_FIDO.contains(usbDevice.getProductId());
    }

    @Override
    public boolean hasIso7816() {
        return hasCcid();
    }

    @Override
    public UsbIso7816Connection connect() throws IOException {
        return new UsbIso7816Connection(usbManager, usbDevice);
    }

}
