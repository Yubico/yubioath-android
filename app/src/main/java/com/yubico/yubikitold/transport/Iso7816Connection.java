package com.yubico.yubikitold.transport;

import java.io.Closeable;
import java.io.IOException;

public interface Iso7816Connection extends Closeable {
    byte[] send(byte cla, byte ins, byte p1, byte p2, byte[] data) throws IOException;
}
