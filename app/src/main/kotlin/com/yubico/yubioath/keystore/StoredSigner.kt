package com.yubico.yubioath.keystore

import com.yubico.yubioath.protocol.ChallengeSigner

interface StoredSigner : ChallengeSigner {
    fun promote()
}