package com.yubico.yubikit.transport;

import androidx.annotation.Nullable;

public interface OnYubiKeyListener {
    void onYubiKey(@Nullable YubiKeyTransport transport);
}
