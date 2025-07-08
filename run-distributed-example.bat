@echo off
REM Script to run the Distributed Discovery Example
REM This script demonstrates the distributed service discovery with gossip protocol

echo Starting Distributed P2P Discovery Example
echo ==========================================

REM Set the configuration file
set CONFIG_FILE=p2p-client/src/main/resources/distributed-config.properties

REM Check if Maven is available
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Maven not found in PATH. Please install Maven or add it to PATH.
    pause
    exit /b 1
)

REM Compile the project
echo Compiling the project...
call mvn clean compile -q
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo.
echo Starting 3 peer instances...
echo Press Ctrl+C in any window to stop all peers
echo.

REM Start peer 1
echo Starting Peer 1...
start "Peer 1" cmd /k "mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample -Dexec.args=\"peer1 8080 file1.txt\" -Dconfig.file=%CONFIG_FILE% -pl p2p-client"

REM Wait a bit before starting next peer
timeout /t 3 /nobreak >nul

REM Start peer 2
echo Starting Peer 2...
start "Peer 2" cmd /k "mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample -Dexec.args=\"peer2 8081 file2.txt\" -Dconfig.file=%CONFIG_FILE% -pl p2p-client"

REM Wait a bit before starting next peer
timeout /t 3 /nobreak >nul

REM Start peer 3
echo Starting Peer 3...
start "Peer 3" cmd /k "mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample -Dexec.args=\"peer3 8082 file3.txt\" -Dconfig.file=%CONFIG_FILE% -pl p2p-client"

echo.
echo All peers started!
echo.
echo You should see:
echo - Each peer registering its file-sharing service
echo - Gossip protocol propagating service information
echo - Peers discovering each other's services
echo - Continuous discovery updates every 30 seconds
echo.
echo To test the distributed registry:
echo 1. Watch the gossip propagation in the console windows
echo 2. Stop one peer and observe how others detect the failure
echo 3. Start additional peers to see them join the network
echo.
echo Press any key to exit this script (peers will continue running)
pause >nul
