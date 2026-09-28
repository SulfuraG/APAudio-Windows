package com.example.apaudio

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

/**
 * Generates the AirPlay 1 Apple-Response value without Android dependencies.
 *
 * Protocol behavior and RSA compatibility key provenance are documented in
 * docs/RAOP_PROVENANCE.md.
 */
internal object AppleChallengeResponse {
    private const val MAX_CHALLENGE_BYTES = 16
    private const val MIN_PAYLOAD_BYTES = 32
    private const val AUTH_RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val AES_KEY_RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"

    private val privateKey: PrivateKey by lazy {
        val keyBytes = decodeBase64(PRIVATE_KEY_PKCS8_BASE64)
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    fun create(
        challenge: ByteArray,
        localAddress: ByteArray,
        deviceIdHex: String
    ): ByteArray {
        val payload = buildPayload(challenge, localAddress, deviceIdHex)
        return Cipher.getInstance(AUTH_RSA_TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, privateKey)
            doFinal(payload)
        }
    }

    fun decryptAesKey(encryptedKey: ByteArray): ByteArray =
        Cipher.getInstance(AES_KEY_RSA_TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, privateKey)
            doFinal(encryptedKey)
        }

    internal fun buildPayload(
        challenge: ByteArray,
        localAddress: ByteArray,
        deviceIdHex: String
    ): ByteArray {
        require(challenge.size <= MAX_CHALLENGE_BYTES) {
            "Apple-Challenge must be at most $MAX_CHALLENGE_BYTES bytes"
        }
        require(localAddress.size == 4 || localAddress.size == 16) {
            "Local address must contain 4 IPv4 bytes or 16 IPv6 bytes"
        }

        val deviceId = decodeDeviceId(deviceIdHex)
        val contentLength = challenge.size + localAddress.size + deviceId.size
        return ByteArray(maxOf(MIN_PAYLOAD_BYTES, contentLength)).also { payload ->
            var offset = 0
            challenge.copyInto(payload, destinationOffset = offset)
            offset += challenge.size
            localAddress.copyInto(payload, destinationOffset = offset)
            offset += localAddress.size
            deviceId.copyInto(payload, destinationOffset = offset)
        }
    }

    private fun decodeDeviceId(deviceIdHex: String): ByteArray {
        require(deviceIdHex.length == 12 && deviceIdHex.all { it.digitToIntOrNull(16) != null }) {
            "RAOP device ID must be 12 hexadecimal characters"
        }
        return ByteArray(6) { index ->
            deviceIdHex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    // Kept local so the protocol core remains usable on Android API 24-25, where
    // java.util.Base64 is unavailable.
    private fun decodeBase64(value: String): ByteArray {
        val input = value.filterNot { it.isWhitespace() }
        require(input.length % 4 == 0) { "Invalid Base64 length" }
        val padding = when {
            input.endsWith("==") -> 2
            input.endsWith('=') -> 1
            else -> 0
        }
        val output = ByteArray(input.length / 4 * 3 - padding)
        var outputIndex = 0

        for (offset in input.indices step 4) {
            val a = base64Value(input[offset])
            val b = base64Value(input[offset + 1])
            val c = if (input[offset + 2] == '=') 0 else base64Value(input[offset + 2])
            val d = if (input[offset + 3] == '=') 0 else base64Value(input[offset + 3])
            val bits = (a shl 18) or (b shl 12) or (c shl 6) or d

            if (outputIndex < output.size) output[outputIndex++] = (bits ushr 16).toByte()
            if (outputIndex < output.size) output[outputIndex++] = (bits ushr 8).toByte()
            if (outputIndex < output.size) output[outputIndex++] = bits.toByte()
        }
        return output
    }

    private fun base64Value(character: Char): Int {
        val value = BASE64_ALPHABET.indexOf(character)
        require(value >= 0) { "Invalid Base64 character" }
        return value
    }

    private const val BASE64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val PRIVATE_KEY_PKCS8_BASE64 = """
        MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDn10TyouJ4i2wfVaCOtwVEqPp5RaqL5sYs5fUcvdTcaEL+PRCD
        3S7ewb/UJS3ALm85i98OYUjqhIVeLkQtptYmZPZ0ofMEkpreT2iT7y325xGox3oNkcnZgIIuUNEpIq/qQOqfDhTA92k4xfOIL8Ay
        Pdn+VRVfUbtZIcIBYp/XM1LV4u+qv5ugSNe4E6K2dn9sPM8etM5nPQN7DS6jDF//6wb40Ird5AlXGpxon+8QcohV3Yz7movvXIlD
        7ztfqhXd5pi+3fNZlgPrPm9hNyu2KPZVn1maeL9QBoeqf0l2wFYtQSlW+JieGKY1W9gVl4JeD8h1ND7HghF2Jc2/mER7AgMBAAEC
        ggEBAOXwDHL1d9YEuaTOQSKqhLAXQ+yZWs/Mf0qyfAsYf5BmW+NZ3xJZgY3u7XnTse+EXk3d2smhVTc7XicNjhMVABouUn1UzfkA
        CldovJjURGs3u70Asp3YtTBiEzsqbnf07jJQViKQTacg+xwSwDmW2nE6BQYJjtvt7Pk20PqcvVkpq7Dto1eZUC+YlNy4/FaaiS0X
        eAMkorbDFm40ZwkTS4VAQbhncGtY/vKg25Ird2KLaOaWk8evQ78qc9C3Mjd6C6F7RPBR6b95hJ3LMzJXH9inCTPC1gvexHmTSj2s
        pAu28vN8Cp0HEG6tyLNpoD8vQciACY6K3UYkDaxozFNU82ECgYEA9+C/Wh5nGDGai2IJwxcURARZ+XOFZhOxeuFQi7PmMW5rf0Yt
        L31kQSuEt2vCPysMNWJFUnmyQ6n3MW+VgAezTGH3aOLUTtX/KycoF+wys+STkpIo+ueOd0yg9169adWSAnmPEW42DGQ4sy4b2Lnc
        HjIy8NMJGIg8xD743aIsNpECgYEA72//+ZTx5WRBqgA1/RmgyNbwI3jHBYDZxIQgeR30B8WR+26/yjIsMIbdkB/S+uGuu2St9rt5
        /4BRvr0M2CCriYdABgGnsv6TkMrMmsq47Sv5HRhtj2lkPX7+D11W33V3otA16lQT/JjY8/kI2gWaN52kscw48V1WCoPMMXFTyEsC
        gYEA0OuvvEAluoGMdXAjNDhOj2lvgE16oOd2TlB7t9Pf78fWeMZoLT+tcTRBvurnJKCewJvcO8BwnJEz1Ins4qUa3QUxJ0kPkobR
        c8ikBU3CCldcfkwMmDT0od6HSRej5ADq+IUGLbXLfjQ2iecR91/ng9fhkZL9dpzVQr6kuQEH7NECgYB/QBjcfeopLaUwQjhvMQWg
        d4rcbz3mkNordMUFWYPt9XRmGi/Xt96AU8zA4gjwyKxib1l9PZnSzlGjezmuS36e8sB18L89g8rNMtqWkZLCiZI1glwH0c0yWaGQ
        bNzUmcthPiLJTLHqlxkGYJ3xsPSLBj8XNyA0NpSZtf35cO9EDQKBgQCQTukg+UTvWq98lCCgD16bSAgsC4Tg+7XdoqImd9+3uEiN
        sr7mTJvdPKxm+jIOdvcc4q8icru9dsq5TghKDEHZsHcdxjNAwazPWonaAbQ3mG8mnPDCFuFeoUoDjNppKvDrbbAOeIArkyUgTS0g
        Aoo/jLE0aOgPZBiOEEa6G+RYpg==
    """
}
