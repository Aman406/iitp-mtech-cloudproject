@echo off
REM Builds the project (with tests) and runs every sample experiment (Windows).
REM Usage: scripts\run-all.cmd   (from the project root)
cd /d "%~dp0\.."

call mvn -q clean verify || exit /b 1
set JAR=target\cloud-orchestration-sim-0.1.0-SNAPSHOT.jar

java -jar %JAR% forecast    configs\forecast-default.properties || exit /b 1
java -jar %JAR% orchestrate configs\orchestration-default.properties || exit /b 1
java -jar %JAR% orchestrate configs\orchestration-hourly-billing.properties || exit /b 1
java -jar %JAR% orchestrate configs\orchestration-slow-provisioning.properties || exit /b 1

echo All results are under .\results
