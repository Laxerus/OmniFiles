#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
activity_path = root / "app/src/main/java/dev/laxerus/omnifiles/ui/DuplicateFinderActivity.kt"
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
        if verify_at < 0 or trash_at < 0 or verify_at >= trash_at:
            errors.append("moveToTrash must reverify SHA-256 before TrashManager receives the file")

if errors:
    print("OmniFiles individual duplicate trash sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles individual duplicate trash sanity: OK")
