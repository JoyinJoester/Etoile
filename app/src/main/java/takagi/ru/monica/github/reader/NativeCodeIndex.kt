package takagi.ru.monica.github.reader

import androidx.annotation.Keep

/** Native offsets reference the original string; only visible lines become substrings. */
@Keep
object NativeCodeIndex {
    init { System.loadLibrary("etoile_code_reader") }

    @JvmStatic external fun lineRanges(text: CharArray): IntArray
}
