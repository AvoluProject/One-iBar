#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$HOME/One-iBar"

if ! command -v git >/dev/null 2>&1; then
  echo "Git belum terpasang. Jalankan: pkg install git" >&2
  exit 1
fi
if ! command -v unzip >/dev/null 2>&1; then
  echo "Unzip belum terpasang. Jalankan: pkg install unzip" >&2
  exit 1
fi

if [[ ! -d "$REPO/.git" ]]; then
  echo "Repository ~/One-iBar belum ada. Clone dulu repository GitHub kamu." >&2
  exit 1
fi

if [[ -n "$(git -C "$REPO" status --porcelain)" ]]; then
  echo "Repository ~/One-iBar masih punya perubahan lokal." >&2
  echo "Selesaikan atau backup perubahan itu dulu agar tidak tertimpa." >&2
  exit 1
fi

if [[ ! -f "$SOURCE_ROOT/settings.gradle.kts" || ! -f "$SOURCE_ROOT/app/src/main/java/com/avolu/oneibar/OneIBarModule.kt" ]]; then
  echo "Folder sumber One iBar tidak lengkap: $SOURCE_ROOT" >&2
  exit 1
fi

cp -a "$SOURCE_ROOT/." "$REPO/"
echo "Source v0.2.1-alpha04 disalin ke ~/One-iBar."
cd "$REPO"
bash ./build.sh
