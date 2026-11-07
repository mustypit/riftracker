#!/bin/bash
# Downloads the big Robolectric jars with curl (resumable, with retries), for cloud sessions where
# Maven Central's rate limiting (HTTP 429) makes Gradle fail on them.
#  - The Android runtimes go to app/build/robolectric-runtime/ (task copyRobolectricRuntime skips
#    jars already there).
#  - The native graphics runtime (~160 MB) goes to ~/.m2/repository: when Gradle needs a file it
#    first fetches the small .sha1 and reuses a local copy with a matching checksum.
# Versions must match app/build.gradle.kts (Robolectric 4.14.1).
cd "$(dirname "$0")/.." || exit 1

fetch() { # fetch <maven path> <destination file>
  [ -s "$2" ] && return 0
  mkdir -p "$(dirname "$2")"
  for i in $(seq 1 30); do
    for H in repo1.maven.org repo.maven.apache.org; do
      code=$(curl -sfL -C - -o "$2.part" -w '%{http_code}' "https://$H/maven2/$1")
      # 416 on resume means the partial file is already complete.
      if [ $? -eq 0 ] || [ "$code" = 416 ]; then mv "$2.part" "$2" && echo "got $(basename "$2")" && return 0; fi
    done
    sleep 4
  done
  echo "failed to download $1"; exit 1
}

DIR=app/build/robolectric-runtime
for V in 14-robolectric-10818077-i7 15-robolectric-12650502-i7; do
  F=android-all-instrumented-$V.jar
  fetch "org/robolectric/android-all-instrumented/$V/$F" "$DIR/$F"
done

NR=org/robolectric/nativeruntime-dist-compat/1.0.16/nativeruntime-dist-compat-1.0.16.jar
fetch "$NR" "$HOME/.m2/repository/$NR"
