package takagi.ru.monica.github.feature.store

import org.junit.Assert.*
import org.junit.Test

class StoreReaderMotionTest {
    @Test fun longDragIsBoundedAndReversalImmediatelyReleasesIt() {
        val distance = storeReaderDrag(0f, 100000f, 96f)
        assertEquals(144f, distance, 0f)
        assertEquals(143f, storeReaderDrag(distance, -1f, 96f), 0f)
        assertEquals(0f, storeReaderDrag(distance, -100000f, 96f), 0f)
    }

    @Test fun displacementHasProgressiveResistanceAndScalesWithDensity() {
        assertEquals(0f, storeReaderDisplacement(0f, 96f), 0f)
        assertEquals(16f, storeReaderDisplacement(96f, 96f), 0.001f)
        val increments = (0..3).map { storeReaderDisplacement(it * 48f, 96f) }
        assertTrue(increments[1] - increments[0] > increments[2] - increments[1])
        assertTrue(increments[2] - increments[1] > increments[3] - increments[2])
        assertTrue(increments.last() < 24f)
        assertEquals(increments.last() * 3f, storeReaderDisplacement(432f, 288f), 0.001f)
        assertEquals(0f, storeReaderDisplacement(0f, 0f), 0f)
    }
}
