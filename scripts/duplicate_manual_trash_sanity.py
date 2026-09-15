#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
activity_path = root / "app/src/main/java/dev/laxerus/omnifiles/ui/DuplicateFinderActivity.kt"
test_path = root / "app/src/test/java/dev/laxerus/omnifiles/fs/DuplicateFinderTest.kt"
errors: list[str] = []

if not activity_path.is_file():
    errors.append(f"missing required file: {activity_path.relative_to(root)}")
else:
    text = activity_path.read_text(encoding="utf-8")
    required = (
        "showFileActions(group, group.files[which])",
        "confirmMoveToTrash(group, duplicate)",
        "moveToTrash(group, duplicate)",
        "DuplicateFinder.verifyDuplicate(",
        "expectedSha256 = expectedSha256",
        "trashManager.moveToTrash(safe)",
        "private fun showTrashUndo(ticket: TrashTicket)",
        "Snackbar.make(binding.root, R.string.duplicate_finder_trashed, Snackbar.LENGTH_LONG)",
        ".setAction(R.string.undo) { restoreTrash(ticket) }",
        "Snackbar.Callback.DISMISS_EVENT_ACTION",
        "private fun restoreTrash(ticket: TrashTicket)",
        "trashManager.restore(ticket)",
        "lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)",
    )
    for token in required:
        if token not in text:
            errors.append(f"DuplicateFinderActivity.kt missing: {token}")

    verification = "if (!verifyDuplicate(duplicate, group.sha256))"
    if text.count(verification) < 2:
        errors.append("individual duplicate trash must verify group SHA-256 before confirmation and again before moving")

    move_start = text.find("private fun moveToTrash(")
    if move_start < 0:
        errors.append("DuplicateFinderActivity.kt missing moveToTrash")
    else:
        verify_at = text.find(verification, move_start)
        trash_at = text.find("trashManager.moveToTrash(safe)", move_start)
        undo_at = text.find("showTrashUndo(ticket)", move_start)
        if verify_at < 0 or trash_at < 0 or verify_at >= trash_at:
            errors.append("moveToTrash must reverify SHA-256 before TrashManager receives the file")
        if undo_at < trash_at:
            errors.append("trash undo must only be exposed after TrashManager returns a ticket")

    undo_start = text.find("private fun showTrashUndo(ticket: TrashTicket)")
    restore_start = text.find("private fun restoreTrash(ticket: TrashTicket)")
    if undo_start < 0 or restore_start < 0 or undo_start >= restore_start:
        errors.append("duplicate trash undo and restore functions must remain in the manual trash flow")
    else:
        action_guard = text.find("if (event == DISMISS_EVENT_ACTION) return", undo_start, restore_start)
        if action_guard < 0:
            errors.append("Snackbar action dismissal must not trigger a competing rescan")

if not test_path.is_file():
    errors.append(f"missing required file: {test_path.relative_to(root)}")
else:
    tests = test_path.read_text(encoding="utf-8")
    required_tests = (
        "verifyDuplicateAcceptsUnchangedVerifiedFile",
        "verifyDuplicateRejectsContentChangedWithSameSizeAndTimestamp",
        "verifyDuplicateRejectsInvalidDigestFormat",
        "verifyDuplicateRejectsDeletedFile",
        "verifyDuplicateRejectsChangedSize",
        "verifyDuplicateRejectsSymlinkEscapingRoot",
    )
    for test_name in required_tests:
        if test_name not in tests:
            errors.append(f"DuplicateFinderTest.kt missing: {test_name}")

if errors:
    print("OmniFiles individual duplicate trash sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles individual duplicate trash sanity: OK")
