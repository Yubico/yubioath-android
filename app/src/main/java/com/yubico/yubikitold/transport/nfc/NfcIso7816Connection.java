package com.yubico.yubikitold.transport.nfc;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import com.yubico.yubikitold.transport.Iso7816Connection;

import java.io.IOException;
import java.nio.ByteBuffer;

class NfcIso7816Connection implements Iso7816Connection {
    private final IsoDep card;

    public NfcIso7816Connection(Tag tag) throws IOException {
        card = IsoDep.get(tag);
        card.connect();
        card.setTimeout(20000);
    }

    @Override
    public byte[] send(byte cla, byte ins, byte p1, byte p2, byte[] data) throws IOException {
        byte[] apdu = ByteBuffer.allocate(data.length + 5)
                .put(cla)
                .put(ins)
                .put(p1)
                .put(p2)
                .put((byte) data.length)
                .put(data, 0, data.length)
                .array();
        return card.transceive(apdu);
    }

    @Override
    public void close() throws IOException {
        card.close();
    }
}
