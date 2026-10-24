package takagi.ru.monica.github.feature.store

/** Bounded gesture accumulation permits an immediate reversal even after a long edge drag. */
internal fun storeReaderDrag(distance: Float, delta: Float, threshold: Float): Float =
    (distance + delta).coerceIn(0f, threshold * 1.5f)

/** At the action threshold only 16dp is revealed; even a long pull stays below 24dp. */
internal fun storeReaderDisplacement(distance: Float, threshold: Float): Float =
    if (threshold <= 0f) 0f else threshold / 3f * distance / (threshold + distance)
