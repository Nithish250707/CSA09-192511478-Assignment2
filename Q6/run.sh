#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out && javac -d out *.java && {
  echo "### java Triage (Complete the Code)"; java -cp out Triage
  echo; echo "### java TriageStudy"; java -Xmx3g -cp out TriageStudy
} > output.txt; cat output.txt
