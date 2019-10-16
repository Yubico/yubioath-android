package com.yubico.yubioath.keystore

import com.yubico.yubikitold.application.oath.ChallengeSigner

interface StoredSigner : ChallengeSigner {
    fun promote()
}