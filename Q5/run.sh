#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out && javac -d out *.java && {
  echo "### java LeaderboardDemo (Complete the Code)"; java -cp out LeaderboardDemo
  echo; echo "### java RankingDesign"; java -Xmx2g -cp out RankingDesign
} > output.txt; cat output.txt
