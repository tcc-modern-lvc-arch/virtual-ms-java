#!/bin/sh
# Generates the AppCDS archive on the very first start (MariaDB must be up).
# Subsequent starts skip training and use the cached archive.
JSA=/data/app.jsa

JVM_BASE="-XX:+UseG1GC \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseStringDeduplication \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseContainerSupport \
  -XX:+ExitOnOutOfMemoryError \
  -Xss512k \
  -Djava.security.egd=file:/dev/./urandom"

LAUNCHER="org.springframework.boot.loader.launch.JarLauncher"

if [ ! -f "$JSA" ]; then
  echo "[entrypoint] AppCDS archive not found — running training pass..."
  # shellcheck disable=SC2086
  java --enable-preview $JVM_BASE \
       -XX:ArchiveClassesAtExit="$JSA" \
       -Dspring.context.exit=onRefresh \
       "$LAUNCHER" || true
  echo "[entrypoint] Training done."
fi

CDS_FLAG=""
[ -f "$JSA" ] && CDS_FLAG="-XX:SharedArchiveFile=$JSA"

# shellcheck disable=SC2086
exec java --enable-preview $JVM_BASE $CDS_FLAG -Xshare:auto "$LAUNCHER"
