package com.yubico.yubikit.application;

import android.util.Log;

import java.util.Arrays;

public class ApduException extends Exception {
    public final byte[] body;
    public final short sw;

    public ApduException(byte[] body, short sw) {
        super(String.format("APDU SW=0x%04X", 0xffff & sw));
        this.body = body;
        this.sw = sw;
    }

    public byte getSw1() {
        return (byte) (sw >> 8);
    }

    public byte getSw2() {
        return (byte) (sw & 0xff);
    }

    public static byte[] getChecked(byte[] data, int from, int to) throws ApduException {
        byte[] body = Arrays.copyOfRange(data, from, to - 2);
        short sw = (short)(((0xff & data[to-2]) << 8) | (0xff & data[to-1]));
        if (sw != (short)0x9000) {
            throw new ApduException(body, sw);
        }
        return body;
    }

    public static byte[] getChecked(byte[] data) throws ApduException {
        return getChecked(data, 0, data.length);
    }
}
