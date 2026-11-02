#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_REPO="https://github.com/berilevi/proot-static.git"
SOURCE_COMMIT="6f8ebfd8e24887dfba64c3f2d7d5fe9dc059b60e"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK="${ANDROID_NDK_HOME:-$SDK_ROOT/ndk/28.2.13676358}"
SOURCE_DIR="$ROOT_DIR/native/proot-src"
OUT_DIR="$ROOT_DIR/native/proot-build"
ARTIFACT_DIR="$ROOT_DIR/native/proot-artifacts"
JNI_DIR="$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
ASSET_DIR="$ROOT_DIR/app/src/main/assets"

if [[ -z "$SDK_ROOT" && -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "ANDROID_HOME, ANDROID_SDK_ROOT or ANDROID_NDK_HOME must be set." >&2
  exit 1
fi
if [[ ! -d "$NDK" ]]; then
  echo "Android NDK not found: $NDK" >&2
  exit 1
fi

rm -rf "$SOURCE_DIR" "$OUT_DIR" "$ARTIFACT_DIR"
mkdir -p "$(dirname "$SOURCE_DIR")" "$JNI_DIR" "$ASSET_DIR/licenses" "$ASSET_DIR/third-party"

git clone --no-checkout "$SOURCE_REPO" "$SOURCE_DIR"
git -C "$SOURCE_DIR" checkout --detach "$SOURCE_COMMIT"

(
  cd "$SOURCE_DIR"
  NDK="$NDK" \
  OUT="$OUT_DIR" \
  REPO_ARTIFACT_DIR="$ARTIFACT_DIR" \
    bash tools/build-static-aarch64.sh
)

test -s "$ARTIFACT_DIR/proot"
test -s "$ARTIFACT_DIR/loader"
test -s "$ARTIFACT_DIR/loader32"

cp "$SOURCE_DIR/COPYING" "$ARTIFACT_DIR/PRoot-COPYING"
cp "$SOURCE_DIR/third_party/talloc-2.4.2/COPYING.LESSER" "$ARTIFACT_DIR/TALLOC-LGPL-3.0.txt"
cp "$SOURCE_DIR/third_party/talloc-2.4.2/COPYING.GPL-3" "$ARTIFACT_DIR/TALLOC-GPL-3.0.txt"
printf '%s\n' \\
  "source=$SOURCE_REPO" \\
  "commit=$SOURCE_COMMIT" \\
  "ndk=28.2.13676358" \\
  "abi=arm64-v8a" \\
  "purpose=separate native executable; corresponding source and build inputs embedded in APK" \\
  > "$ARTIFACT_DIR/PROVENANCE.txt"
(cd "$ARTIFACT_DIR" && sha256sum proot loader loader32 > SHA256SUMS)

cp "$ARTIFACT_DIR/proot" "$JNI_DIR/libproot.so"
cp "$ARTIFACT_DIR/loader" "$JNI_DIR/libproot_loader.so"
cp "$ARTIFACT_DIR/loader32" "$JNI_DIR/libproot_loader32.so"

cp "$ARTIFACT_DIR/PRoot-COPYING" "$ASSET_DIR/licenses/PRoot-COPYING.txt"
cp "$ARTIFACT_DIR/TALLOC-LGPL-3.0.txt" "$ASSET_DIR/licenses/TALLOC-LGPL-3.0.txt"
cp "$ARTIFACT_DIR/TALLOC-GPL-3.0.txt" "$ASSET_DIR/licenses/TALLOC-GPL-3.0.txt"
cp "$ARTIFACT_DIR/PROVENANCE.txt" "$ASSET_DIR/licenses/PRoot-PROVENANCE.txt"

tar --exclude='.git' -czf "$ASSET_DIR/third-party/proot-corresponding-source.tar.gz" \
  -C "$ROOT_DIR/native" proot-src proot-build proot-artifacts

cat > "$ASSET_DIR/licenses/PRoot-NOTICE.txt" <<'NOTICE'
PIXEL DESK includes an Android ARM64 PRoot prototype as a separate native executable.
PRoot source is licensed under GPL-2.0-or-later. The bundled Talloc source is
provided under LGPL-3.0 (with the accompanying GPL-3.0 license text).
See the license files in this directory.

The corresponding source and relinkable build inputs for this exact pinned build
are included at third-party/proot-corresponding-source.tar.gz.
The source revision and NDK version are recorded in PRoot-PROVENANCE.txt.
NOTICE

echo "Prepared PRoot native payload:"
ls -lh "$JNI_DIR" "$ASSET_DIR/third-party/proot-corresponding-source.tar.gz"
