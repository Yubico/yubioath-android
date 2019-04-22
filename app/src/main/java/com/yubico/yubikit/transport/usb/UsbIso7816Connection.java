package com.yubico.yubikit.transport.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.yubico.yubikit.transport.Iso7816Connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UsbIso7816Connection implements Iso7816Connection {
    private static final int TIMEOUT = 10000;
    private static final byte SLOT = 0;
    private static final byte STATUS_TIME_EXTENSION = (byte) 0x80;

    private final UsbDeviceConnection connection;
    private final UsbEndpoint bulkOut, bulkIn;
    private final byte[] atr;
    private byte sequence = 0;

    public UsbIso7816Connection(UsbManager usbManager, UsbDevice usbDevice) throws IOException {
        UsbInterface ccidInterface = null;
        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface usbInterface = usbDevice.getInterface(i);
            if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_CSCID) {
                ccidInterface = usbInterface;
                break;
            }
        }
        if (ccidInterface == null) {
            throw new IOException("No CCID interface found!");
        }

        UsbEndpoint bulkIn = null;
        UsbEndpoint bulkOut = null;
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
            throw new IOException("Unable to find endpoints!");
        }
        this.bulkIn = bulkIn;
        this.bulkOut = bulkOut;

        connection = usbManager.openDevice(usbDevice);
        if (connection == null) {
            throw new IOException("Unable to connect to USB device!");
        }
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
        responseBuffer.getShort(); // Slot and Sequence, already checked
        byte status = responseBuffer.get();
        byte error = responseBuffer.get();
        if (status != 0) {
            throw new IOException(String.format("Invalid response! bStatus: 0x%02x, bError: 0x%02x", status, error));
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