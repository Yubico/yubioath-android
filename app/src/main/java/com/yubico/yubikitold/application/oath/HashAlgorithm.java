package com.yubico.yubikitold.application.oath;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public enum HashAlgorithm {
    SHA1((byte)1, 64),
    SHA256((byte)2, 64),
    SHA512((byte)3, 128);

    public final byte value;
    public final int blockSize;

    HashAlgorithm(byte value, int blockSize) {
        this.value = value;
        this.blockSize = blockSize;
    }

    public byte[] prepareKey(byte[] key) throws NoSuchAlgorithmException {
        if (key.length < 14) {
            return ByteBuffer.allocate(14).put(key).array();
        } else if (key.length > blockSize) {
            return MessageDigest.getInstance(name()).digest(key);
        } else {
            return key;
        }
    }

    public static HashAlgorithm fromValue(byte value) {
        for (HashAlgorithm type : HashAlgorithm.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Not a valid HashAlgorithm");
    }
}
