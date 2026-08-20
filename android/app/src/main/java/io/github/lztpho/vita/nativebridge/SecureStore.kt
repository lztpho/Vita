// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object ProviderBinding {
    fun normalizeScope(value: String): String = value.trim().toHttpUrlOrNull()?.toString()?.trimEnd('/')
        ?: value.trim().trimEnd('/')

    fun replaceProvider(old: JSONObject, provider: JSONObject): JSONObject {
        val oldScope = normalizeScope(old.optString("scope"))
        val newScope = normalizeScope(provider.optString("baseUrl"))
        return JSONObject().put("provider", JSONObject(provider.toString())).put("scope", newScope).also { record ->
            if (oldScope == newScope && old.optString("apiKey").isNotBlank()) record.put("apiKey", old.getString("apiKey"))
        }
    }

    fun bindKey(record: JSONObject, value: String, scope: String): JSONObject {
        val key = value.trim()
        require(key.isNotBlank() && key.length <= 4096) { "API Key 不能为空或过长" }
        val normalized = normalizeScope(scope)
        require(normalizeScope(record.optString("scope")) == normalized) { "API Key 与当前端点不匹配，请先保存模型设置" }
        return JSONObject(record.toString()).put("scope", normalized).put("apiKey", key)
    }

    fun keyFor(record: JSONObject, scope: String): String = if (
        normalizeScope(record.optString("scope")) == normalizeScope(scope)
    ) record.optString("apiKey") else ""
}

class SecureStore(private val context: Context) {
    companion object {
        private const val KEY_ALIAS = "vita-app-master-v1"
        private const val PREFS = "vita-app-secure-v1"
        private const val PROVIDER_RECORD = "provider-record"
        private const val DB_KEY = "database-key"
        private const val LATEST_MEAL_TASK = "latest-meal-task"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    fun encryptBytes(clear: ByteArray, aad: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(clear)
        return byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + encrypted
    }

    fun decryptBytes(sealed: ByteArray, aad: String): ByteArray {
        require(sealed.size > 14 && sealed[0].toInt() == 1) { "Encrypted value is invalid" }
        val ivLength = sealed[1].toInt() and 0xff
        require(ivLength in 12..16 && sealed.size > ivLength + 2) { "Encrypted value is invalid" }
        val iv = sealed.copyOfRange(2, 2 + ivLength)
        val encrypted = sealed.copyOfRange(2 + ivLength, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted)
    }

    private fun putEncrypted(name: String, value: ByteArray?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(name)
        else editor.putString(name, Base64.encodeToString(encryptBytes(value, "pref:$name"), Base64.NO_WRAP))
        check(editor.commit()) { "无法保存加密配置" }
    }

    private fun getEncrypted(name: String): ByteArray? {
        val encoded = prefs.getString(name, null) ?: return null
        return decryptBytes(Base64.decode(encoded, Base64.NO_WRAP), "pref:$name")
    }

    private fun providerRecord(): JSONObject = getEncrypted(PROVIDER_RECORD)?.let {
        JSONObject(String(it, Charsets.UTF_8))
    } ?: JSONObject()

    fun provider(): JSONObject = providerRecord().optJSONObject("provider") ?: JSONObject()

    @Synchronized fun saveProvider(value: JSONObject) {
        val old = providerRecord()
        val record = ProviderBinding.replaceProvider(old, value)
        putEncrypted(PROVIDER_RECORD, record.toString().toByteArray(Charsets.UTF_8))
    }

    fun apiKey(scope: String): String {
        return ProviderBinding.keyFor(providerRecord(), scope)
    }

    fun hasApiKey(scope: String): Boolean = apiKey(scope).isNotEmpty()

    @Synchronized fun saveApiKey(value: String, scope: String) {
        val record = ProviderBinding.bindKey(providerRecord(), value, scope)
        putEncrypted(PROVIDER_RECORD, record.toString().toByteArray(Charsets.UTF_8))
    }

    @Synchronized fun clearApiKey() {
        val record = providerRecord()
        record.remove("apiKey")
        putEncrypted(PROVIDER_RECORD, record.toString().toByteArray(Charsets.UTF_8))
    }

    fun databaseKey(): ByteArray {
        getEncrypted(DB_KEY)?.let { return it }
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
        putEncrypted(DB_KEY, generated)
        return generated
    }

    fun saveMealTask(taskId: String, value: JSONObject) {
        putEncrypted("meal-task:$taskId", value.toString().toByteArray(Charsets.UTF_8))
        putEncrypted(LATEST_MEAL_TASK, taskId.toByteArray(Charsets.UTF_8))
    }

    fun mealTask(taskId: String): JSONObject? = getEncrypted("meal-task:$taskId")?.let {
        JSONObject(String(it, Charsets.UTF_8))
    }

    fun latestMealTaskId(): String = getEncrypted(LATEST_MEAL_TASK)?.let {
        String(it, Charsets.UTF_8)
    }.orEmpty()

    fun clearMealTask(taskId: String) {
        putEncrypted("meal-task:$taskId", null)
        if (latestMealTaskId() == taskId) putEncrypted(LATEST_MEAL_TASK, null)
    }

    @Synchronized fun clearAll() {
        check(prefs.edit().clear().commit()) { "无法清除加密配置" }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }
}
