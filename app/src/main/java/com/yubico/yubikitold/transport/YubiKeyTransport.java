package com.yubico.yubikitold.transport;

import java.io.IOException;

public interface YubiKeyTransport {
    boolean hasIso7816();

    Iso7816Connection connect() throws IOException;

}
