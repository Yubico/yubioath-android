package com.yubico.yubikit.application.oath;

import com.yubico.yubikit.application.Tlv;

import java.nio.charset.Charset;

public class ListResponse {
    public final String name;
    public final OathType oathType;
    public final HashAlgorithm hashAlgorithm;

    ListResponse(Tlv respone) {
        byte[] value = respone.getValue();
        this.name = new String(value, 1, value.length - 1, Charset.forName("UTF-8"));
        this.oathType = OathType.fromValue((byte) (0xf0 & value[0]));
        this.hashAlgorithm = HashAlgorithm.fromValue((byte) (0x0f & value[0]));
    }
}
