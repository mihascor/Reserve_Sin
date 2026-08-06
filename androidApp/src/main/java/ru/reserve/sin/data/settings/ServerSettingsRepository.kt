package ru.reserve.sin.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val KEY_ALIAS = "reserve_sin_api_token"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val DEFAULT_SERVER_URL = "https://reserve-sin.duckdns.org"
private val Context.serverSettingsDataStore by preferencesDataStore(name = "server_settings")
private val serverUrlKey = stringPreferencesKey("server_url")
private val encryptedTokenKey = stringPreferencesKey("encrypted_api_token")
private val tokenIvKey = stringPreferencesKey("api_token_iv")

data class ServerSettings(val serverUrl: String, val hasToken: Boolean)

class ServerSettingsRepository(private val context: Context) {
    val settings: Flow<ServerSettings> = context.serverSettingsDataStore.data.map { preferences ->
        ServerSettings(
            serverUrl = preferences[serverUrlKey] ?: DEFAULT_SERVER_URL,
            hasToken = preferences[encryptedTokenKey] != null && preferences[tokenIvKey] != null,
        )
    }

    suspend fun save(serverUrl: String, token: String) {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        context.serverSettingsDataStore.edit { preferences ->
            preferences[serverUrlKey] = normalizedUrl
            if (token.isNotBlank()) {
                val encrypted = encrypt(token.trim())
                preferences[encryptedTokenKey] = encrypted.value
                preferences[tokenIvKey] = encrypted.iv
            }
        }
    }

    suspend fun token(): String? {
        val preferences = context.serverSettingsDataStore.data.first()
        val encrypted = preferences[encryptedTokenKey] ?: return null
        val iv = preferences[tokenIvKey] ?: return null
        return decrypt(encrypted, iv)
    }

    private fun normalizeServerUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        val uri = runCatching { URI(normalized) }.getOrNull()
        require(uri != null && uri.scheme == "https" && uri.host != null && uri.path.isNullOrEmpty() && uri.query == null) {
            "Укажите HTTPS-адрес сервера без пути"
        }
        return normalized
    }

    private fun encrypt(value: String): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return EncryptedValue(
            value = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    private fun decrypt(value: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private data class EncryptedValue(val value: String, val iv: String)
}
