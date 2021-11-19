package io.curity.bff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class CookieEncrypter(private val config: BFFConfiguration, private val cookieName: CookieName)
{

    private val key = getKeyFromPassword()

    private fun getKeyFromPassword(): SecretKey {

        /*
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(config.encKey.toCharArray(), config.salt.toByteArray(), 65536, 256)
        return SecretKeySpec(
            factory.generateSecret(spec)
                .encoded, "AES"
        )*/

        // This is an attempt to enable the BFF token plugin to decrypt the cookie correctly
        return SecretKeySpec(config.encKey.toByteArray(), 0, config.encKey.length, "AES")
    }

    suspend fun getEncryptedCookie(cookieName: String, cookieValue: String, cookieOptions: CookieSerializeOptions) =
        encryptValue(cookieValue).serializeToCookie(cookieName, cookieOptions)

    suspend fun getEncryptedCookie(cookieName: String, cookieValue: String): String =
        encryptValue(cookieValue).serializeToCookie(cookieName, config.cookieSerializeOptions)

    suspend fun encryptValue(value: String): String
    {
        return withContext(Dispatchers.Default) {
            kotlin.run {
                val iv = generateIv()
                return@withContext "${
                    iv.iv.toHexString()
                }:${encrypt("AES/CBC/PKCS5Padding", value, iv)}"
            }
        }
    }

    fun generateIv(): IvParameterSpec
    {
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        return IvParameterSpec(iv)
    }

    fun encrypt(
        algorithm: String, input: String, iv: IvParameterSpec
    ): String
    {
        val cipher: Cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val cipherText: ByteArray = cipher.doFinal(input.toByteArray())
        return cipherText.toHexString()
    }

    private fun decrypt(
        algorithm: String, cipherText: String, key: SecretKey, iv: IvParameterSpec
    ): String
    {
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val plainText = cipher.doFinal(cipherText.decodeHex())
        return String(plainText)
    }

    suspend fun decryptValueFromCookie(cookieValue: String): String
    {
        return withContext(Dispatchers.Default) {
            val valueArray = cookieValue.split(":")

            val iv = valueArray[0]
            val cipherText = valueArray[1]

            return@withContext decrypt("AES/CBC/PKCS5Padding", cipherText, key, IvParameterSpec(iv.decodeHex()))
        }
    }

    fun String.decodeHex(): ByteArray
    {
        return ByteArray(length / 2) { current ->
            Integer.parseInt(this, current * 2, (current + 1) * 2, 16).toByte()
        }
    }

    fun String.serializeToCookie(name: String, options: CookieSerializeOptions): String
    {
        val builder = StringBuilder()
        builder.append(name).append('=')
        builder.append(this)

        builder.append("; Domain=").append(options.domain)
        builder.append("; Path=").append(options.path)
        if (options.secure)
        {
            builder.append("; Secure")
        }

        builder.append("; HttpOnly")

        if (options.sameSite)
        {
            builder.append("; SameSite=true")
        }

        val expiresInSeconds = options.expiresInSeconds
        if (expiresInSeconds != null)
        {
            if (expiresInSeconds > -1)
            {
                builder.append("; Max-Age=").append(options.expiresInSeconds)
            }

            val expires =
                if (expiresInSeconds != 0) ZonedDateTime.now()
                    .plusSeconds(expiresInSeconds.toLong()) else Instant.EPOCH.atZone(ZoneOffset.UTC)
            builder.append("; Expires=").append(expires.format(DateTimeFormatter.RFC_1123_DATE_TIME))
        }

        return builder.toString()
    }

    fun getCookieForUnset(cookieName: String): String
    {
        val options = config.cookieSerializeOptions.copy(expiresInSeconds = minusDayInSeconds)
        return "".serializeToCookie(cookieName, options)
    }

    fun getCookiesForUnset(): List<String>
    {
        val options = config.cookieSerializeOptions.copy(expiresInSeconds = minusDayInSeconds)
        return cookieName.cookiesForUnset.map { "".serializeToCookie(it, options) }
    }

    companion object
    {
        private val minusDayInSeconds = -Duration.ofDays(1).toSeconds().toInt()
    }
}
