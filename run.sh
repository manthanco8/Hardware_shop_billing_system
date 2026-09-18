#!/usr/bin/env bash
set -e
mkdir -p lib bin
if [ ! -f lib/sqlite-jdbc.jar ]; then
  echo "Please place the SQLite JDBC driver at lib/sqlite-jdbc.jar before running."
  exit 1
fi
javac -cp "lib/sqlite-jdbc.jar" -d bin src/*.java
java -cp "bin:lib/sqlite-jdbc.jar" HardwareShopApp
