package com.yubico.yubioath.client

data class Code(val value: String, val validFrom: Long, val validUntil: Long)