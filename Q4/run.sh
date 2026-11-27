#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out && javac -d out *.java && java -Xmx3g -cp out PharmacyMaps > output.txt && cat output.txt
