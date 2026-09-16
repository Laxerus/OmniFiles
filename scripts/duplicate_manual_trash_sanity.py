#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
activity_path = root / "app/src/main/java/dev/laxerus/omnifiles/ui/DuplicateFinderActivity.kt"
test_path = root / "app/src/test/java/dev/laxerus/omnifiles/fs/DuplicateFinderTest.kt"
strings_path = root / "app/src/main/res/values/strings_duplicate_finder.xml"
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
        "private var manualTrashUndoPending = false",
        "if (manualTrashUndoPending || scanJob?.isActive == true) return",
        "private fun showTrashUndo(ticket: TrashTicket)",
        "manualTrashUndoPending = true",
        "Snackbar.make(binding.root, R.string.duplicate_finder_trashed, Snackbar.LENGTH_LONG)",
        ".setAction(R.string.undo) { restoreTrash(ticket) }",
        "Snackbar.Callback.DISMISS_EVENT_ACTION",
        "private fun restoreTrash(ticket: TrashTicket)",
        "trashManager.restore(ticket)",
        "private fun refreshAfterManualTrash()",
        "lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)",
        "val tickets: List<TrashTicket>",
        "val tickets = mutableListOf<TrashTicket>()",
        ".onSuccess { tickets += it }",
        "CleanupOutcome(tickets.toList(), failed, keeperInvalidated)",
        "private fun showGroupTrashUndo(outcome: CleanupOutcome)",
        ".setAction(R.string.undo) { restoreTrashTickets(outcome.tickets) }",
        "private fun restoreTrashTickets(tickets: List<TrashTicket>)",
        "private enum class ManualTrashCheck",
        "CONTENT_CHANGED",
        "NO_VERIFIED_PEER",
        "private fun verifyManualTrashState(",
        ".filterNot { peer -> peer.path == duplicate.path }",
        "resolveFileSilently(peer) != null && verifyDuplicate(peer, group.sha256)",
        "if (!hasVerifiedPeer) return ManualTrashCheck.NO_VERIFIED_PEER",
        "private fun showManualTrashCheckFailure(check: ManualTrashCheck)",
        "R.string.duplicate_finder_entry_content_changed",
        "R.string.duplicate_finder_no_verified_peer",
    )
    for token in required:
        if token not in text:
            errors.append(f"DuplicateFinderActivity.kt missing: {token}")

    verify_state = "verifyManualTrashState(group, duplicate)"
    if text.count(verify_state) < 2:
        errors.append("individual duplicate trash must evaluate selected SHA and verified peer before confirmation and again before moving")

    confirm_start = text.find("private fun confirmMoveToTrash(")
    move_start = text.find("private fun moveToTrash(")
    guard_start = text.find("private fun verifyManualTrashState(")
    if confirm_start < 0 or move_start < 0 or guard_start < 0 or not (confirm_start < move_start < guard_start):
        errors.append("manual duplicate trash confirmation, move, and guard functions must remain ordered")
    else:
        confirm_io = text.find("withContext(Dispatchers.IO)", confirm_start, move_start)
        confirm_check = text.find(verify_state, confirm_start, move_start)
        if confirm_io < 0 or confirm_check < 0 or confirm_io >= confirm_check:
            errors.append("manual duplicate pre-confirm SHA/peer verification must run on Dispatchers.IO")

        move_io = text.find("withContext(Dispatchers.IO)", move_start, guard_start)
        move_check = text.find(verify_state, move_start, guard_start)
        trash_at = text.find("trashManager.moveToTrash(safe)", move_start, guard_start)
        undo_at = text.find("showTrashUndo(outcome.ticket)", move_start, guard_start)
        if move_io < 0 or move_check < 0 or move_io >= move_check:
            errors.append("manual duplicate pre-trash SHA/peer verification must run on Dispatchers.IO")
        if move_check < 0 or trash_at < 0 or move_check >= trash_at:
            errors.append("moveToTrash must reverify selected SHA and a peer immediately before TrashManager receives the file")
        if undo_at < trash_at:
            errors.append("trash undo must only be exposed after TrashManager returns a ticket")

        selected_first = text.find("if (!verifyDuplicate(duplicate, group.sha256)) return ManualTrashCheck.CONTENT_CHANGED", guard_start)
        peer_check = text.find("val hasVerifiedPeer = group.files.asSequence()", guard_start)
        selected_second = text.find(
            "if (!verifyDuplicate(duplicate, group.sha256)) return ManualTrashCheck.CONTENT_CHANGED",
            selected_first + 1 if selected_first >= 0 else guard_start,
        )
        if selected_first < 0 or peer_check < 0 or selected_second < 0 or not (selected_first < peer_check < selected_second):
            errors.append("manual trash guard must verify selected content, then a surviving peer, then selected content again")

    clean_confirm_start = text.find("private fun confirmCleanGroup(")
    clean_start = text.find("private fun cleanGroup(")
    if clean_confirm_start < 0 or clean_start < 0:
        errors.append("group cleanup functions missing")
    else:
        keeper_io = text.find("withContext(Dispatchers.IO)", clean_confirm_start, clean_start)
        keeper_verify = text.find("verifyDuplicate(keeper, group.sha256)", clean_confirm_start, clean_start)
        if keeper_io < 0 or keeper_verify < 0 or keeper_io >= keeper_verify:
            errors.append("group keeper full SHA verification must run on Dispatchers.IO")

    undo_start = text.find("private fun showTrashUndo(ticket: TrashTicket)")
    restore_start = text.find("private fun restoreTrash(ticket: TrashTicket)")
    restore_many_start = text.find("private fun restoreTrashTickets(tickets: List<TrashTicket>)")
    refresh_start = text.find("private fun refreshAfterManualTrash()")
    if (
        undo_start < 0
        or restore_start < 0
        or restore_many_start < 0
        or refresh_start < 0
        or not (undo_start < restore_start < restore_many_start < refresh_start)
    ):
        errors.append("duplicate trash undo, restore, batch restore, and refresh functions must remain ordered")
    else:
        action_guard = text.find(
            "if (event == Snackbar.Callback.DISMISS_EVENT_ACTION) return",
            undo_start,
            restore_start,
        )
        if action_guard < 0:
            errors.append("Snackbar action dismissal must not trigger a competing rescan")
        clear_before_refresh = text.find("manualTrashUndoPending = false", undo_start, restore_start)
        refresh_call = text.find("refreshAfterManualTrash()", undo_start, restore_start)
        if clear_before_refresh < 0 or refresh_call < 0 or clear_before_refresh >= refresh_call:
            errors.append("normal Snackbar dismissal must clear pending undo state before refreshing")

    group_start = text.find("private fun cleanGroup(")
    group_undo_start = text.find("private fun showGroupTrashUndo(outcome: CleanupOutcome)")
    file_actions_start = text.find("private fun showFileActions(")
    if group_start < 0 or group_undo_start < 0 or file_actions_start < 0 or not (group_start < group_undo_start < file_actions_start):
        errors.append("group cleanup must flow into its undo handler before file action handlers")
    else:
        ticket_branch = text.find("outcome.tickets.isNotEmpty() -> showGroupTrashUndo(outcome)", group_start, group_undo_start)
        if ticket_branch < 0:
            errors.append("successful or partial group cleanup must expose undo when tickets exist")
        group_action_guard = text.find(
            "if (event == Snackbar.Callback.DISMISS_EVENT_ACTION) return",
            group_undo_start,
            file_actions_start,
        )
        if group_action_guard < 0:
            errors.append("group cleanup undo action must suppress competing rescan")

if not strings_path.is_file():
    errors.append(f"missing required file: {strings_path.relative_to(root)}")
else:
    strings = strings_path.read_text(encoding="utf-8")
    for token in (
        'name="duplicate_finder_entry_content_changed"',
        'name="duplicate_finder_no_verified_peer"',
    ):
        if token not in strings:
            errors.append(f"strings_duplicate_finder.xml missing: {token}")

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
    print("OmniFiles duplicate trash sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles duplicate trash sanity: OK")
