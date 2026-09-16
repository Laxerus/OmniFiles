#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors: list[str] = []


def require(path: str, *tokens: str) -> None:
    file = root / path
    if not file.is_file():
        errors.append(f"missing required file: {path}")
        return
    text = file.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            errors.append(f"{path} missing: {token}")


require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/CopyIntegrityVerifier.kt",
    "object CopyIntegrityVerifier",
    "fun matches(source: File, destination: File): Boolean",
    "private fun directCanonical(file: File): File?",
    "if (absolute.path != canonical.path) null else canonical",
    "private fun stableSha256(file: File): ByteArray?",
    "val sourceDigestBefore = stableSha256(source)",
    "val destinationDigestBefore = stableSha256(destination)",
    "val sourceDigestAfter = stableSha256(source)",
    "val destinationDigestAfter = stableSha256(destination)",
    "private fun matchingDirectories",
    "val sourceDigestBefore = treeDigest(source)",
    "val destinationDigestBefore = treeDigest(destination)",
    "val sourceDigestAfter = treeDigest(source)",
    "val destinationDigestAfter = treeDigest(destination)",
    "val canonicalRoot = directCanonical(root)",
    "val canonical = directCanonical(entry.file)",
    "MessageDigest.getInstance(\"SHA-256\")",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/DurableFileWriter.kt",
    "object DurableFileWriter",
    "fun writeNewUtf8(temp: File, destination: File, content: String)",
    "output.fd.sync()",
    "check(temp.readBytes().contentEquals(bytes))",
    "check(temp.renameTo(destination))",
    "val persistedMatches = runCatching",
    "destination.delete()",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt",
    "!CopyIntegrityVerifier.matches(source, destination)",
    "TrashMetadataCodec.encode(",
    "DurableFileWriter.writeNewUtf8(temp, destination, encoded)",
    "TrashMetadataCodec.decode(destination.readText(Charsets.UTF_8))",
    "Çöp metadata kaydı bütünlük doğrulamasından geçmedi",
    "Çöp metadata kaydı alan doğrulamasından geçmedi",
    "Klasör güvenli biçimde kopyalanıp doğrulanamadı; kaynak korunuyor",
    "Dosya içerik doğrulamasından geçmedi; kaynak korunuyor",
    "Klasör eski konumuna içerik doğrulamasıyla geri yüklenemedi; çöp kopyası korundu",
    "Dosya geri yükleme içerik doğrulamasından geçmedi; çöp kopyası korundu",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/TrashMetadataCodec.kt",
    "object TrashMetadataCodec",
    "KEY_INTEGRITY_SHA256",
    "MessageDigest.getInstance(\"SHA-256\")",
    "MessageDigest.isEqual(",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/CopyIntegrityVerifierTest.kt",
    "acceptsIdenticalFiles",
    "rejectsSameSizeDifferentFileContent",
    "acceptsIdenticalDirectoryTrees",
    "rejectsDirectoryTreeWithSameSizeMutation",
    "rejectsSymlinkedTreeEntry",
    "rejectsSymlinkedRootFile",
    "rejectsSymlinkedRootDirectory",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/DurableFileWriterTest.kt",
    "writesVerifiedUtf8AndRemovesTemp",
    "refusesToOverwriteExistingDestination",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/TrashMetadataCodecTest.kt",
    "roundTripsSealedRecord",
    "rejectsModifiedOriginalPath",
    "acceptsLegacyUnsealedRecordForCompatibility",
)

verifier_text = (root / "app/src/main/java/dev/laxerus/omnifiles/fs/CopyIntegrityVerifier.kt").read_text(encoding="utf-8")
if verifier_text.count("stableSha256(") < 6:
    errors.append("CopyIntegrityVerifier must use stable SHA-256 in both file and tree verification paths")

trash_text = (root / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt").read_text(encoding="utf-8")
if trash_text.count("!CopyIntegrityVerifier.matches(source, destination)") < 4:
    errors.append("TrashManager must content-verify file and directory fallback copies for both trash and restore")

if errors:
    print("OmniFiles trash copy integrity sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles trash copy integrity sanity: OK")
