package com.laowang.idea.immersive.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
class CredentialsStore(
    private val backend: CredentialBackend = PasswordSafeCredentialBackend(),
    private val serviceNameProvider: (String, String) -> String = ::generateServiceName,
) {
    fun getApiKey(): String? =
        getApiKey(ProviderIds.OPENAI)

    fun getApiKey(providerId: String): String? =
        backend.get(apiKeyAttributes(providerId))?.getPasswordAsString()?.trim()?.takeIf { it.isNotEmpty() }

    fun setApiKey(apiKey: String?) {
        setApiKey(ProviderIds.OPENAI, apiKey)
    }

    fun setApiKey(providerId: String, apiKey: String?) {
        val normalized = apiKey?.trim()?.takeIf { it.isNotEmpty() }
        backend.set(
            apiKeyAttributes(providerId),
            normalized?.let { Credentials(null, it) },
        )
    }

    fun clear() {
        clear(ProviderIds.OPENAI)
    }

    fun clear(providerId: String) {
        backend.set(apiKeyAttributes(providerId), null)
    }

    private fun apiKeyAttributes(providerId: String): CredentialAttributes =
        CredentialAttributes(serviceNameProvider(SERVICE_SUBSYSTEM, "${providerId.trim()}::$API_KEY_KEY"))

    companion object {
        private const val SERVICE_SUBSYSTEM = "Immersive Translate"
        private const val API_KEY_KEY = "api-key"

        fun getInstance(): CredentialsStore = service()
    }

    interface CredentialBackend {
        fun get(attributes: CredentialAttributes): Credentials?

        fun set(attributes: CredentialAttributes, credentials: Credentials?)
    }

    private class PasswordSafeCredentialBackend : CredentialBackend {
        override fun get(attributes: CredentialAttributes): Credentials? = PasswordSafe.instance.get(attributes)

        override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
            PasswordSafe.instance.set(attributes, credentials)
        }
    }
}
