#!/usr/bin/env bash
# Downloads the official CPython Android builds from python.org and packages
# them into app/src/main/jniLibs/<abi>/ for PythonX.
#
# Output per ABI:
#   libpython3.14.so, libpython3.so      CPython runtime
#   libssl_python.so, libcrypto_python.so, libsqlite3_python.so
#   libpythonx.so                        `python3` launcher executable
#   libpystdlib.so                       zip of lib/python3.14 (extracted on first run)
#
# Usage: tools/build-python.sh [ndk-dir]
set -euo pipefail

PY_VERSION=3.14.8
PY_MAJOR_MINOR=3.14
API_LEVEL=26

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${1:-${ANDROID_NDK_HOME:-$(ls -d "$HOME"/Library/Android/sdk/ndk/28* | tail -1)}}"
TOOLCHAIN="$(echo "$NDK"/toolchains/llvm/prebuilt/*)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# abi triple sha256
ABIS="
arm64-v8a aarch64-linux-android 11324313957da3736f44e90257304d288864321a4dd376ed0cc35a54eacf9a33
x86_64 x86_64-linux-android 58eb3b2d76ef57e076985a0a6b093cf08906d65ea4ab78cccc6d894d4250c972
"

TZDATA_VERSION=2026.5
TZDATA_WHEEL="$WORK/tzdata.whl"
curl -sSfL -o "$TZDATA_WHEEL" \
  "https://files.pythonhosted.org/packages/94/21/1e5995a1c920cce14e4bffae20c665ec10e7ed03ab25e006cd741092b718/tzdata-$TZDATA_VERSION-py2.py3-none-any.whl"
echo "b683bd1b6659ddcd810ff02ad09ba821d4bf1065072805063eb35c49617905ac  $TZDATA_WHEEL" | shasum -a 256 -c -

echo "$ABIS" | while read -r abi triple sha; do
  [ -z "$abi" ] && continue
  archive="$WORK/python-$abi.tar.gz"
  echo "==> $abi: downloading Python $PY_VERSION"
  curl -sSfL -o "$archive" \
    "https://www.python.org/ftp/python/$PY_VERSION/python-$PY_VERSION-$triple.tar.gz"
  echo "$sha  $archive" | shasum -a 256 -c -

  src="$WORK/$abi"
  mkdir -p "$src"
  tar xzf "$archive" -C "$src"
  prefix="$src/prefix"

  out="$ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$out"
  for lib in libpython$PY_MAJOR_MINOR.so libpython3.so libssl_python.so \
             libcrypto_python.so libsqlite3_python.so; do
    cp "$prefix/lib/$lib" "$out/"
  done

  echo "==> $abi: building launcher"
  "$TOOLCHAIN/bin/clang" --target="$triple$API_LEVEL" \
    -O2 -fPIE -pie -Wl,-z,max-page-size=16384 -Wl,-rpath,'$ORIGIN' \
    -I"$prefix/include/python$PY_MAJOR_MINOR" \
    "$ROOT/tools/pythonx.c" -L"$prefix/lib" -lpython$PY_MAJOR_MINOR \
    -o "$out/libpythonx.so"
  "$TOOLCHAIN/bin/llvm-strip" "$out/libpythonx.so"

  echo "==> $abi: packaging standard library"
  stdlib="$prefix/lib/python$PY_MAJOR_MINOR"
  # Android's time zone database isn't in a format zoneinfo can read; ship the IANA data
  # from PyPI's tzdata package, which zoneinfo falls back to automatically.
  unzip -q -o "$TZDATA_WHEEL" 'tzdata/*' -d "$stdlib"
  rm -rf "$stdlib"/{test,idlelib,tkinter,turtledemo,turtle.py}
  find "$stdlib" -name __pycache__ -prune -exec rm -rf {} +
  rm -f "$out/libpystdlib.so"
  (cd "$prefix" && zip -qr -9 "$out/libpystdlib.so" "lib/python$PY_MAJOR_MINOR")
done

echo "Done."
