#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Runs comprehensive tests for P2P Java modules with detailed reporting.

.DESCRIPTION
    This script executes unit tests, integration tests, and performance tests for
    P2P Java modules. It provides detailed test reporting, coverage analysis,
    and can run tests in parallel for faster execution.

.PARAMETER TestType
    Type of tests to run: Unit, Integration, Performance, or All (default: All).

.PARAMETER ModuleFilter
    Optional filter to test only specific modules (comma-separated list).

.PARAMETER Parallel
    If specified, runs tests in parallel where possible.

.PARAMETER Coverage
    If specified, generates test coverage reports.

.PARAMETER FailFast
    If specified, stops testing on first failure.

.PARAMETER Verbose
    Enables verbose test output.

.EXAMPLE
    .\run-p2p-tests.ps1
    Runs all tests for all modules.

.EXAMPLE
    .\run-p2p-tests.ps1 -TestType Unit -Coverage
    Runs only unit tests with coverage reporting.

.EXAMPLE
    .\run-p2p-tests.ps1 -ModuleFilter "p2p-tracker,p2p-discovery" -Parallel
    Runs tests for specific modules in parallel.
#>

param(
    [ValidateSet("Unit", "Integration", "Performance", "All")]
    [string]$TestType = "All",
    [string]$ModuleFilter,
    [switch]$Parallel,
    [switch]$Coverage,
    [switch]$FailFast,
    [switch]$Verbose
)

# Configuration
$P2P_MODULES = @(
    "p2p-common-api",
    "p2p-util",
    "p2p-config",
    "p2p-health",
    "p2p-monitoring",
    "p2p-discovery",
    "p2p-storage",
    "p2p-connection",
    "p2p-cache",
    "p2p-circuit",
    "p2p-auth",
    "p2p-bootstrap",
    "p2p-tracker",
    "p2p-indexserver",
    "p2p-peer",
    "p2p-client"
)

# Test patterns for different test types
$TEST_PATTERNS = @{
    "Unit" = "*Test.java"
    "Integration" = "*IntegrationTest.java"
    "Performance" = "*PerformanceTest.java"
}

# Function to check if module has tests
function Test-ModuleHasTests {
    param(
        [string]$ModuleName,
        [string]$TestPattern
    )
    
    $testDir = Join-Path $ModuleName "src/test/java"
    if (-not (Test-Path $testDir)) {
        return $false
    }
    
    $testFiles = Get-ChildItem -Path $testDir -Recurse -Filter $TestPattern
    return $testFiles.Count -gt 0
}

# Function to run tests for a single module
function Invoke-ModuleTests {
    param(
        [string]$ModuleName,
        [string]$TestType,
        [switch]$Coverage,
        [switch]$Verbose
    )
    
    if (-not (Test-Path $ModuleName)) {
        Write-Warning "Module $ModuleName does not exist, skipping..."
        return @{
            Module = $ModuleName
            Success = $false
            TestsRun = 0
            TestsPassed = 0
            TestsFailed = 0
            Duration = 0
            Error = "Module not found"
        }
    }
    
    Write-Host "Running $TestType tests for: $ModuleName" -ForegroundColor Cyan
    
    try {
        $testArgs = @("test", "-pl", $ModuleName)
        
        # Add test type filter
        if ($TestType -ne "All") {
            $pattern = $TEST_PATTERNS[$TestType]
            $testArgs += "-Dtest=**/$($pattern.Replace('.java', ''))"
        }
        
        # Add coverage if requested
        if ($Coverage) {
            $testArgs += "jacoco:report"
        }
        
        # Add verbose output if requested
        if (-not $Verbose) {
            $testArgs += "-q"
        }
        
        $startTime = Get-Date
        
        if ($Verbose) {
            Write-Host "  Command: mvn $($testArgs -join ' ')" -ForegroundColor Gray
        }
        
        $output = & mvn $testArgs 2>&1
        $exitCode = $LASTEXITCODE
        
        $duration = (Get-Date) - $startTime
        
        # Parse test results from output
        $testsRun = 0
        $testsPassed = 0
        $testsFailed = 0
        
        if ($output) {
            $testSummaryLine = $output | Where-Object { $_ -match "Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)" } | Select-Object -Last 1
            if ($testSummaryLine) {
                if ($testSummaryLine -match "Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)") {
                    $testsRun = [int]$matches[1]
                    $failures = [int]$matches[2]
                    $errors = [int]$matches[3]
                    $testsFailed = $failures + $errors
                    $testsPassed = $testsRun - $testsFailed
                }
            }
        }
        
        $success = ($exitCode -eq 0)
        
        if ($success) {
            Write-Host "  SUCCESS - $testsRun tests run, $testsPassed passed in $($duration.TotalSeconds.ToString('F1'))s" -ForegroundColor Green
        } else {
            Write-Host "  FAILED - $testsRun tests run, $testsFailed failed in $($duration.TotalSeconds.ToString('F1'))s" -ForegroundColor Red
            if ($Verbose -and $output) {
                Write-Host "  Test output:" -ForegroundColor Red
                $output | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
            }
        }
        
        return @{
            Module = $ModuleName
            Success = $success
            TestsRun = $testsRun
            TestsPassed = $testsPassed
            TestsFailed = $testsFailed
            Duration = $duration.TotalSeconds
            Error = if ($success) { $null } else { "Tests failed" }
        }
        
    } catch {
        Write-Host "  ERROR - Exception running tests for $ModuleName`: $_" -ForegroundColor Red
        return @{
            Module = $ModuleName
            Success = $false
            TestsRun = 0
            TestsPassed = 0
            TestsFailed = 0
            Duration = 0
            Error = $_.Exception.Message
        }
    }
}

# Function to generate coverage report
function New-CoverageReport {
    param([array]$TestResults)
    
    Write-Host "Generating coverage report..." -ForegroundColor Blue
    
    try {
        $coverageArgs = @("jacoco:report-aggregate")
        & mvn $coverageArgs
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Coverage report generated successfully" -ForegroundColor Green
            
            # Look for coverage report
            $reportPath = "target/site/jacoco-aggregate/index.html"
            if (Test-Path $reportPath) {
                Write-Host "Coverage report available at: $reportPath" -ForegroundColor Cyan
            }
        } else {
            Write-Warning "Failed to generate coverage report"
        }
    } catch {
        Write-Warning "Error generating coverage report: $_"
    }
}

# Main execution
Write-Host "P2P Java Test Runner" -ForegroundColor Magenta
Write-Host "====================" -ForegroundColor Magenta
Write-Host ""

# Filter modules if specified
$modulesToTest = if ($ModuleFilter) {
    $filterList = $ModuleFilter -split ","
    $P2P_MODULES | Where-Object { $filterList -contains $_ }
} else {
    $P2P_MODULES
}

if ($modulesToTest.Count -eq 0) {
    Write-Error "No modules to test. Check your filter: $ModuleFilter"
    exit 1
}

Write-Host "Test configuration:" -ForegroundColor Blue
Write-Host "  Test type: $TestType" -ForegroundColor Blue
Write-Host "  Modules: $($modulesToTest -join ', ')" -ForegroundColor Blue
Write-Host "  Parallel: $Parallel" -ForegroundColor Blue
Write-Host "  Coverage: $Coverage" -ForegroundColor Blue
Write-Host "  Fail fast: $FailFast" -ForegroundColor Blue
Write-Host ""

# Check Maven availability
try {
    $mavenVersion = & mvn --version 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Maven not found"
    }
    Write-Host "Maven version: $($mavenVersion[0])" -ForegroundColor Green
} catch {
    Write-Error "Maven is not available. Please install Maven and ensure it's in your PATH."
    exit 1
}

Write-Host ""

# Run tests
$startTime = Get-Date
$allResults = @()

if ($Parallel) {
    Write-Host "Running tests in parallel..." -ForegroundColor Blue
    
    $jobs = @()
    foreach ($module in $modulesToTest) {
        $job = Start-Job -ScriptBlock {
            param($ModuleName, $TestType, $Coverage, $Verbose)
            
            # Import function into job context
            function Invoke-ModuleTests {
                param(
                    [string]$ModuleName,
                    [string]$TestType,
                    [switch]$Coverage,
                    [switch]$Verbose
                )
                
                $testArgs = @("test", "-pl", $ModuleName)
                if ($TestType -ne "All") {
                    $pattern = switch ($TestType) {
                        "Unit" { "*Test.java" }
                        "Integration" { "*IntegrationTest.java" }
                        "Performance" { "*PerformanceTest.java" }
                    }
                    $testArgs += "-Dtest=**/$($pattern.Replace('.java', ''))"
                }
                if ($Coverage) { $testArgs += "jacoco:report" }
                if (-not $Verbose) { $testArgs += "-q" }
                
                $startTime = Get-Date
                $output = & mvn $testArgs 2>&1
                $duration = (Get-Date) - $startTime
                
                return @{
                    Module = $ModuleName
                    Success = ($LASTEXITCODE -eq 0)
                    Duration = $duration.TotalSeconds
                    Output = $output
                }
            }
            
            return Invoke-ModuleTests -ModuleName $ModuleName -TestType $TestType -Coverage:$Coverage -Verbose:$Verbose
            
        } -ArgumentList $module, $TestType, $Coverage, $Verbose
        
        $jobs += $job
    }
    
    # Wait for all jobs and collect results
    foreach ($job in $jobs) {
        $result = Receive-Job -Job $job -Wait
        $allResults += $result
        Remove-Job -Job $job
    }
} else {
    # Sequential execution
    foreach ($module in $modulesToTest) {
        $result = Invoke-ModuleTests -ModuleName $module -TestType $TestType -Coverage:$Coverage -Verbose:$Verbose
        $allResults += $result
        
        if ($FailFast -and -not $result.Success) {
            Write-Host ""
            Write-Host "Tests failed for $($result.Module). Stopping due to -FailFast." -ForegroundColor Red
            break
        }
    }
}

$totalTime = (Get-Date) - $startTime

# Generate coverage report if requested
if ($Coverage) {
    Write-Host ""
    New-CoverageReport -TestResults $allResults
}

# Summary
Write-Host ""
Write-Host "Test Summary:" -ForegroundColor Magenta
$successfulModules = $allResults | Where-Object { $_.Success }
$failedModules = $allResults | Where-Object { -not $_.Success }

Write-Host "  Total modules tested: $($allResults.Count)" -ForegroundColor White
Write-Host "  Successful: $($successfulModules.Count)" -ForegroundColor Green
Write-Host "  Failed: $($failedModules.Count)" -ForegroundColor Red
Write-Host "  Total time: $($totalTime.TotalMinutes.ToString('F1')) minutes" -ForegroundColor White

if ($allResults.Count -gt 0) {
    $totalTests = ($allResults | Measure-Object -Property TestsRun -Sum).Sum
    $totalPassed = ($allResults | Measure-Object -Property TestsPassed -Sum).Sum
    $totalFailed = ($allResults | Measure-Object -Property TestsFailed -Sum).Sum
    
    Write-Host "  Total tests run: $totalTests" -ForegroundColor White
    Write-Host "  Total passed: $totalPassed" -ForegroundColor Green
    Write-Host "  Total failed: $totalFailed" -ForegroundColor Red
}

if ($failedModules.Count -gt 0) {
    Write-Host ""
    Write-Host "Failed modules:" -ForegroundColor Red
    $failedModules | ForEach-Object { 
        Write-Host "  - $($_.Module): $($_.Error)" -ForegroundColor Red 
    }
    exit 1
} else {
    Write-Host ""
    Write-Host "All tests passed successfully!" -ForegroundColor Green
    exit 0
}
