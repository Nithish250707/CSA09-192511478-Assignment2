#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out/before out/after && {
  echo "### BEFORE completing TODO 1 (before/Attendance.java)"; javac -d out/before before/Attendance.java && java -cp out/before Attendance
  echo; echo "### AFTER completing TODO 1 (Attendance.java)"; javac -d out/after Attendance.java && java -cp out/after Attendance
  echo; echo "### ContractExperiments"; javac -d out ContractExperiments.java && java -cp out ContractExperiments
} > output.txt; cat output.txt
