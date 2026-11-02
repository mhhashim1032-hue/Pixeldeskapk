#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
OUTPUT_DIR="${2:-build/release-candidate}"
GRADLE_FILE="app/build.gradle.kts"

if [[ ! -s "$APK_PATH" ]]; then
  echo "Debug APK is missing or empty: $APK_PATH" >&2
  exit 1
fi
if [[ ! -f "$GRADLE_FILE" ]]; then
  echo "Cannot find Android application Gradle configuration." >&2
  exit 1
fi

read_gradle_value() {
  local key="$1"
  sed -nE "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*([^[:space:]]+).*/\\1/p" "$GRADLE_FILE" | head -n 1
}

read_gradle_string() {
  local key="$1"
  sed -nE "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\".*/\\1/p" "$GRADLE_FILE" | head -n 1
}

APPLICATION_ID="$(read_gradle_string applicationId)"
VERSION_CODE="$(read_gradle_value versionCode)"
VERSION_NAME="$(read_gradle_string versionName)"
MIN_SDK="$(read_gradle_value minSdk)"
COMMIT="$(git rev-parse HEAD)"
SHORT_COMMIT="${COMMIT:0:12}"

if [[ -z "$APPLICATION_ID" || -z "$VERSION_CODE" || -z "$VERSION_NAME" || -z "$MIN_SDK" ]]; then
  echo "Could not extract application ID/version/minSdk from $GRADLE_FILE." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
cp "$APK_PATH" "$OUTPUT_DIR/app-debug.apk"

if ! unzip -Z1 "$OUTPUT_DIR/app-debug.apk" | grep -qx 'lib/arm64-v8a/libpixeldeskpty.so'; then
  echo "APK is missing the ARM64 native PTY bridge." >&2
  exit 1
fi

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
APKSIGNER=""
if [[ -n "$SDK_ROOT" && -x "$SDK_ROOT/build-tools/35.0.0/apksigner" ]]; then
  APKSIGNER="$SDK_ROOT/build-tools/35.0.0/apksigner"
elif command -v apksigner >/dev/null 2>&1; then
  APKSIGNER="$(command -v apksigner)"
fi
if [[ -z "$APKSIGNER" ]]; then
  echo "Android apksigner was not found; refusing to label an unverified APK installable." >&2
  exit 1
fi
"$APKSIGNER" verify --verbose "$OUTPUT_DIR/app-debug.apk"

APK_SIZE="$(wc -c < "$OUTPUT_DIR/app-debug.apk" | tr -d ' ')"
APK_SHA256="$(sha256sum "$OUTPUT_DIR/app-debug.apk" | awk '{print $1}')"
BUILT_AT="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

cat > "$OUTPUT_DIR/manifest.txt" <<EOF
product=PIXEL DESK
artifact=app-debug.apk
variant=debug
application_id=$APPLICATION_ID
version_code=$VERSION_CODE
version_name=$VERSION_NAME
minimum_android_api=$MIN_SDK
included_native_abi=arm64-v8a
native_pty_bridge=verified-present
apk_signature=verified-with-apksigner
git_commit=$COMMIT
git_commit_short=$SHORT_COMMIT
built_at_utc=$BUILT_AT
apk_size_bytes=$APK_SIZE
apk_sha256=$APK_SHA256
EOF

(
  cd "$OUTPUT_DIR"
  sha256sum app-debug.apk > app-debug.apk.sha256
)
echo "Installable debug candidate prepared:"
cat "$OUTPUT_DIR/manifest.txt"
