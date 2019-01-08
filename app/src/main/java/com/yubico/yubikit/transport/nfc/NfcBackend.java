package com.yubico.yubikit.transport.nfc;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.tech.Ndef;

import com.yubico.yubikit.transport.Iso7816Backend;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import androidx.annotation.Nullable;
import nordpol.IsoCard;
import nordpol.android.AndroidCard;

public class NfcBackend implements Iso7816Backend {
    private static final byte URL_NDEF_RECORD = (byte) 0xd1;
    private static final byte[] URL_PREFIX_BYTES = new byte[]{0x55, 0x04, 0x6d, 0x79, 0x2e, 0x79, 0x75, 0x62, 0x69, 0x63, 0x6f, 0x2e, 0x63, 0x6f, 0x6d, 0x2f};

    private final Tag tag;

    private IsoCard card;
    private boolean ndefRead = false;
    private byte[] ndefData;

    public NfcBackend(Tag tag) {
        this.tag = tag;
    }

    public byte[] readRawNdefData() throws IOException {
        if (!ndefRead) {
            if (card != null) {
                card.close();
            }

            ndefRead = true;
            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                return null;
            }
            ndef.connect();
            try {
                NdefMessage ndefMessage = ndef.getNdefMessage();
                ndefData = ndefMessage != null ? ndefMessage.toByteArray() : null;
            } catch (FormatException e) {
                //Ignore
            }
            ndef.close();

            if (card != null) {
                card.connect();
            }
        }

        return ndefData;
    }

    public byte[] getNdefBytes() throws IOException {
        return parseNdefOtp(readRawNdefData());
    }

    public String getNdefOtp() throws IOException {
        return new String(getNdefBytes(), Charset.forName("UTF-8"));
    }

    @Override
    public void connect() throws IOException {
        card = AndroidCard.get(tag);
        card.connect();
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
        StringBuilder sb = new StringBuilder();
        for (byte b : apdu) {
            sb.append(String.format("0x%02x", b));
        }
        return card.transceive(apdu);
    }

    @Override
    public void close() throws IOException {
        card.close();
    }

    @Nullable
    public static byte[] parseNdefOtp(byte[] ndefData) {
        if (ndefData[0] == URL_NDEF_RECORD && Arrays.equals(URL_PREFIX_BYTES, Arrays.copyOfRange(ndefData, 3, 3 + URL_PREFIX_BYTES.length))) {
            // YubiKey NEO uses https://my.yubico.com/neo/<payload>
            if (Arrays.equals("/neo/".getBytes(), Arrays.copyOfRange(ndefData, 18, 18 + 5))) {
                ndefData[22] = '#';
            }
            for (int i = 0; i < ndefData.length; i++) {
                if (ndefData[i] == '#') {
                    return Arrays.copyOfRange(ndefData, i + 1, ndefData.length);
                }
            }
        }

        return null;
    }
}