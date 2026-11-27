#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -d .git ]]; then
  echo "Folder ini bukan repository Git: $ROOT" >&2
  exit 1
fi

BRANCH="$(git branch --show-current)"
if [[ -z "$BRANCH" ]]; then
  echo "Tidak bisa mendeteksi branch Git aktif." >&2
  exit 1
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  echo "Remote origin belum disetel." >&2
  exit 1
fi

if [[ -z "$(git config user.name || true)" ]]; then
  git config user.name "One iBar Builder"
fi
if [[ -z "$(git config user.email || true)" ]]; then
  git config user.email "oneibar-builder@users.noreply.github.com"
fi

bash scripts/verify-source.sh
git add -A
if ! git diff --cached --quiet; then
  git commit -m "One iBar v0.2.1-alpha04 source update"
else
  echo "Tidak ada perubahan source baru untuk di-commit."
fi

git pull --rebase origin "$BRANCH"
git push -u origin "$BRANCH"

echo
echo "Push selesai. GitHub Actions akan build APK otomatis."
echo "Ambil hasilnya di GitHub: Actions > Build One iBar > Artifacts."
