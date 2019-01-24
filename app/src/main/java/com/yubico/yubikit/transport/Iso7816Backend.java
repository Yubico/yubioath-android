package com.yubico.yubikit.transport;

import java.io.IOException;

public interface Iso7816Backend extends YubiKeyBackend {
    byte[] send(byte cla, byte ins, byte p1, byte p2, byte[] data) throws IOException;
}
