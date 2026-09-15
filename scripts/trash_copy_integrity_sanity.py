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
    "MessageDigest.getInstance(\"SHA-256\")",
    "private fun matchingDirectories",
    "val sourceDigestBefore = treeDigest(source)",
    "val destinationDigestBefore = treeDigest(destination)",
    "val sourceDigestAfter = treeDigest(source)",
    "val destinationDigestAfter = treeDigest(destination)",
    "absolute.path != canonical.path",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt",
    "!CopyIntegrityVerifier.matches(source, destination)",
    "Klasör güvenli biçimde kopyalanıp doğrulanamadı; kaynak korunuyor",
    "Dosya içerik doğrulamasından geçmedi; kaynak korunuyor",
    "Klasör eski konumuna içerik doğrulamasıyla geri yüklenemedi; çöp kopyası korundu",
    "Dosya geri yükleme içerik doğrulamasından geçmedi; çöp kopyası korundu",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/CopyIntegrityVerifierTest.kt",
    "acceptsIdenticalFiles",
    "rejectsSameSizeDifferentFileContent",
    "acceptsIdenticalDirectoryTrees",
    "rejectsDirectoryTreeWithSameSizeMutation",
    "rejectsSymlinkedTreeEntry",
)

trash_text = (root / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt").read_text(encoding="utf-8")
if trash_text.count("!CopyIntegrityVerifier.matches(source, destination)") < 4:
    errors.append("TrashManager must content-verify file and directory fallback copies for both trash and restore")

if errors:
    print("OmniFiles trash copy integrity sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles trash copy integrity sanity: OK")
