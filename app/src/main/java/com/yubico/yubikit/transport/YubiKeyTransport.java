package com.yubico.yubikit.transport;

import java.io.IOException;

import androidx.annotation.Nullable;

public interface YubiKeyTransport {
    boolean hasIso7816();

    Iso7816Connection connect() throws IOException;

}
