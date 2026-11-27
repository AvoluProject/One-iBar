#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

required=(
  "app/src/main/AndroidManifest.xml"
  "app/src/main/java/com/avolu/oneibar/OneIBarModule.kt"
  "app/src/main/java/com/avolu/oneibar/SystemUiHooks.kt"
  "app/src/main/java/com/avolu/oneibar/OneIBarSystemUi.kt"
  "app/src/main/java/com/avolu/oneibar/OneIBarStatusGroup.kt"
  "app/src/main/java/com/avolu/oneibar/OneIBarDrawable.kt"
  "app/src/main/java/com/avolu/oneibar/OneIBarConfig.kt"
  "app/src/main/resources/META-INF/xposed/java_init.list"
  "app/src/main/resources/META-INF/xposed/scope.list"
  "app/src/main/resources/META-INF/xposed/module.prop"
  ".github/workflows/android.yml"
)
for file in "${required[@]}"; do
  test -s "$file" || { echo "Missing/empty required file: $file" >&2; exit 1; }
done

grep -Fxq 'com.avolu.oneibar.OneIBarModule' app/src/main/resources/META-INF/xposed/java_init.list
grep -Fxq 'com.android.systemui' app/src/main/resources/META-INF/xposed/scope.list
grep -Fxq 'targetApiVersion=102' app/src/main/resources/META-INF/xposed/module.prop
grep -Fxq 'autoHotReload=true' app/src/main/resources/META-INF/xposed/module.prop
grep -Fq 'applicationId = "com.avolu.oneibar"' app/build.gradle.kts
grep -Fq 'versionCode = 2000 + runNumber' app/build.gradle.kts

python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for path in Path("app/src/main").rglob("*.xml"):
    ET.parse(path)
print("Android XML resources: valid")
PY

echo "One iBar source checks: OK"
