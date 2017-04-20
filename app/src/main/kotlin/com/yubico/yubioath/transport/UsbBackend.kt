package com.yubico.yubioath.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

/**
 * Created by Dain on 2017-04-19.
 */

class UsbBackend(private val connection: UsbDeviceConnection, private val iface: UsbInterface, private val endpointBulkOut: UsbEndpoint, private val endpointBulkIn: UsbEndpoint) : Backend, Closeable {
    override val persistent = true

    private val _atr: ByteArray
    private var sequence: Byte = 0

    val atr get() = Arrays.copyOf(_atr, _atr.size)

    init {
        connection.claimInterface(iface, true)

        //Send IccPowerOn, get ATR
        _atr = transceive(0x62.toByte(), ByteArray(0))
    }

    private fun transceive(type: Byte, data: ByteArray): ByteArray {
        val packet = ByteBuffer.allocate(10 + data.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(type)
                .putInt(data.size)
                .put(SLOT)
                .put(sequence)
                .put(0.toByte())
                .putShort(0.toShort())
                .put(data)
        packet.rewind()
        var buf = ByteArray(endpointBulkOut.maxPacketSize)
        var remaining = packet.remaining()
        while (remaining >= 0) { // Note that we send an empty packet on multiples of packet.length!
            val packetSize = Math.min(buf.size, remaining)
            packet.get(buf, 0, packetSize)
            connection.bulkTransfer(endpointBulkOut, buf, packetSize, TIMEOUT)
            remaining -= buf.size
        }

        buf = ByteArray(endpointBulkIn.maxPacketSize)

        var read:Int
        do {
            read = connection.bulkTransfer(endpointBulkIn, buf, buf.size, TIMEOUT)
        } while(buf[5] != SLOT || buf[6] != sequence || buf[7] == STATUS_TIME_EXTENSION)
        sequence++

        val firstPacket = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        firstPacket.get() //TODO: Should be 0x80
        val length = firstPacket.int
        firstPacket.get() // Slot, already checked
        firstPacket.get() // Sequence, already checked
        firstPacket.get() //TODO: Status, should be 0
        firstPacket.get() //TODO: Should be 0, error
        firstPacket.get() //TODO: Should be ?, level parameter
        val response = ByteBuffer.allocate(length)
        response.put(buf, firstPacket.position(), Math.min(length, firstPacket.remaining()))
        while (read == buf.size) {  //Read until first non-full packet.
            read = connection.bulkTransfer(endpointBulkIn, buf, buf.size, TIMEOUT)
            response.put(buf, 0, read)
        }

        return response.array()
    }

    override fun sendApdu(apdu: ByteArray): ByteArray {
        return transceive(0x6f.toByte(), apdu)
    }

    @Throws(IOException::class)
    override fun close() {
        connection.releaseInterface(iface)
        connection.close()
    }

    companion object {
        private const val TIMEOUT = 10000
        private const val SLOT: Byte = 0

        private const val STATUS_TIME_EXTENSION = 0x80.toByte()

        private fun findInterface(device: UsbDevice): UsbInterface? {
            for (i in 0..device.interfaceCount - 1) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_CSCID) {
                    return iface
                }
            }
            return null
        }

        fun isSupported(device: UsbDevice): Boolean {
            return findInterface(device) != null
        }

        fun connect(manager: UsbManager, device: UsbDevice): UsbBackend {
            val iface = findInterface(device)
            if (iface != null) {
                var endpointBulkIn: UsbEndpoint? = null
                var endpointBulkOut: UsbEndpoint? = null
                for (j in 0..iface.endpointCount - 1) {
                    val endpoint = iface.getEndpoint(j)
                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                            endpointBulkIn = endpoint
                        } else {
                            endpointBulkOut = endpoint
                        }
                    }
                }
                if (endpointBulkOut != null && endpointBulkIn != null) {
                    return UsbBackend(manager.openDevice(device), iface, endpointBulkOut, endpointBulkIn)
                }
            }
            throw IllegalArgumentException("UsbDevice does not support CCID")
        }
    }
}
