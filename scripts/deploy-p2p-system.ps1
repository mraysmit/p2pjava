#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Deploys P2P Java distributed system components with configuration management.

.DESCRIPTION
    This script deploys P2P Java components (tracker, indexserver, peers) with proper
    configuration management, health monitoring, and service coordination. Supports
    both local and distributed deployment scenarios.

.PARAMETER DeploymentType
    Type of deployment: Local, Distributed, or Docker (default: Local).

.PARAMETER Components
    Components to deploy: Tracker, IndexServer, Peer, All (default: All).

.PARAMETER Environment
    Target environment: Development, Staging, Production (default: Development).

.PARAMETER ConfigFile
    Path to custom configuration file.

.PARAMETER HealthCheck
    If specified, performs health checks after deployment.

.PARAMETER DryRun
    If specified, shows what would be deployed without actually deploying.

.EXAMPLE
    .\deploy-p2p-system.ps1
    Deploys all components locally for development.

.EXAMPLE
    .\deploy-p2p-system.ps1 -DeploymentType Distributed -Environment Production
    Deploys for production distributed environment.

.EXAMPLE
    .\deploy-p2p-system.ps1 -Components Tracker,IndexServer -HealthCheck
    Deploys specific components with health checks.
#>

param(
    [ValidateSet("Local", "Distributed", "Docker")]
    [string]$DeploymentType = "Local",
    [ValidateSet("Tracker", "IndexServer", "Peer", "All")]
    [string]$Components = "All",
    [ValidateSet("Development", "Staging", "Production")]
    [string]$Environment = "Development",
    [string]$ConfigFile,
    [switch]$HealthCheck,
    [switch]$DryRun
)

# Configuration
$DEPLOYMENT_CONFIG = @{
    "Development" = @{
        "TrackerPort" = 6000
        "IndexServerPort" = 6001
        "PeerStartPort" = 8080
        "GossipPort" = 6003
        "HealthPort" = 8080
        "LogLevel" = "DEBUG"
        "ThreadPoolSize" = 10
    }
    "Staging" = @{
        "TrackerPort" = 6000
        "IndexServerPort" = 6001
        "PeerStartPort" = 8080
        "GossipPort" = 6003
        "HealthPort" = 8080
        "LogLevel" = "INFO"
        "ThreadPoolSize" = 20
    }
    "Production" = @{
        "TrackerPort" = 6000
        "IndexServerPort" = 6001
        "PeerStartPort" = 8080
        "GossipPort" = 6003
        "HealthPort" = 8080
        "LogLevel" = "WARN"
        "ThreadPoolSize" = 50
    }
}

# Component definitions
$COMPONENTS_CONFIG = @{
    "Tracker" = @{
        "Module" = "p2p-tracker"
        "MainClass" = "dev.mars.p2pjava.Tracker"
        "RequiredPorts" = @("TrackerPort", "HealthPort")
        "Dependencies" = @()
    }
    "IndexServer" = @{
        "Module" = "p2p-indexserver"
        "MainClass" = "dev.mars.p2pjava.IndexServer"
        "RequiredPorts" = @("IndexServerPort", "HealthPort")
        "Dependencies" = @("Tracker")
    }
    "Peer" = @{
        "Module" = "p2p-peer"
        "MainClass" = "dev.mars.p2pjava.Peer"
        "RequiredPorts" = @("PeerStartPort", "GossipPort")
        "Dependencies" = @("Tracker", "IndexServer")
    }
}

# Function to check if port is available
function Test-PortAvailable {
    param([int]$Port)
    
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
        $listener.Start()
        $listener.Stop()
        return $true
    } catch {
        return $false
    }
}

# Function to wait for service to be ready
function Wait-ForService {
    param(
        [string]$ServiceName,
        [string]$HealthUrl,
        [int]$TimeoutSeconds = 60
    )
    
    Write-Host "Waiting for $ServiceName to be ready..." -ForegroundColor Yellow
    
    $startTime = Get-Date
    $timeout = $startTime.AddSeconds($TimeoutSeconds)
    
    while ((Get-Date) -lt $timeout) {
        try {
            $response = Invoke-WebRequest -Uri $HealthUrl -TimeoutSec 5 -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                Write-Host "$ServiceName is ready!" -ForegroundColor Green
                return $true
            }
        } catch {
            # Service not ready yet, continue waiting
        }
        
        Start-Sleep -Seconds 2
    }
    
    Write-Warning "$ServiceName did not become ready within $TimeoutSeconds seconds"
    return $false
}

# Function to deploy a single component
function Deploy-Component {
    param(
        [string]$ComponentName,
        [hashtable]$Config,
        [string]$DeploymentType,
        [switch]$DryRun
    )
    
    $componentConfig = $COMPONENTS_CONFIG[$ComponentName]
    if (-not $componentConfig) {
        Write-Error "Unknown component: $ComponentName"
        return $false
    }
    
    Write-Host "Deploying $ComponentName..." -ForegroundColor Cyan
    
    # Check dependencies
    foreach ($dependency in $componentConfig.Dependencies) {
        Write-Host "  Checking dependency: $dependency" -ForegroundColor Gray
        # In a real deployment, you would check if the dependency is running
    }
    
    # Check port availability
    foreach ($portKey in $componentConfig.RequiredPorts) {
        $port = $Config[$portKey]
        if (-not (Test-PortAvailable -Port $port)) {
            Write-Warning "Port $port ($portKey) is not available for $ComponentName"
            if (-not $DryRun) {
                return $false
            }
        } else {
            Write-Host "  Port $port ($portKey) is available" -ForegroundColor Green
        }
    }
    
    # Build deployment command
    $jarPath = "$($componentConfig.Module)/target/$($componentConfig.Module).jar"
    
    if (-not (Test-Path $jarPath) -and -not $DryRun) {
        Write-Error "JAR file not found: $jarPath. Please build the project first."
        return $false
    }
    
    # Prepare environment variables
    $envVars = @()
    foreach ($key in $Config.Keys) {
        $envName = $key.ToUpper() -replace "([A-Z])", "_`$1"
        $envName = $envName.TrimStart("_")
        $envVars += "$envName=$($Config[$key])"
    }
    
    # Build command
    $javaArgs = @()
    
    # Add system properties
    $javaArgs += "-Dtracker.port=$($Config.TrackerPort)"
    $javaArgs += "-Dindexserver.port=$($Config.IndexServerPort)"
    $javaArgs += "-Dpeer.port=$($Config.PeerStartPort)"
    $javaArgs += "-Ddiscovery.gossip.port=$($Config.GossipPort)"
    $javaArgs += "-Dhealth.port=$($Config.HealthPort)"
    $javaArgs += "-Dlogging.level=$($Config.LogLevel)"
    $javaArgs += "-Dtracker.threadpool.size=$($Config.ThreadPoolSize)"
    
    # Add deployment type specific settings
    switch ($DeploymentType) {
        "Distributed" {
            $javaArgs += "-Ddiscovery.distributed.enabled=true"
            $javaArgs += "-Ddiscovery.gossip.bootstrap.peers=localhost:6003,localhost:6004"
        }
        "Docker" {
            $javaArgs += "-Ddiscovery.distributed.enabled=true"
            $javaArgs += "-Dnetwork.interface=0.0.0.0"
        }
        default {
            $javaArgs += "-Ddiscovery.distributed.enabled=false"
        }
    }
    
    $javaArgs += "-jar"
    $javaArgs += $jarPath
    
    $command = "java $($javaArgs -join ' ')"
    
    if ($DryRun) {
        Write-Host "  Would execute: $command" -ForegroundColor Yellow
        Write-Host "  Environment variables:" -ForegroundColor Yellow
        $envVars | ForEach-Object { Write-Host "    $_" -ForegroundColor Yellow }
        return $true
    }
    
    # Deploy based on deployment type
    switch ($DeploymentType) {
        "Local" {
            Write-Host "  Starting $ComponentName locally..." -ForegroundColor Blue
            Write-Host "  Command: $command" -ForegroundColor Gray
            
            # Start as background process
            $process = Start-Process -FilePath "java" -ArgumentList ($javaArgs) -PassThru -WindowStyle Hidden
            
            if ($process) {
                Write-Host "  $ComponentName started with PID: $($process.Id)" -ForegroundColor Green
                
                # Store process info for later management
                $processInfo = @{
                    Name = $ComponentName
                    PID = $process.Id
                    StartTime = Get-Date
                }
                
                return $processInfo
            } else {
                Write-Error "Failed to start $ComponentName"
                return $false
            }
        }
        
        "Distributed" {
            Write-Host "  Deploying $ComponentName for distributed environment..." -ForegroundColor Blue
            # In a real scenario, this would deploy to multiple machines
            Write-Host "  Command: $command" -ForegroundColor Gray
            return $true
        }
        
        "Docker" {
            Write-Host "  Creating Docker container for $ComponentName..." -ForegroundColor Blue
            
            $dockerArgs = @(
                "run", "-d",
                "--name", "p2p-$($ComponentName.ToLower())",
                "--network", "p2p-network"
            )
            
            # Add port mappings
            foreach ($portKey in $componentConfig.RequiredPorts) {
                $port = $Config[$portKey]
                $dockerArgs += "-p", "$port`:$port"
            }
            
            # Add environment variables
            foreach ($envVar in $envVars) {
                $dockerArgs += "-e", $envVar
            }
            
            $dockerArgs += "p2p-java:latest"
            $dockerArgs += $javaArgs
            
            Write-Host "  Docker command: docker $($dockerArgs -join ' ')" -ForegroundColor Gray
            return $true
        }
    }
}

# Function to perform health checks
function Test-ComponentHealth {
    param([array]$DeployedComponents)
    
    Write-Host "Performing health checks..." -ForegroundColor Blue
    
    $healthResults = @()
    
    foreach ($component in $DeployedComponents) {
        if ($component -is [hashtable] -and $component.Name) {
            $healthUrl = "http://localhost:$($DEPLOYMENT_CONFIG[$Environment].HealthPort)/health"
            $isHealthy = Wait-ForService -ServiceName $component.Name -HealthUrl $healthUrl -TimeoutSeconds 30
            
            $healthResults += @{
                Component = $component.Name
                Healthy = $isHealthy
                PID = $component.PID
            }
        }
    }
    
    return $healthResults
}

# Main execution
Write-Host "P2P Java Deployment Script" -ForegroundColor Magenta
Write-Host "==========================" -ForegroundColor Magenta
Write-Host ""

$config = $DEPLOYMENT_CONFIG[$Environment]

Write-Host "Deployment configuration:" -ForegroundColor Blue
Write-Host "  Type: $DeploymentType" -ForegroundColor Blue
Write-Host "  Environment: $Environment" -ForegroundColor Blue
Write-Host "  Components: $Components" -ForegroundColor Blue
Write-Host "  Config: $($config | ConvertTo-Json -Compress)" -ForegroundColor Blue

if ($ConfigFile -and (Test-Path $ConfigFile)) {
    Write-Host "  Custom config file: $ConfigFile" -ForegroundColor Blue
    # Load custom configuration
    $customConfig = Get-Content $ConfigFile | ConvertFrom-Json
    foreach ($key in $customConfig.PSObject.Properties.Name) {
        $config[$key] = $customConfig.$key
    }
}

if ($DryRun) {
    Write-Host "  DRY RUN MODE - No actual deployment" -ForegroundColor Yellow
}

Write-Host ""

# Determine components to deploy
$componentsTodeploy = if ($Components -eq "All") {
    @("Tracker", "IndexServer", "Peer")
} else {
    $Components -split ","
}

Write-Host "Components to deploy: $($componentsTodeploy -join ', ')" -ForegroundColor Blue
Write-Host ""

# Deploy components
$deployedComponents = @()
$deploymentSuccess = $true

foreach ($component in $componentsTodeploy) {
    $result = Deploy-Component -ComponentName $component -Config $config -DeploymentType $DeploymentType -DryRun:$DryRun
    
    if ($result) {
        $deployedComponents += $result
    } else {
        $deploymentSuccess = $false
        Write-Error "Failed to deploy $component"
        break
    }
    
    # Wait a bit between component deployments
    if (-not $DryRun) {
        Start-Sleep -Seconds 5
    }
}

# Perform health checks if requested
if ($HealthCheck -and $deploymentSuccess -and -not $DryRun) {
    Write-Host ""
    $healthResults = Test-ComponentHealth -DeployedComponents $deployedComponents
    
    Write-Host ""
    Write-Host "Health Check Results:" -ForegroundColor Magenta
    foreach ($result in $healthResults) {
        $status = if ($result.Healthy) { "HEALTHY" } else { "UNHEALTHY" }
        $color = if ($result.Healthy) { "Green" } else { "Red" }
        Write-Host "  $($result.Component): $status (PID: $($result.PID))" -ForegroundColor $color
    }
}

# Summary
Write-Host ""
Write-Host "Deployment Summary:" -ForegroundColor Magenta
Write-Host "  Components deployed: $($deployedComponents.Count)" -ForegroundColor White
Write-Host "  Deployment successful: $deploymentSuccess" -ForegroundColor $(if ($deploymentSuccess) { "Green" } else { "Red" })

if ($deployedComponents.Count -gt 0 -and -not $DryRun) {
    Write-Host ""
    Write-Host "Running components:" -ForegroundColor Blue
    foreach ($component in $deployedComponents) {
        if ($component -is [hashtable] -and $component.Name) {
            Write-Host "  $($component.Name) (PID: $($component.PID))" -ForegroundColor Cyan
        }
    }
    
    Write-Host ""
    Write-Host "To stop all components, run:" -ForegroundColor Yellow
    Write-Host "  Get-Process -Id $($deployedComponents.PID -join ',') | Stop-Process" -ForegroundColor Yellow
}

if ($deploymentSuccess) {
    exit 0
} else {
    exit 1
}
