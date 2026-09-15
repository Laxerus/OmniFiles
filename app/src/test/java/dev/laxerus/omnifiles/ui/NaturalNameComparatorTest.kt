package dev.laxerus.omnifiles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalNameComparatorTest {
    @Test
    fun sortsNumericChunksByValueInsteadOfText() {
        val names = listOf("file10.txt", "file2.txt", "file1.txt")
            .sortedWith(Comparator(NaturalNameComparator::compare))

        assertEquals(listOf("file1.txt", "file2.txt", "file10.txt"), names)
    }

    @Test
    fun prefersShorterEquivalentNumericChunkWhenLeadingZerosDiffer() {
        assertTrue(NaturalNameComparator.compare("file2", "file02") < 0)
        assertTrue(NaturalNameComparator.compare("file02", "file002") < 0)
    }

    @Test
    fun handlesHugeNumericChunksWithoutIntegerParsing() {
        val smaller = "backup99999999999999999999999999999999999999.zip"
        val larger = "backup100000000000000000000000000000000000000.zip"

        assertTrue(NaturalNameComparator.compare(smaller, larger) < 0)
    }

    @Test
    fun comparisonIsCaseInsensitiveBeforeStableCaseFallback() {
        assertTrue(NaturalNameComparator.compare("Alpha2", "alpha10") < 0)
        assertTrue(NaturalNameComparator.compare("Alpha", "alpha") < 0)
    }
}
