package com.laowang.idea.immersive.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CredentialsStoreTest {
    @Test
    fun `set and get api key round trip`() {
        val store = CredentialsStore(
            backend = InMemoryCredentialBackend(),
            serviceNameProvider = { subsystem, key -> "$subsystem::$key" },
        )

        store.setApiKey("sk-test")

        assertEquals("sk-test", store.getApiKey())
    }

    @Test
    fun `api keys are isolated by provider id`() {
        val store = CredentialsStore(
            backend = InMemoryCredentialBackend(),
            serviceNameProvider = { subsystem, key -> "$subsystem::$key" },
        )

        store.setApiKey(ProviderIds.OPENAI, "openai-key")
        store.setApiKey(ProviderIds.GEMINI, "gemini-key")

        assertEquals("openai-key", store.getApiKey(ProviderIds.OPENAI))
        assertEquals("gemini-key", store.getApiKey(ProviderIds.GEMINI))
    }

    @Test
    fun `set null unsets api key`() {
        val store = CredentialsStore(
            backend = InMemoryCredentialBackend(),
            serviceNameProvider = { subsystem, key -> "$subsystem::$key" },
        )
        store.setApiKey("sk-test")

        store.setApiKey(null)

        assertNull(store.getApiKey())
    }

    @Test
    fun `clear removes stored api key`() {
        val store = CredentialsStore(
            backend = InMemoryCredentialBackend(),
            serviceNameProvider = { subsystem, key -> "$subsystem::$key" },
        )
        store.setApiKey("sk-test")

        store.clear()

        assertNull(store.getApiKey())
    }

    private class InMemoryCredentialBackend : CredentialsStore.CredentialBackend {
        private val store = mutableMapOf<String, Credentials?>()

        override fun get(attributes: CredentialAttributes): Credentials? = store[attributes.serviceName]

        override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
            if (credentials == null) {
                store.remove(attributes.serviceName)
            } else {
                store[attributes.serviceName] = credentials
            }
        }
    }
}
