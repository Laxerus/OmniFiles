#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors: list[str] = []
trash_path = root / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt"
policy_path = root / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashMetadataPolicy.kt"
test_path = root / "app/src/test/java/dev/laxerus/omnifiles/fs/TrashMetadataPolicyTest.kt"

if not trash_path.is_file():
    errors.append("missing TrashManager.kt")
else:
    text = trash_path.read_text(encoding="utf-8")
    required = (
        "VerifiedTrashCopyRetainedException",
        "writeMetadata(ticket, safeTarget.name, trashedAt)",
        "moveIntoTrash(safeTarget, ticket.trashedFile)",
        "catch (retained: VerifiedTrashCopyRetainedException)",
        "catch (failure: Throwable)",
        "removeMetadata(ticket.trashedFile)",
        "cleanupOrphanMetadata()",
        "TrashMetadataPolicy.shouldDeleteOrphan(",
        "requireTrashSlot(destination, mustExist = false)",
    )
    for token in required:
        if token not in text:
            errors.append(f"TrashManager.kt missing: {token}")

    move_start = text.find("fun moveToTrash(target: File): TrashTicket")
    metadata_write = text.find("writeMetadata(ticket, safeTarget.name, trashedAt)", move_start)
    physical_move = text.find("moveIntoTrash(safeTarget, ticket.trashedFile)", move_start)
    retained_catch = text.find("catch (retained: VerifiedTrashCopyRetainedException)", move_start)
    generic_catch = text.find("catch (failure: Throwable)", move_start)
    if min(move_start, metadata_write, physical_move, retained_catch, generic_catch) < 0 or not (
        move_start < metadata_write < physical_move < retained_catch < generic_catch
    ):
        errors.append("trash transaction must persist metadata before physical move and preserve catch ordering")
    else:
        retained_block = text[retained_catch:generic_catch]
        if "removeMetadata(ticket.trashedFile)" in retained_block:
            errors.append("verified retained trash copy must keep recovery metadata")
        generic_end = text.find("return ticket", generic_catch)
        if generic_end < 0 or "removeMetadata(ticket.trashedFile)" not in text[generic_catch:generic_end]:
            errors.append("failed pre-commit trash move must clean prewritten metadata")

if not policy_path.is_file():
    errors.append("missing TrashMetadataPolicy.kt")
else:
    policy = policy_path.read_text(encoding="utf-8")
    for token in (
        "ORPHAN_GRACE_MS: Long = 10 * 60 * 1000L",
        "if (name in liveMetadataNames) return false",
        "return now - modifiedAt >= ORPHAN_GRACE_MS",
    ):
        if token not in policy:
            errors.append(f"TrashMetadataPolicy.kt missing: {token}")

if not test_path.is_file():
    errors.append("missing TrashMetadataPolicyTest.kt")
else:
    tests = test_path.read_text(encoding="utf-8")
    for name in (
        "keepsLiveMetadataEvenWhenOld",
        "keepsFreshOrphanMetadata",
        "deletesStaleOrphanJson",
        "deletesStaleTempFile",
        "ignoresUnknownMetadataFilesConservatively",
    ):
        if name not in tests:
            errors.append(f"TrashMetadataPolicyTest.kt missing: {name}")

if errors:
    print("OmniFiles trash transaction sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles trash transaction sanity: OK")
