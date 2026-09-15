#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/dev/laxerus/omnifiles/ui/RecentFoldersButton.kt"
errors: list[str] = []

if not path.is_file():
    errors.append(f"missing required file: {path.relative_to(root)}")
else:
    text = path.read_text(encoding="utf-8")
    required = (
        "FilePathPolicy.requireDirectEntry(folder, sharedRoot)",
        "it.exists() && it.isDirectory && it.canRead()",
        "val browserHost = findActivity(context) as? FileBrowserActivity",
        "context.startActivity(intent)",
        "recentFolderStore.record(safeFolder, sharedRoot)",
        "browserHost?.finish()",
        "private fun findActivity(start: Context): Activity?",
        "ContextWrapper",
    )
    for token in required:
        if token not in text:
            errors.append(f"RecentFoldersButton.kt missing: {token}")

    section = text.find("private fun openFolder(folder: File, sharedRoot: File)")
    start = text.find("context.startActivity(intent)", section)
    record = text.find("recentFolderStore.record(safeFolder, sharedRoot)", section)
    finish = text.find("browserHost?.finish()", section)
    failure = text.find(".onFailure", section)
    if section < 0 or start < 0 or record < 0 or finish < 0:
        errors.append("quick access folder navigation section is incomplete")
    elif not (start < record < finish):
        errors.append("quick access must start target, record history, then finish old browser")
    if failure >= 0 and finish >= failure:
        errors.append("old browser must not be finished from the launch failure branch")

if errors:
    print("OmniFiles quick access navigation sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles quick access navigation sanity: OK")
