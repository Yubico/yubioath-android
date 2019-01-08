package com.yubico.yubioath.keystore

import com.yubico.yubikit.application.oath.ChallengeSigner

interface StoredSigner : ChallengeSigner {
    fun promote()
}