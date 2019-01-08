package com.yubico.yubikit.application;

import java.nio.ByteBuffer;

public final class Version {
    public final byte major;
    public final byte minor;
    public final byte micro;

    public Version(byte major, byte minor, byte micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    public byte[] getBytes() {
        return ByteBuffer.allocate(3).put(major).put(minor).put(micro).array();
    }

    public int compare(int major, int minor, int micro) {
        if (major > this.major || (major == this.major && (minor > this.minor || minor == this.minor && micro > this.micro))) {
            return -1;
        } else if (major == this.major && minor == this.minor && micro == this.micro) {
            return 0;
        } else {
            return 1;
        }
    }

    public static Version parse(byte[] bytes, int offset) {
        return new Version(bytes[offset], bytes[offset + 1], bytes[offset + 2]);
    }
}
