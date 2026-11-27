#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out && javac -d out *.java && java -Xmx2g -cp out TicketBuilderBenchmark > output.txt && cat output.txt
