#!/data/data/com.termux/files/usr/bin/bash
set -e

git add .
if ! git diff --cached --quiet; then
  git commit -m "Update One iBar"
fi

git pull --rebase origin main
git push origin main

echo "Push berhasil! Cek tab Actions di GitHub."
