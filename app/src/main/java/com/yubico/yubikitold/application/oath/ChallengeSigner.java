package com.yubico.yubikitold.application.oath;

public interface ChallengeSigner {
    byte[] sign(byte[] challenge);
}
