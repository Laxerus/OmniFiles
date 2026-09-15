package dev.laxerus.omnifiles.ui

object NaturalNameComparator {
    fun compare(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0

        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]
            val leftDigit = leftChar in '0'..'9'
            val rightDigit = rightChar in '0'..'9'

            if (leftDigit && rightDigit) {
                val leftStart = leftIndex
                val rightStart = rightIndex
                while (leftIndex < left.length && left[leftIndex] in '0'..'9') leftIndex++
                while (rightIndex < right.length && right[rightIndex] in '0'..'9') rightIndex++

                var leftSignificant = leftStart
                var rightSignificant = rightStart
                while (leftSignificant < leftIndex && left[leftSignificant] == '0') leftSignificant++
                while (rightSignificant < rightIndex && right[rightSignificant] == '0') rightSignificant++

                val leftSignificantLength = leftIndex - leftSignificant
                val rightSignificantLength = rightIndex - rightSignificant
                if (leftSignificantLength != rightSignificantLength) {
                    return leftSignificantLength.compareTo(rightSignificantLength)
                }

                for (offset in 0 until leftSignificantLength) {
                    val result = left[leftSignificant + offset].compareTo(right[rightSignificant + offset])
                    if (result != 0) return result
                }

                val leftRawLength = leftIndex - leftStart
                val rightRawLength = rightIndex - rightStart
                if (leftRawLength != rightRawLength) return leftRawLength.compareTo(rightRawLength)
                continue
            }

            if (leftDigit != rightDigit) {
                val result = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                if (result != 0) return result
                leftIndex++
                rightIndex++
                continue
            }

            val result = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
            if (result != 0) return result
            leftIndex++
            rightIndex++
        }

        val lengthResult = (left.length - leftIndex).compareTo(right.length - rightIndex)
        if (lengthResult != 0) return lengthResult

        return left.compareTo(right)
    }
}
