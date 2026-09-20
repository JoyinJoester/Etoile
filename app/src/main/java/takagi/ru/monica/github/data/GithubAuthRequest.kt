package takagi.ru.monica.github.data

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Keeps cancellation attached until the response body has also been read. */
internal suspend fun <T> Call.readAuthResponse(read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use(read) }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
