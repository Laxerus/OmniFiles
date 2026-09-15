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
            errors.append(f"{path} missing build contract token: {token}")


require(
    "app/build.gradle",
    "compileSdk 37",
    "targetSdk 36",
    "versionCode 800",
    "versionName '0.8.0-dev'",
    "applicationIdSuffix '.debug'",
    "versionNameSuffix '-debug'",
)
require(
    "build.gradle",
    "com.android.application' version '9.1.1'",
    "kotlin-gradle-plugin:2.4.0",
)
require(
    "scripts/render_build_apk.sh",
    'GRADLE_VERSION="9.3.1"',
    'ANDROID_PLATFORM_PACKAGE="37.0"',
    'BUILD_TOOLS="36.0.0"',
    'OUT_NAME="OmniFiles-0.8.0-dev-debug.apk"',
    '"platforms;android-${ANDROID_PLATFORM_PACKAGE}"',
)
require(
    ".github/workflows/build-apk.yml",
    'runs-on: ubuntu-24.04',
    'platforms;android-37.0',
    'build-tools;36.0.0',
    'gradle-version: "9.3.1"',
)

if errors:
    print("OmniFiles build contract sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles build contract sanity: OK")
