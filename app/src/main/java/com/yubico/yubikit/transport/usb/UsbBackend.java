package com.yubico.yubikit.transport.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.yubico.yubikit.transport.Iso7816Backend;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UsbBackend implements Iso7816Backend {
    private static final Set<Integer> PRODUCT_IDS_CCID = new HashSet<>(Arrays.asList(0x0111, 0x0112, 0x0115, 0x0116, 0x0404, 0x0405, 0x0406, 0x0407));
    private static final Set<Integer> PRODUCT_IDS_OTP = new HashSet<>(Arrays.asList(0x0010, 0x00110, 0x0111, 0x0114, 0x0116, 0x0401, 0x0403, 0x0405, 0x0407, 0x0410));
    private static final Set<Integer> PRODUCT_IDS_FIDO = new HashSet<>(Arrays.asList(0x0113, 0x0114, 0x0115, 0x0116, 0x0120, 0x0402, 0x0403, 0x0406, 0x0407, 0x0410));

    private static final int TIMEOUT = 10000;
    private static final byte SLOT = 0;
    private static final byte STATUS_TIME_EXTENSION = (byte) 0x80;

    private final UsbManager usbManager;
    private final UsbDevice usbDevice;

    private UsbDeviceConnection connection;
    private UsbInterface ccidInterface;
    private UsbEndpoint bulkOut, bulkIn;
    private byte sequence = 0;
    private byte[] atr;

    public UsbBackend(UsbManager usbManager, UsbDevice usbDevice) {
        this.usbManager = usbManager;
        this.usbDevice = usbDevice;
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
    public void connect() throws IOException {
        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface usbInterface = usbDevice.getInterface(i);
            if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_CSCID) {
                this.ccidInterface = usbInterface;
                break;
            }
        }
        if (ccidInterface == null) {
            throw new IllegalStateException("No CCID interface found!");
        }

        for (int i = 0; i < ccidInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = ccidInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    bulkIn = endpoint;
                } else {
                    bulkOut = endpoint;
                }
            }
        }
        if (bulkIn == null || bulkOut == null) {
            throw new IllegalStateException("Unable to find endpoints!");
        }

        connection = usbManager.openDevice(usbDevice);
        connection.claimInterface(ccidInterface, true);

        atr = transceive((byte) 0x62, new byte[0]);
    }

    private byte[] transceive(byte type, byte[] data) throws IOException {
        ByteBuffer packet = ByteBuffer.allocate(10 + data.length).order(ByteOrder.LITTLE_ENDIAN)
                .put(type)
                .putInt(data.length)
                .put(SLOT)
                .put(sequence)
                .put((byte) 0)
                .putShort((short) 0)
                .put(data);
        packet.rewind();
        byte[] bufOut = new byte[bulkOut.getMaxPacketSize()];
        int remaining = packet.remaining();
        while (remaining >= 0) { // Note that we send an empty packet on multiples of packet.length!
            int packetSize = Math.min(bufOut.length, remaining);
            packet.get(bufOut, 0, packetSize);
            connection.bulkTransfer(bulkOut, bufOut, packetSize, TIMEOUT);
            remaining -= bufOut.length;
        }

        byte[] bufIn = new byte[bulkIn.getMaxPacketSize()];
        int read;
        int tries = 5;
        do {
            read = connection.bulkTransfer(bulkIn, bufIn, bufIn.length, TIMEOUT);
            if ((bufIn[5] != SLOT || bufIn[6] != sequence) && tries-- < 0)
                throw new IOException("Failed to read response");
        } while (bufIn[5] != SLOT || bufIn[6] != sequence || bufIn[7] == STATUS_TIME_EXTENSION);
        sequence++;


        ByteBuffer responseBuffer = ByteBuffer.wrap(bufIn).order(ByteOrder.LITTLE_ENDIAN);
        if (responseBuffer.get() != (byte) 0x80) {
            throw new IOException("Invalid response");
        }
        int length = responseBuffer.getInt();
        responseBuffer.get(); // Slot, already checked
        responseBuffer.get(); // Sequence, already checked
        if (responseBuffer.getShort() != 0) {
            throw new IOException("Invalid response");
        }
        responseBuffer.get(); //TODO: Should be ?, level parameter
        ByteBuffer response = ByteBuffer.allocate(length).put(bufIn, responseBuffer.position(), Math.min(length, responseBuffer.remaining()));

        while (read == bufIn.length) {  //Read until first non-full packet.
            read = connection.bulkTransfer(bulkIn, bufIn, bufIn.length, TIMEOUT);
            if (read > 0) {
                response.put(bufIn, 0, read);
            } else if (read < 0) {
                throw new IOException("Failed to read response");
            }
        }

        return response.array();
    }

    public byte[] getAtr() {
        return atr;
    }

    @Override
    public byte[] send(byte cla, byte ins, byte p1, byte p2, byte[] data) throws IOException {
        byte[] apdu = ByteBuffer.allocate(5 + data.length)
                .put(cla)
                .put(ins)
                .put(p1)
                .put(p2)
                .put((byte) data.length)
                .put(data)
                .array();
        return transceive((byte) 0x6f, apdu);
    }

    @Override
    public void close() {
        connection.close();
    }
}
