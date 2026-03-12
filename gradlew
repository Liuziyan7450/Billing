#!/usr/bin/env sh

set -e

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
  exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  echo "[billing] gradle-wrapper.jar 未提交，回退到系统 gradle。" >&2
  exec gradle "$@"
fi

echo "Gradle is not installed, and gradle-wrapper.jar is missing." >&2
echo "Please open this project in Android Studio (which can regenerate wrapper files)," >&2
echo "or run: gradle wrapper --gradle-version 8.7" >&2
exit 1
