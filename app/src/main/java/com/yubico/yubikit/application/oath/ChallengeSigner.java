package com.yubico.yubikit.application.oath;

public interface ChallengeSigner {
    byte[] sign(byte[] challenge);
}
