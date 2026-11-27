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

grep -Fq 'android:description="@string/app_description"' app/src/main/AndroidManifest.xml
grep -Fq 'name="app_description"' app/src/main/res/values/strings.xml
if grep -Fq 'param.processName' app/src/main/java/com/avolu/oneibar/OneIBarModule.kt; then
  echo 'Invalid libxposed processName property access remains' >&2
  exit 1
fi
grep -Fxq 'com.avolu.oneibar.OneIBarModule' app/src/main/resources/META-INF/xposed/java_init.list
grep -Fxq 'com.android.systemui' app/src/main/resources/META-INF/xposed/scope.list
grep -Fxq 'targetApiVersion=102' app/src/main/resources/META-INF/xposed/module.prop
grep -Fxq 'autoHotReload=true' app/src/main/resources/META-INF/xposed/module.prop
grep -Fq 'applicationId = "com.avolu.oneibar"' app/build.gradle.kts
grep -Fq 'versionCode = 2100 + runNumber' app/build.gradle.kts
grep -Fq 'param.getProcessName()' app/src/main/java/com/avolu/oneibar/OneIBarModule.kt
grep -Fq 'param.getPackageName()' app/src/main/java/com/avolu/oneibar/OneIBarModule.kt
grep -Fq 'config.batteryOffsetDp' app/src/main/java/com/avolu/oneibar/OneIBarStatusGroup.kt
grep -Fq 'hasSignalSource = sources.any' app/src/main/java/com/avolu/oneibar/OneIBarSystemUi.kt
grep -Fq 'oneibar_original_clock_gravity' app/src/main/res/values/ids.xml

python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for path in Path("app/src/main").rglob("*.xml"):
    ET.parse(path)
print("Android XML resources: valid")
PY

echo "One iBar source checks: OK"
