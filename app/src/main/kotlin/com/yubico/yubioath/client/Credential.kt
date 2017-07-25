package com.yubico.yubioath.client

import com.yubico.yubioath.protocol.OathType
import java.util.*

class Credential(val parentId:ByteArray, val key:String, val type: OathType, val touch: Boolean) {
    val issuer:String?
    val name:String
    val period:Int

    init {
        var data = key
        var issuerData = if(':' in data) {
            val parts = data.split(':', limit = 2)
            data = parts[1]
            parts[0]
        } else null

        period = issuerData?.let {
            if(it.contains(Regex("""^\d+/"""))) {
                val parts = it.split('/', limit = 2)
                issuerData = parts[1]
                parts[0].toInt()
            } else null
        } ?: 30

        issuer = issuerData
        name = data
    }

    override fun equals(other: Any?): Boolean {
        return other is Credential && Arrays.equals(parentId, other.parentId) && key == other.key
    }

    override fun hashCode(): Int {
        return 31 * Arrays.hashCode(parentId) + key.hashCode()
    }
}