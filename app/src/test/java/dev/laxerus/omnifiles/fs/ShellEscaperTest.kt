package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellEscaperTest {
    @Test fun quotesSimpleText() {
        assertEquals("'hello world'", ShellEscaper.quote("hello world"))
    }

    @Test fun escapesSingleQuote() {
        assertEquals("'a'\"'\"'b'", ShellEscaper.quote("a'b"))
    }
}
