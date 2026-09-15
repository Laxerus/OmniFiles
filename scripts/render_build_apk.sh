#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CACHE_ROOT="${XDG_CACHE_HOME:-$ROOT/.render-cache}/omnifiles"
TOOLS="$CACHE_ROOT/tools"
SDK="$CACHE_ROOT/android-sdk"
GRADLE_USER_HOME="$CACHE_ROOT/gradle-user-home"
PUBLIC="$ROOT/public"
GRADLE_VERSION="9.3.1"
ANDROID_PLATFORM_PACKAGE="37.0"
BUILD_TOOLS="36.0.0"
CMDLINE_TOOLS_REV="15859902"

export GRADLE_USER_HOME
mkdir -p "$TOOLS" "$SDK" "$GRADLE_USER_HOME" "$PUBLIC"
rm -rf "$PUBLIC"/*

log() { printf '\n==> %s\n' "$*"; }

fetch() {
  local url="$1" out="$2"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 4 --retry-delay 2 --connect-timeout 20 "$url" -o "$out"
  elif command -v wget >/dev/null 2>&1; then
    wget --tries=4 --timeout=20 -O "$out" "$url"
  else
    echo "curl veya wget bulunamadı" >&2
    exit 1
  fi
}

command -v unzip >/dev/null 2>&1 || { echo "unzip bulunamadı" >&2; exit 1; }

JAVA_MAJOR=""
if command -v java >/dev/null 2>&1; then
  JAVA_MAJOR="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
fi
if [[ "$JAVA_MAJOR" != "17" ]]; then
  JDK_DIR="$TOOLS/jdk17"
  if [[ ! -x "$JDK_DIR/bin/java" ]]; then
    log "Temurin JDK 17 indiriliyor"
    rm -rf "$JDK_DIR" "$TOOLS/jdk17.tar.gz" "$TOOLS/jdk-extract"
    mkdir -p "$TOOLS/jdk-extract"
    fetch "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" "$TOOLS/jdk17.tar.gz"
    tar -xzf "$TOOLS/jdk17.tar.gz" -C "$TOOLS/jdk-extract"
    EXTRACTED_JDK="$(find "$TOOLS/jdk-extract" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
    test -n "$EXTRACTED_JDK"
    mv "$EXTRACTED_JDK" "$JDK_DIR"
    rm -rf "$TOOLS/jdk-extract" "$TOOLS/jdk17.tar.gz"
  else
    log "Temurin JDK 17 build cache'ten kullanılıyor"
  fi
  export JAVA_HOME="$JDK_DIR"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

log "Java"
java -version

GRADLE_HOME="$TOOLS/gradle-$GRADLE_VERSION"
if [[ ! -x "$GRADLE_HOME/bin/gradle" ]]; then
  log "Gradle $GRADLE_VERSION indiriliyor"
  fetch "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" "$TOOLS/gradle.zip"
  unzip -q "$TOOLS/gradle.zip" -d "$TOOLS"
  rm -f "$TOOLS/gradle.zip"
else
  log "Gradle $GRADLE_VERSION build cache'ten kullanılıyor"
fi
export PATH="$GRADLE_HOME/bin:$PATH"

export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
CMDLINE_LATEST="$SDK/cmdline-tools/latest"
CMDLINE_MARKER="$CMDLINE_LATEST/.omnifiles-revision"
INSTALLED_CMDLINE_REV="$(cat "$CMDLINE_MARKER" 2>/dev/null || true)"
if [[ ! -x "$CMDLINE_LATEST/bin/sdkmanager" || "$INSTALLED_CMDLINE_REV" != "$CMDLINE_TOOLS_REV" ]]; then
  log "Android command-line tools $CMDLINE_TOOLS_REV indiriliyor"
  rm -rf "$SDK/cmdline-tools" "$TOOLS/android-tools.zip"
  mkdir -p "$SDK/cmdline-tools"
  fetch "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_REV}_latest.zip" "$TOOLS/android-tools.zip"
  unzip -q "$TOOLS/android-tools.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$CMDLINE_LATEST"
  printf '%s\n' "$CMDLINE_TOOLS_REV" > "$CMDLINE_MARKER"
  rm -f "$TOOLS/android-tools.zip"
else
  log "Android command-line tools $CMDLINE_TOOLS_REV build cache'ten kullanılıyor"
fi
export PATH="$CMDLINE_LATEST/bin:$SDK/platform-tools:$PATH"

log "Android SDK lisansları kabul ediliyor"
yes | sdkmanager --licenses >/dev/null 2>&1 || true

if [[ ! -f "$SDK/platforms/android-$ANDROID_PLATFORM_PACKAGE/android.jar" || ! -x "$SDK/build-tools/$BUILD_TOOLS/aapt2" || ! -x "$SDK/platform-tools/adb" ]]; then
  log "Android SDK Platform $ANDROID_PLATFORM_PACKAGE kuruluyor"
  sdkmanager \
    "platform-tools" \
    "platforms;android-${ANDROID_PLATFORM_PACKAGE}" \
    "build-tools;${BUILD_TOOLS}"
else
  log "Android SDK Platform $ANDROID_PLATFORM_PACKAGE build cache'ten kullanılıyor"
fi

log "Kaynak sanity kontrolü"
python3 scripts/build_contract_sanity.py
python3 scripts/source_sanity.py
python3 scripts/file_manager_shell_sanity.py
python3 scripts/feature_regression_sanity.py
python3 scripts/duplicate_manual_trash_sanity.py
python3 scripts/quick_access_navigation_sanity.py

log "Unit test + Android Lint + Debug APK"
gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n 1)"
if [[ -z "$APK" || ! -s "$APK" ]]; then
  echo "APK üretilemedi" >&2
  exit 1
fi

OUT_NAME="OmniFiles-0.8.0-dev-debug.apk"
cp "$APK" "$PUBLIC/$OUT_NAME"
sha256sum "$PUBLIC/$OUT_NAME" > "$PUBLIC/$OUT_NAME.sha256"

COMMIT="${RENDER_GIT_COMMIT:-unknown}"
SIZE="$(du -h "$PUBLIC/$OUT_NAME" | awk '{print $1}')"
cat > "$PUBLIC/index.html" <<HTML
<!doctype html>
<html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>OmniFiles APK</title><style>body{font-family:system-ui,sans-serif;max-width:720px;margin:48px auto;padding:0 20px;line-height:1.55;background:#111;color:#eee}.card{padding:24px;border:1px solid #333;border-radius:18px;background:#181818}a.button{display:inline-block;margin-top:14px;padding:13px 18px;border-radius:12px;background:#eee;color:#111;text-decoration:none;font-weight:700}code{word-break:break-all;color:#bbb}</style></head><body><div class="card"><h1>OmniFiles</h1><p>Sürüm: <strong>0.8.0-dev-debug</strong> · Boyut: <strong>${SIZE}</strong></p><p>Kaynak commit: <code>${COMMIT}</code></p><a class="button" href="/$OUT_NAME">APK'yı indir</a><p><a href="/$OUT_NAME.sha256">SHA-256</a></p></div></body></html>
HTML

log "Hazır: $PUBLIC/$OUT_NAME"
sha256sum "$PUBLIC/$OUT_NAME"
