package takagi.ru.monica.github.data

import okhttp3.RequestBody
import okio.BufferedSink

/** A GitHub mutation must not be replayed after a status or transport follow-up. */
internal fun RequestBody.githubSingleUseBody(): RequestBody {
    val delegate = this
    return object : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
    }
}
