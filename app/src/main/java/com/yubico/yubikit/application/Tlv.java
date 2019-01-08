package com.yubico.yubikit.application;

import android.util.SparseArray;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Tlv {
    private final byte[] data;
    private final int offset;
    private final int valueOffset;
    private final int end;

    public Tlv(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
        int length = 0xff & data[offset+1];
        int valuePos = 2;
        if (length > 0x80) {
            int nBytes = length - 0x80;
            length = 0;
            for (int i=0; i < nBytes; i++) {
                length = (length << 8) | (0xff & data[offset + valuePos + i]);
            }
            valuePos += nBytes;
        }
        this.valueOffset = offset + valuePos;
        this.end = valueOffset + length;
    }

    public Tlv(byte[] data) {
        this(data, 0);
    }

    public byte[] getBytes() {
        return Arrays.copyOfRange(data, offset, end);
    }

    public byte getTag() {
        return data[offset];
    }

    public int getLength() {
        return end - valueOffset;
    }

    public byte[] getValue() {
        return Arrays.copyOfRange(data, valueOffset, end);
    }

    public static Tlv of(byte tag) {
        return new Tlv(new byte[]{ tag, 0 });
    }

    public static Tlv of(byte tag, byte[] value) {
        byte[] lengthBytes;
        if (value.length < 0x80) {
            lengthBytes = new byte[] { (byte)value.length };
        } else if (value.length < 0xff) {
            lengthBytes = new byte[] { (byte)0x81, (byte)value.length };
        } else if (value.length <= 0xffff){
            lengthBytes = new byte[] { (byte)0x82, (byte)(value.length >> 8), (byte) value.length };
        } else {
            throw new IllegalArgumentException("Length of value is too large.");
        }

        return new Tlv(ByteBuffer.allocate(1 + lengthBytes.length + value.length).put(tag).put(lengthBytes).put(value).array());
    }

    public static Iterable<Tlv> parseTlvList(byte[] data, int offset) {
        List<Tlv> tlvs = new ArrayList<>();
        while (offset < data.length) {
            Tlv tlv = new Tlv(data, offset);
            tlvs.add(tlv);
            offset = tlv.end;
        }

        return tlvs;
    }

    public static final class Group {
        private final byte[] data;
        private final int offset;

        public Group(byte[] data, int offset) {
            this.data = data;
            this.offset = offset;
        }

        public Group(byte[] data) {
            this(data, 0);
        }

        public List<Tlv> toList() {
            List<Tlv> tlvs = new ArrayList<>();
            int offset = this.offset;
            while (offset < data.length) {
                Tlv tlv = new Tlv(data, offset);
                tlvs.add(tlv);
                offset = tlv.end;
            }
            return tlvs;
        }

        public SparseArray<byte[]> toDict() {
            SparseArray<byte[]> tlvs = new SparseArray<>();
            for (Tlv tlv : toList()) {
                tlvs.put(tlv.getTag(), tlv.getValue());
            }
            return tlvs;
        }

        public byte[] getBytes() {
            return Arrays.copyOfRange(data, offset, data.length);
        }

        public static Group of(Tlv... tlvs) {
            return of(Arrays.asList(tlvs));
        }

        public static Group of(byte k1, byte[] v1, byte k2, byte[] v2) {
            return of(Tlv.of(k1, v1), Tlv.of(k2, v2));
        }

        public static Group of(byte k1, byte[] v1, byte k2, byte[] v2, byte k3, byte[] v3) {
            return of(Tlv.of(k1, v1), Tlv.of(k2, v2), Tlv.of(k3, v3));
        }

        public static Group of(byte k1, byte[] v1, byte k2, byte[] v2, byte k3, byte[] v3, byte k4, byte[] v4) {
            return of(Tlv.of(k1, v1), Tlv.of(k2, v2), Tlv.of(k3, v3), Tlv.of(k4, v4));
        }

        public static Group of(Iterable<Tlv> tlvs) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            for (Tlv tlv : tlvs) {
                buffer.put(tlv.getBytes());
            }
            return new Group(Arrays.copyOfRange(buffer.array(), 0, buffer.position()));
        }

        public static Group of(SparseArray<byte[]> tlvs) {
            List<Tlv> list = new ArrayList<>();
            for (int i = 0; i < tlvs.size(); i++) {
                list.add(Tlv.of((byte)tlvs.keyAt(i), tlvs.valueAt(i)));
            }
            return of(list);
        }
    }

}
