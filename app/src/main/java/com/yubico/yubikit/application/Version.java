package com.yubico.yubikit.application;

import java.nio.ByteBuffer;
import java.util.Locale;

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

    public int compareOld(int major, int minor, int micro) {
        if (major > this.major || (major == this.major && (minor > this.minor || minor == this.minor && micro > this.micro))) {
            return -1;
        } else if (major == this.major && minor == this.minor && micro == this.micro) {
            return 0;
        } else {
            return 1;
        }
    }

    public boolean isLessThan(int major, int minor, int micro) {
        return this.major < major || (this.major == major && (this.minor < minor || (this.minor == minor && this.micro < micro)));
    }

    public boolean isAtLeast(int major, int minor, int micro) {
        return !isLessThan(major, minor, micro);
    }

    public static Version parse(byte[] bytes, int offset) {
        return new Version(bytes[offset], bytes[offset + 1], bytes[offset + 2]);
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%d.%d.%d", 0xff & major, 0xff & minor, 0xff & micro);
    }
}
