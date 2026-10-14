#!/usr/bin/env bash
# 一键构建（需要 Android SDK + JDK 17）
set -e
./gradlew :app:assembleRelease
echo "产物: app/build/outputs/apk/release/app-release-unsigned.apk"
