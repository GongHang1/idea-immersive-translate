package com.laowang.idea.immersive.cache

import com.intellij.openapi.components.Service
import com.laowang.idea.immersive.core.Translation

@Service(Service.Level.APP)
class TranslationCache(
    private val maxEntries: Int,
) {
    constructor() : this(DEFAULT_MAX_ENTRIES)

    private val cache = object : LinkedHashMap<String, Translation>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translation>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun put(translation: Translation) {
        cache[translation.segmentId] = translation
    }

    @Synchronized
    fun get(segmentId: String): Translation? = cache[segmentId]

    @Synchronized
    fun has(segmentId: String): Boolean = cache.containsKey(segmentId)

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    fun size(): Int = cache.size

    companion object {
        private const val DEFAULT_MAX_ENTRIES = 5000
    }
}
