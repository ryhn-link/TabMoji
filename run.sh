#!/bin/bash
set -e

mvn package

rm -f server/plugins/tabmoji-*.jar
cp target/tabmoji-*.jar server/plugins

cd server
java -jar paper-1.19.2-270.jar nogui