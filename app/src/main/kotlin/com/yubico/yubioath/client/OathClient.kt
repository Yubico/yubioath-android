package com.yubico.yubioath.client

import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.protocol.ChallengeSigner
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.protocol.YkOathApi
import com.yubico.yubioath.transport.Backend
import java.io.Closeable
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class OathClient(backend: Backend, private val keyManager: KeyManager) : Closeable {
    private val api: YkOathApi = YkOathApi(backend)
    val deviceInfo = api.deviceInfo

    init {
        if (api.isLocked()) {
            var missing = true
            keyManager.getKeys(deviceInfo.id).find {
                missing = false
                api.unlock(it)
            }?.apply {
                promote()
            } ?: throw PasswordRequiredException(if (missing) "Password is missing" else "Password is incorrect!", deviceInfo.id, api.deviceSalt, missing)
        }
    }

    override fun close() = api.close()

    private fun ensureOwnership(credential: Credential) {
        if (deviceInfo.id != credential.deviceId) {
            throw IllegalArgumentException("Credential parent ID doesn't match!")
        }
    }

    fun setPassword(oldPassword: String, newPassword: String, remember: Boolean): Boolean {
        api.reselect()
        if (api.isLocked()) {
            if (oldPassword.isEmpty()) return false

            sequenceOf(false, true).find { legacy ->
                api.unlock(object : ChallengeSigner {
                    override fun sign(input: ByteArray): ByteArray {
                        return Mac.getInstance("HmacSHA1").apply {
                            init(SecretKeySpec(KeyManager.calculateSecret(oldPassword, api.deviceSalt, legacy), algorithm))
                        }.doFinal(input)
                    }
                })
            } ?: return false
        }

        if (newPassword.isEmpty()) {
            api.unsetLockCode()
            keyManager.clearKeys(deviceInfo.id)
        } else {
            val secret = KeyManager.calculateSecret(newPassword, api.deviceSalt, false)
            api.setLockCode(secret)
            keyManager.addKey(deviceInfo.id, secret, remember)
        }
        return true
    }

    fun calculate(credential: Credential, timestamp: Long): Code {
        ensureOwnership(credential)

        val timeStep = (timestamp / 1000 / credential.period)
        val challenge = ByteBuffer.allocate(8).putLong(timeStep).array()

        val value = when (credential.issuer) {
            "Steam" -> formatSteam(api.calculate(credential.key, challenge, false))
            else -> formatTruncated(api.calculate(credential.key, challenge))
        }

        val (validFrom, validUntil) = when (credential.type) {
            OathType.TOTP -> Pair(timeStep * 1000 * credential.period, (timeStep + 1) * 1000 * credential.period)
            OathType.HOTP -> Pair(System.currentTimeMillis(), Long.MAX_VALUE)
        }

        return Code(value, validFrom, validUntil)
    }

    fun refreshCodes(timestamp: Long, existing: MutableMap<Credential, Code?>): MutableMap<Credential, Code?> {
        // Default to 30 second period
        val timeStep = (timestamp / 1000 / 30)
        val challenge = ByteBuffer.allocate(8).putLong(timeStep).array()

        return api.calculateAll(challenge).map {
            val credential = Credential(deviceInfo.id, it.key, it.oathType, it.touch)
            val existingCode = existing[credential]
            val code: Code? = if (it.data.size > 1) {
                if (credential.period != 30 || credential.issuer == "Steam") {
                    //Recalculate needed for for periods != 30 or Steam credentials
                    if (existingCode != null && existingCode.validUntil > timestamp) existingCode else calculate(credential, timestamp)
                } else {
                    Code(formatTruncated(it.data), timeStep * 30 * 1000, (timeStep + 1) * 30 * 1000)
                }
            } else existingCode

            Pair(credential, code)
        }.toMap().toSortedMap(compareBy<Credential> { it.issuer }.thenBy { it.name })
    }

    fun delete(credential: Credential) {
        ensureOwnership(credential)
        api.deleteCode(credential.key)
    }

    fun addCredential(data: CredentialData): Credential {
        with(data) {
            if (issuer != null) {
                name = "$issuer:$name"
            }
            if (oathType == OathType.TOTP && period != 30) {
                name = "$period/$name"
            }
            api.putCode(name, secret, oathType, algorithm, digits, counter, touch)
            return Credential(deviceInfo.id, name, oathType, touch)
        }
    }

    companion object {
        private val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

        private fun formatTruncated(data: ByteArray): String {
            return with(ByteBuffer.wrap(data)) {
                val digits = get().toInt()
                int.toString().takeLast(digits).padStart(digits, '0')
            }
        }

        private fun formatSteam(data: ByteArray): String {
            val offs = 0xf and data[data.size - 1].toInt() + 1
            var code = 0x7fffffff and ByteBuffer.wrap(data.copyOfRange(offs, offs + 4)).int
            return StringBuilder().apply {
                for (i in 0..4) {
                    append(STEAM_CHARS[code % STEAM_CHARS.length])
                    code /= STEAM_CHARS.length
                }
            }.toString()
        }
    }
}