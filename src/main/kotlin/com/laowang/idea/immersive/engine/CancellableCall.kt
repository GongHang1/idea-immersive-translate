package com.laowang.idea.immersive.engine

import com.laowang.idea.immersive.core.TranslationCancellationToken
import com.laowang.idea.immersive.core.TranslationCancelledException
import java.io.IOException
import okhttp3.Call

internal inline fun <T> Call.executeCancellable(
    cancellationToken: TranslationCancellationToken,
    block: (okhttp3.Response) -> T,
): T {
    cancellationToken.throwIfCancelled()
    val registration = cancellationToken.onCancel { cancel() }
    return try {
        cancellationToken.throwIfCancelled()
        execute().use(block)
    } catch (exception: IOException) {
        if (cancellationToken.isCancelled) {
            throw TranslationCancelledException(exception)
        }
        throw exception
    } finally {
        registration.close()
    }
}
