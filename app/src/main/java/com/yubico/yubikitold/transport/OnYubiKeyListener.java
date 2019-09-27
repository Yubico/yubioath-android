package com.yubico.yubikitold.transport;

import androidx.annotation.Nullable;

public interface OnYubiKeyListener {
    void onYubiKey(@Nullable YubiKeyTransport transport);
}
