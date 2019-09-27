package com.yubico.yubikitold.application.oath;

import com.yubico.yubikitold.application.Tlv;

import java.util.Arrays;

public class CalculateResponse {
    public static final byte TYPE_FULL = 0x75;
    public static final byte TYPE_TRUNCATED = 0x76;
    public static final byte TYPE_HOTP = 0x77;
    public static final byte TYPE_TOUCH = 0x7c;

    public final String name;
    public final byte responseType;
    public final int digits;
    public final byte[] response;

    CalculateResponse(String name, Tlv response) {
        this.name = name;
        this.responseType = response.getTag();
        byte[] value = response.getValue();
        this.digits = value[0];
        this.response = Arrays.copyOfRange(value, 1, value.length);
    }
}
