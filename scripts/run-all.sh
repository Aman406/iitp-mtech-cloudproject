#!/usr/bin/env bash
# Builds the project (with tests) and runs every sample experiment.
# Usage: ./scripts/run-all.sh            (from the project root)
set -euo pipefail
cd "$(dirname "$0")/.."

mvn -q clean verify
JAR=target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar

java -jar "$JAR" forecast    configs/forecast-default.properties
java -jar "$JAR" orchestrate configs/orchestration-default.properties
java -jar "$JAR" orchestrate configs/orchestration-hourly-billing.properties
java -jar "$JAR" orchestrate configs/orchestration-slow-provisioning.properties

echo "All results are under ./results"
