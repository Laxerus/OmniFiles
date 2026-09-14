package dev.laxerus.omnifiles.sqlite

import org.junit.Assert.assertEquals
import org.junit.Test

class SqliteWorkspaceTest {
    @Test fun quotesIdentifiers() {
        assertEquals("\"users\"", SqliteWorkspace.quoteIdentifier("users"))
        assertEquals("\"odd\"\"name\"", SqliteWorkspace.quoteIdentifier("odd\"name"))
    }
}
