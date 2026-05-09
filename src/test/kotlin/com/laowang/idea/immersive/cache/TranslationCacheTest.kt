package com.laowang.idea.immersive.cache

import com.laowang.idea.immersive.core.Translation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationCacheTest {
    @Test
    fun `put get and has return cached translation`() {
        val cache = TranslationCache(maxEntries = 2)
        val translation = translation("first")

        cache.put(translation)

        assertTrue(cache.has("first"))
        assertEquals(translation, cache.get("first"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `put evicts oldest entry when max size exceeded`() {
        val cache = TranslationCache(maxEntries = 2)

        cache.put(translation("first"))
        cache.put(translation("second"))
        cache.put(translation("third"))

        assertFalse(cache.has("first"))
        assertTrue(cache.has("second"))
        assertTrue(cache.has("third"))
        assertEquals(2, cache.size())
    }

    @Test
    fun `get refreshes LRU order before next eviction`() {
        val cache = TranslationCache(maxEntries = 2)

        cache.put(translation("first"))
        cache.put(translation("second"))
        assertEquals("text-first", cache.get("first")?.translatedText)

        cache.put(translation("third"))

        assertTrue(cache.has("first"))
        assertFalse(cache.has("second"))
        assertTrue(cache.has("third"))
    }

    @Test
    fun `clear removes all entries`() {
        val cache = TranslationCache(maxEntries = 2)

        cache.put(translation("first"))
        cache.put(translation("second"))

        cache.clear()

        assertEquals(0, cache.size())
        assertFalse(cache.has("first"))
        assertFalse(cache.has("second"))
        assertNull(cache.get("first"))
    }

    private fun translation(id: String): Translation =
        Translation(
            segmentId = id,
            translatedText = "text-$id",
            engineId = "openai",
            timestamp = id.hashCode().toLong(),
        )
}
