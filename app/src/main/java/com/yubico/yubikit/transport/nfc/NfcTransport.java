package com.yubico.yubikit.transport.nfc;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.tech.Ndef;

import com.yubico.yubikit.transport.YubiKeyTransport;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import androidx.annotation.Nullable;

public class NfcTransport implements YubiKeyTransport {
    private static final byte URL_NDEF_RECORD = (byte) 0xd1;
    private static final byte[] URL_PREFIX_BYTES = new byte[]{0x55, 0x04, 0x6d, 0x79, 0x2e, 0x79, 0x75, 0x62, 0x69, 0x63, 0x6f, 0x2e, 0x63, 0x6f, 0x6d, 0x2f};

    private final Tag tag;

    private boolean ndefRead = false;
    private byte[] ndefData;

    public NfcTransport(Tag tag) {
        this.tag = tag;
    }

    @Nullable
    public byte[] readRawNdefData() throws IOException {
        if (!ndefRead) {
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
            } finally {
                ndef.close();
            }
        }

        return ndefData;
    }

    @Nullable
    public byte[] getNdefBytes() throws IOException {
        return parseNdefOtp(readRawNdefData());
    }

    @Nullable
    public String getNdefOtp() throws IOException {
        byte[] ndefBytes = getNdefBytes();
        return ndefBytes == null ? null : new String(ndefBytes, Charset.forName("UTF-8"));
    }

    @Override
    public boolean hasIso7816() {
        return true;
    }

    @Override
    public NfcIso7816Connection connect() throws IOException {
        return new NfcIso7816Connection(tag);
    }


    @Nullable
    public static byte[] parseNdefOtp(byte[] ndefData) {
        if (ndefData != null && ndefData[0] == URL_NDEF_RECORD && Arrays.equals(URL_PREFIX_BYTES, Arrays.copyOfRange(ndefData, 3, 3 + URL_PREFIX_BYTES.length))) {
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