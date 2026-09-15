#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors: list[str] = []
trash_path = root / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt"
metadata_policy_path = root / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashMetadataPolicy.kt"
recovery_policy_path = root / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashRecoveryPolicy.kt"
restore_policy_path = root / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashRestorePolicy.kt"
trash_activity_path = root / "app/src/main/java/dev/laxerus/omnifiles/ui/TrashActivity.kt"
trash_adapter_path = root / "app/src/main/java/dev/laxerus/omnifiles/ui/TrashListAdapter.kt"
trash_strings_path = root / "app/src/main/res/values/strings_trash.xml"
metadata_test_path = root / "app/src/test/java/dev/laxerus/omnifiles/fs/TrashMetadataPolicyTest.kt"
recovery_test_path = root / "app/src/test/java/dev/laxerus/omnifiles/fs/TrashRecoveryPolicyTest.kt"
restore_test_path = root / "app/src/test/java/dev/laxerus/omnifiles/fs/TrashRestorePolicyTest.kt"

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
        "recoverInterruptedTransactions()",
        "cleanupStaleTempMetadata()",
        "TrashRecoveryPolicy.decide(",
        "TrashRecoveryAction.DELETE_METADATA",
        "TrashMetadataPolicy.shouldDeleteStaleTemp(",
        "restoreAvailability: TrashRestoreAvailability",
        "TrashRestorePolicy.availability(originalFile)",
        "entry.restoreAvailability == TrashRestoreAvailability.AVAILABLE",
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

    list_start = text.find("fun listEntries(): List<TrashEntry>")
    recover_call = text.find("recoverInterruptedTransactions()", list_start)
    temp_cleanup_call = text.find("cleanupStaleTempMetadata()", list_start)
    listing = text.find("return trashRoot.listFiles()", list_start)
    if min(list_start, recover_call, temp_cleanup_call, listing) < 0 or not (
        list_start < recover_call < temp_cleanup_call < listing
    ):
        errors.append("trash listing must recover interrupted transactions before temp cleanup and listing")

if not metadata_policy_path.is_file():
    errors.append("missing TrashMetadataPolicy.kt")
else:
    policy = metadata_policy_path.read_text(encoding="utf-8")
    for token in (
        "ORPHAN_GRACE_MS: Long = 10 * 60 * 1000L",
        "fun isPastGrace(",
        "fun shouldDeleteStaleTemp(",
        "if (!name.endsWith(\".tmp\")) return false",
    ):
        if token not in policy:
            errors.append(f"TrashMetadataPolicy.kt missing: {token}")
    if "endsWith(\".json\")" in policy:
        errors.append("TrashMetadataPolicy must not blindly delete stale JSON recovery metadata")

if not recovery_policy_path.is_file():
    errors.append("missing TrashRecoveryPolicy.kt")
else:
    policy = recovery_policy_path.read_text(encoding="utf-8")
    for token in (
        "enum class TrashRecoveryAction",
        "KEEP",
        "DELETE_METADATA",
        "if (!TrashMetadataPolicy.isPastGrace(metadataModifiedAt, now)) return TrashRecoveryAction.KEEP",
        "if (trashExists) return TrashRecoveryAction.KEEP",
        "if (!originalExists) return TrashRecoveryAction.KEEP",
        "return TrashRecoveryAction.DELETE_METADATA",
    ):
        if token not in policy:
            errors.append(f"TrashRecoveryPolicy.kt missing: {token}")

if not restore_policy_path.is_file():
    errors.append("missing TrashRestorePolicy.kt")
else:
    policy = restore_policy_path.read_text(encoding="utf-8")
    for token in (
        "enum class TrashRestoreAvailability",
        "AVAILABLE",
        "ORIGINAL_UNKNOWN",
        "DESTINATION_OCCUPIED",
        "originalFile == null -> TrashRestoreAvailability.ORIGINAL_UNKNOWN",
        "originalFile.exists() -> TrashRestoreAvailability.DESTINATION_OCCUPIED",
    ):
        if token not in policy:
            errors.append(f"TrashRestorePolicy.kt missing: {token}")

if not trash_activity_path.is_file():
    errors.append("missing TrashActivity.kt")
else:
    ui = trash_activity_path.read_text(encoding="utf-8")
    for token in (
        "entry.restoreAvailability == TrashRestoreAvailability.AVAILABLE",
        "TrashRestoreAvailability.DESTINATION_OCCUPIED -> getString(R.string.trash_restore_conflict)",
        "entry.restoreAvailability != TrashRestoreAvailability.AVAILABLE",
    ):
        if token not in ui:
            errors.append(f"TrashActivity.kt missing: {token}")

if not trash_adapter_path.is_file():
    errors.append("missing TrashListAdapter.kt")
else:
    ui = trash_adapter_path.read_text(encoding="utf-8")
    for token in (
        "TrashRestoreAvailability.DESTINATION_OCCUPIED",
        "R.string.trash_restore_conflict_short",
        "oldItem.restoreAvailability == newItem.restoreAvailability",
    ):
        if token not in ui:
            errors.append(f"TrashListAdapter.kt missing: {token}")

if not trash_strings_path.is_file():
    errors.append("missing strings_trash.xml")
else:
    strings = trash_strings_path.read_text(encoding="utf-8")
    for token in ("trash_restore_conflict", "trash_restore_conflict_short"):
        if token not in strings:
            errors.append(f"strings_trash.xml missing: {token}")

if not metadata_test_path.is_file():
    errors.append("missing TrashMetadataPolicyTest.kt")
else:
    tests = metadata_test_path.read_text(encoding="utf-8")
    for name in (
        "freshMetadataIsInsideGrace",
        "staleMetadataIsPastGrace",
        "futureTimestampIsKeptConservatively",
        "deletesOnlyStaleTempFiles",
        "ignoresUnknownFilesConservatively",
    ):
        if name not in tests:
            errors.append(f"TrashMetadataPolicyTest.kt missing: {name}")

if not recovery_test_path.is_file():
    errors.append("missing TrashRecoveryPolicyTest.kt")
else:
    tests = recovery_test_path.read_text(encoding="utf-8")
    for name in (
        "keepsFreshPendingTransaction",
        "deletesStaleMetadataWhenMoveNeverCommitted",
        "keepsMetadataWhenTrashEntryExists",
        "keepsMetadataWhenOriginalIsMissing",
    ):
        if name not in tests:
            errors.append(f"TrashRecoveryPolicyTest.kt missing: {name}")

if not restore_test_path.is_file():
    errors.append("missing TrashRestorePolicyTest.kt")
else:
    tests = restore_test_path.read_text(encoding="utf-8")
    for name in (
        "unknownOriginalIsUnavailable",
        "missingDestinationIsAvailable",
        "existingDestinationBlocksRestore",
    ):
        if name not in tests:
            errors.append(f"TrashRestorePolicyTest.kt missing: {name}")

if errors:
    print("OmniFiles trash transaction sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles trash transaction sanity: OK")
