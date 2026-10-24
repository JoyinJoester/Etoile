package takagi.ru.monica.gitlab.data

import okhttp3.RequestBody
import okio.BufferedSink

/** Do not replay writes or rotating OAuth exchanges on transport/status follow-ups. */
internal fun RequestBody.gitLabSingleUseBody(): RequestBody {
    val delegate = this
    return object : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
    }
}
