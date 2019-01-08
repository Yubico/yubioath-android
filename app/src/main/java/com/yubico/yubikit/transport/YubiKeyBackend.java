package com.yubico.yubikit.transport;

import java.io.Closeable;
import java.io.IOException;

import androidx.annotation.Nullable;

public interface YubiKeyBackend extends Closeable {
    void connect() throws IOException;

    interface BackendHandler<T extends YubiKeyBackend> {
        void onYubiKeyBackend(@Nullable T backend);
    }
}
