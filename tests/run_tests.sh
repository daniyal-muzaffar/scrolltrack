#!/bin/bash
# Runs the ScrollTrack JVM unit tests + the SQLite migration test.
# Usage: ./run_tests.sh
set -e
cd "$(dirname "$0")"
export JAVA_HOME=~/android-sdk/jdk/jdk-17.0.20.1+1
export PATH=$JAVA_HOME/bin:$PATH
SRC=../app/src/main/java
mkdir -p out
echo "== compiling pure-logic classes + tests =="
javac -encoding UTF-8 -d out \
  "$SRC/com/dudeyy/scrolltrack/util/ReelsDetection.java" \
  "$SRC/com/dudeyy/scrolltrack/util/ReelCounter.java" \
  "$SRC/com/dudeyy/scrolltrack/util/DayAttribution.java" \
  TestReelCounter.java TestDayAttribution.java
echo "== ReelCounter tests =="
java -cp out TestReelCounter
echo "== DayAttribution tests =="
java -cp out TestDayAttribution
echo "== AppUpdater tests (pure UpdateCheck) =="
javac -encoding UTF-8 -d out \
  "$SRC/com/dudeyy/scrolltrack/util/UpdateCheck.java" \
  TestAppUpdater.java
java -cp out TestAppUpdater
