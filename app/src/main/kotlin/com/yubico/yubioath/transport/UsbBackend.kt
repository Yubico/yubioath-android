package com.yubico.yubioath.transport

import android.hardware.usb.*
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

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
        val bufOut = ByteArray(endpointBulkOut.maxPacketSize)
        var remaining = packet.remaining()
        while (remaining >= 0) { // Note that we send an empty packet on multiples of packet.length!
            val packetSize = Math.min(bufOut.size, remaining)
            packet.get(bufOut, 0, packetSize)
            connection.bulkTransfer(endpointBulkOut, bufOut, packetSize, TIMEOUT)
            remaining -= bufOut.size
        }

        val bufIn = ByteArray(endpointBulkIn.maxPacketSize)
        var read: Int
        var tries = 5
        do {
            read = connection.bulkTransfer(endpointBulkIn, bufIn, bufIn.size, TIMEOUT)
            if ((bufIn[5] != SLOT || bufIn[6] != sequence) && tries-- < 0) throw IOException("Failed to read response")
        } while (bufIn[5] != SLOT || bufIn[6] != sequence || bufIn[7] == STATUS_TIME_EXTENSION)
        sequence++


        val response = ByteBuffer.wrap(bufIn).order(ByteOrder.LITTLE_ENDIAN).run {
            get() //TODO: Should be 0x80
            val length = int
            get() // Slot, already checked
            get() // Sequence, already checked
            get() //TODO: Status, should be 0
            get() //TODO: Should be 0, error
            get() //TODO: Should be ?, level parameter

            ByteBuffer.allocate(length).put(bufIn, position(), Math.min(length, remaining()))
        }

        while (read == bufIn.size) {  //Read until first non-full packet.
            read = connection.bulkTransfer(endpointBulkIn, bufIn, bufIn.size, TIMEOUT)
            if (read > 0) {
                response.put(bufIn, 0, read)
            } else if (read < 0) {
                throw IOException("Failed to read response")
            }
        }

        return response.array()
    }

    override fun sendApdu(apdu: ByteArray): ByteArray = transceive(0x6f.toByte(), apdu)

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
            return (0 until device.interfaceCount).asSequence()
                    .map { device.getInterface(it) }
                    .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_CSCID }
        }

        fun isSupported(device: UsbDevice): Boolean = findInterface(device) != null

        fun connect(manager: UsbManager, device: UsbDevice): UsbBackend {
            return findInterface(device)?.let { iface ->
                (0 until iface.endpointCount)
                        .map { iface.getEndpoint(it) }
                        .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }.let {
                            UsbBackend(
                                    manager.openDevice(device),
                                    iface,
                                    it.first { it.direction == UsbConstants.USB_DIR_OUT },
                                    it.first { it.direction == UsbConstants.USB_DIR_IN }
                            )
                        }
            } ?: throw IllegalArgumentException("UsbDevice does not support CCID")
        }
    }
}
