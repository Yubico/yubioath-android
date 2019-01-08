package com.yubico.yubioath.client

import android.util.Base64
import com.yubico.yubikit.application.ApduException
import com.yubico.yubikit.application.oath.CalculateResponse
import com.yubico.yubikit.application.oath.OathApplication
import com.yubico.yubikit.application.oath.OathType
import com.yubico.yubikit.transport.Iso7816Backend
import com.yubico.yubikit.transport.usb.UsbBackend
import com.yubico.yubioath.exc.DuplicateKeyException
import com.yubico.yubioath.exc.PasswordRequiredException
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class OathClient(backend: Iso7816Backend, private val keyManager: KeyManager) {
    private val api: OathApplication = OathApplication(backend)
    val deviceInfo: DeviceInfo

    init {
        api.select()
        deviceInfo = DeviceInfo(getDeviceId(api.deviceId), backend is UsbBackend, api.version, api.isLocked)
        if (api.isLocked) {
            var missing = true
            keyManager.getKeys(deviceInfo.id).find {
                missing = false
                try {
                    api.unlock(it)
                    true
                } catch (e: ApduException) {
                    false
                }
            }?.apply {
                promote()
            } ?: throw PasswordRequiredException(if (missing) "Password is missing" else "Password is incorrect!", deviceInfo.id, api.deviceId, missing)
        }
    }

    private fun getDeviceId(id: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA256").apply {
            update(id)
        }.digest()

        return Base64.encodeToString(digest.sliceArray(0 until 16), Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun ensureOwnership(credential: Credential) {
        if (deviceInfo.id != credential.deviceId) {
            throw IllegalArgumentException("Credential parent ID doesn't match!")
        }
    }

    fun setPassword(oldPassword: String, newPassword: String, remember: Boolean): Boolean {
        api.select()
        if (api.isLocked) {
            if (oldPassword.isEmpty()) return false

            sequenceOf(false, true).find { legacy ->
                try {
                    api.unlock { input ->
                        Mac.getInstance("HmacSHA1").apply {
                            init(SecretKeySpec(KeyManager.calculateSecret(oldPassword, api.deviceId, legacy), algorithm))
                        }.doFinal(input)
                    }
                    true
                } catch (e: ApduException) {
                    false
                }
            } ?: return false
        }

        if (newPassword.isEmpty()) {
            api.unsetLockCode()
            keyManager.clearKeys(deviceInfo.id)
        } else {
            val secret = KeyManager.calculateSecret(newPassword, api.deviceId, false)
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
            else -> formatTruncated(api.calculate(credential.key, challenge, true))
        }

        val (validFrom, validUntil) = when (credential.type) {
            OathType.TOTP -> Pair(timeStep * 1000 * credential.period, (timeStep + 1) * 1000 * credential.period)
            OathType.HOTP, null -> Pair(System.currentTimeMillis(), Long.MAX_VALUE)
        }

        return Code(value, validFrom, validUntil)
    }

    fun refreshCodes(timestamp: Long, existing: Map<Credential, Code?>): MutableMap<Credential, Code?> {
        // Default to 30 second period
        val timeStep = (timestamp / 1000 / 30)
        val challenge = ByteBuffer.allocate(8).putLong(timeStep).array()

        return api.calculateAll(challenge).filter { !it.name.startsWith("_hidden:") }.map {
            val credential = Credential(deviceInfo.id, it.name, if(it.responseType == CalculateResponse.TYPE_HOTP) OathType.HOTP else OathType.TOTP, it.responseType == CalculateResponse.TYPE_TOUCH)
            val existingCode = existing[credential]
            val code: Code? = if (it.response.size > 1) {
                if (credential.period != 30 || credential.issuer == "Steam") {
                    //Recalculate needed for for periods != 30 or Steam credentials
                    if (existingCode != null && existingCode.validUntil > timestamp) existingCode else calculate(credential, timestamp)
                } else {
                    Code(formatTruncated(it), timeStep * 30 * 1000, (timeStep + 1) * 30 * 1000)
                }
            } else existingCode

            Pair(credential, code)
        }.toMap().toMutableMap()
    }

    fun delete(credential: Credential) {
        ensureOwnership(credential)
        api.deleteCredential(credential.key)
    }

    fun addCredential(data: CredentialData): Credential {
        with(data) {
            if (issuer != null) {
                name = "$issuer:$name"
            }
            if (oathType == OathType.TOTP && period != 30) {
                name = "$period/$name"
            }
            if (api.listCredentials().any { it.name == name }) {
                throw DuplicateKeyException()
            }
            api.putCredential(name, secret, oathType, algorithm, digits, counter, touch)
            return Credential(deviceInfo.id, name, oathType, touch)
        }
    }

    companion object {
        private const val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

        private fun formatTruncated(data: CalculateResponse): String {
            return with(data) {
                ByteBuffer.wrap(response).int.toString().takeLast(digits).padStart(digits, '0')
            }
        }

        private fun formatSteam(data: CalculateResponse): String {
            val response = data.response
            val offs = 0xf and response[response.size - 1].toInt() + 1
            var code = 0x7fffffff and ByteBuffer.wrap(response.copyOfRange(offs, offs + 4)).int
            return StringBuilder().apply {
                for (i in 0..4) {
                    append(STEAM_CHARS[code % STEAM_CHARS.length])
                    code /= STEAM_CHARS.length
                }
            }.toString()
        }
    }
}