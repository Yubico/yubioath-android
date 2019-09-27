package com.yubico.yubikitold.application.oath;

public enum OathType {

    HOTP((byte) 0x10),
    TOTP((byte) 0x20);

    public final byte value;

    OathType(byte value) {
        this.value = value;
    }

    public static OathType fromValue(byte value) {
        for (OathType type : OathType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Not a valid OathType");
    }
}
