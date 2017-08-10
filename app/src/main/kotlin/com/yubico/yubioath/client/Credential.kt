package com.yubico.yubioath.client

import com.yubico.yubioath.protocol.OathType
import java.util.*

class Credential(val parentId:ByteArray, val key:String, val type: OathType, val touch: Boolean) {
    val issuer:String?
    val name:String
    val period:Int

    init {
        var data = key

        period = if(data.contains(Regex("""^\d+/"""))) {
            val parts = data.split('/', limit = 2)
            data = parts[1]
            parts[0].toInt()
        } else 30

        issuer = if(':' in data) {
            val parts = data.split(':', limit = 2)
            data = parts[1]
            parts[0]
        } else null

        name = data
    }

    override fun toString(): String = "Credential($key)"

    override fun equals(other: Any?): Boolean {
        return other is Credential && Arrays.equals(parentId, other.parentId) && key == other.key
    }

    override fun hashCode(): Int {
        return 31 * Arrays.hashCode(parentId) + key.hashCode()
    }
}