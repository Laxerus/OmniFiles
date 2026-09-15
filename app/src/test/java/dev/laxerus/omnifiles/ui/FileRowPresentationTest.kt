package dev.laxerus.omnifiles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileRowPresentationTest {
    @Test
    fun `directory ignores file extension`() {
        val result = FileRowPresenter.describe("photos.zip", isDirectory = true)

        assertEquals(FileVisualKind.DIRECTORY, result.kind)
        assertNull(result.extensionLabel)
    }

    @Test
    fun `recognizes common office and android packages`() {
        assertEquals(FileVisualKind.DOCUMENT, FileRowPresenter.classify("rapor.DOCX", false))
        assertEquals(FileVisualKind.SPREADSHEET, FileRowPresenter.classify("butce.xlsx", false))
        assertEquals(FileVisualKind.PRESENTATION, FileRowPresenter.classify("sunum.pptx", false))
        assertEquals(FileVisualKind.ANDROID_PACKAGE, FileRowPresenter.classify("uygulama.apkm", false))
    }

    @Test
    fun `recognizes modern media and developer formats`() {
        assertEquals(FileVisualKind.IMAGE, FileRowPresenter.classify("foto.avif", false))
        assertEquals(FileVisualKind.AUDIO, FileRowPresenter.classify("ses.opus", false))
        assertEquals(FileVisualKind.CODE, FileRowPresenter.classify("Screen.tsx", false))
        assertEquals(FileVisualKind.FONT, FileRowPresenter.classify("Inter.woff2", false))
    }

    @Test
    fun `hidden marker without extension is not treated as extension`() {
        val result = FileRowPresenter.describe(".nomedia", isDirectory = false)

        assertEquals(FileVisualKind.OTHER, result.kind)
        assertNull(result.extensionLabel)
    }

    @Test
    fun `extension label is normalized and bounded`() {
        assertEquals("PDF", FileRowPresenter.extensionLabel("guide.PdF", false))
        assertNull(FileRowPresenter.extensionLabel("no_extension", false))
        assertNull(FileRowPresenter.extensionLabel("file.thisextensionistoolong", false))
    }
}
