# TwitchDownloader rclone & Scheduler Auto-Setup Script for Windows
# Run this script as Administrator to configure automatic downloads from your VPS.

# 1. Self-elevation check
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "=========================================================" -ForegroundColor Yellow
    Write-Host " This script requires Administrator privileges!" -ForegroundColor Yellow
    Write-Host " Requesting elevation..." -ForegroundColor Yellow
    Write-Host "=========================================================" -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    Exit
}

$ErrorActionPreference = "Stop"
Clear-Host

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "       TwitchDownloader: Windows Client Setup Script     " -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "This script will download rclone, configure connection to"
Write-Host "your VPS, and set up a Windows Scheduled Task for auto-sync."
Write-Host ""

# 2. Get User Inputs
$vpsHost = Read-Host "Enter your VPS IP Address (e.g. 192.168.1.1)"
if ([string]::IsNullOrWhiteSpace($vpsHost)) { throw "VPS IP Address cannot be empty." }

$vpsUser = Read-Host "Enter SSH User [default: root]"
if ([string]::IsNullOrWhiteSpace($vpsUser)) { $vpsUser = "root" }

$authType = ""
while ($authType -ne "1" -and $authType -ne "2") {
    Write-Host "Select Authentication Type:"
    Write-Host " 1) Password"
    Write-Host " 2) SSH Private Key File"
    $authType = Read-Host "Choose [1 or 2]"
}

$vpsPassword = ""
$vpsKeyPath = ""

if ($authType -eq "1") {
    $securePassword = Read-Host "Enter SSH Password" -AsSecureString
    # Convert SecureString to PlainText
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $vpsPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
} else {
    $vpsKeyPath = Read-Host "Enter absolute path to your SSH Private Key file (e.g. C:\Users\user\.ssh\id_rsa)"
    if (-not (Test-Path $vpsKeyPath)) {
        throw "SSH Key file not found at: $vpsKeyPath"
    }
}

$localPath = Read-Host "Enter Local Downloads Path [default: D:\twitch_downloads]"
if ([string]::IsNullOrWhiteSpace($localPath)) { $localPath = "D:\twitch_downloads" }

$interval = Read-Host "Enter Sync Interval in minutes [default: 15]"
if ([string]::IsNullOrWhiteSpace($interval)) { $interval = 15 }
$interval = [int]$interval

# Create download folder if it doesn't exist
if (-not (Test-Path $localPath)) {
    New-Item -ItemType Directory -Force -Path $localPath | Out-Null
    Write-Host "Created folder: $localPath" -ForegroundColor Green
}

# 3. Check / Download rclone
$installDir = "C:\rclone"
$rclonePath = Join-Path $installDir "rclone.exe"

if (-not (Test-Path $rclonePath)) {
    Write-Host ""
    Write-Host "rclone was not found in $installDir. Downloading now..." -ForegroundColor Yellow
    
    if (-not (Test-Path $installDir)) {
        New-Item -ItemType Directory -Path $installDir | Out-Null
    }
    
    $tempZip = Join-Path $env:TEMP "rclone-download.zip"
    $downloadUrl = "https://downloads.rclone.org/rclone-current-windows-amd64.zip"
    
    Write-Host "Downloading from $downloadUrl..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $tempZip
    
    Write-Host "Extracting archive..."
    Expand-Archive -Path $tempZip -DestinationPath $tempZip.Replace(".zip", "") -Force
    
    # Locate extracted rclone.exe and copy it to C:\rclone
    $extractedExe = Get-ChildItem -Path $tempZip.Replace(".zip", "") -Filter "rclone.exe" -Recurse | Select-Object -First 1
    if ($extractedExe) {
        Copy-Item -Path (Split-Path $extractedExe.FullName) -Destination $installDir -Recurse -Force
        # Move files to root C:\rclone if nested
        $nestedPath = Join-Path $installDir $extractedExe.Directory.Name
        if (Test-Path $nestedPath) {
            Copy-Item -Path "$nestedPath\*" -Destination $installDir -Force
            Remove-Item -Path $nestedPath -Recurse -Force
        }
        Write-Host "Successfully installed rclone to $installDir" -ForegroundColor Green
    } else {
        throw "Failed to locate rclone.exe in the downloaded zip file."
    }
    
    # Add C:\rclone to user environment PATH
    $oldPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($oldPath -notlike "*C:\rclone*") {
        [Environment]::SetEnvironmentVariable("Path", "$oldPath;C:\rclone", "User")
        $env:Path += ";C:\rclone"
        Write-Host "Added C:\rclone to system User PATH." -ForegroundColor Green
    }
    
    # Cleanup temp file
    Remove-Item $tempZip -Force
} else {
    Write-Host "Found existing rclone installation at $rclonePath" -ForegroundColor Green
}

# 4. Configure rclone remote
Write-Host ""
Write-Host "Configuring rclone SFTP remote 'vps_server'..." -ForegroundColor Yellow

# Delete existing remote if any to avoid conflicts
& $rclonePath config delete vps_server 2>$null

if ($authType -eq "1") {
    # Obscure the password for security
    $obscuredPassword = & $rclonePath obscure $vpsPassword
    & $rclonePath config create vps_server sftp host $vpsHost user $vpsUser port 22 pass $obscuredPassword | Out-Null
} else {
    # Configure using SSH key file
    & $rclonePath config create vps_server sftp host $vpsHost user $vpsUser port 22 key_file $vpsKeyPath | Out-Null
}

Write-Host "Remote 'vps_server' configured successfully." -ForegroundColor Green

# 5. Create Scheduled Task in Windows (Run under SYSTEM to make it completely silent/hidden)
Write-Host ""
Write-Host "Configuring Windows Scheduled Task 'Twitch Auto Pull'..." -ForegroundColor Yellow

# Locate rclone config file
$configFilePath = Join-Path $env:APPDATA "rclone\rclone.conf"
if (-not (Test-Path $configFilePath)) {
    # Fallback to rclone's detection
    $configFilePath = (& $rclonePath config file | Select-Object -Last 1).Trim()
}

$logPath = Join-Path $localPath "rclone_scheduler.log"
$rcloneArguments = "move vps_server:/opt/TwitchDownloader/downloads/completed `"$localPath`" --delete-empty-src-dirs --config `"$configFilePath`" --log-file `"$logPath`" --log-level INFO"

# Remove existing task if any
Unregister-ScheduledTask -TaskName "Twitch Auto Pull" -Confirm:$false -ErrorAction SilentlyContinue | Out-Null

# Define Actions, Triggers, and Settings
$action = New-ScheduledTaskAction -Execute $rclonePath -Argument $rcloneArguments
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes $interval)
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

# Register the new task under SYSTEM account so it runs completely hidden without terminal window popping up
Register-ScheduledTask -TaskName "Twitch Auto Pull" -User "NT AUTHORITY\SYSTEM" -Action $action -Trigger $trigger -Settings $settings | Out-Null

Write-Host "Scheduled Task registered to run under SYSTEM account every $interval minutes (100% silent)." -ForegroundColor Green

# 6. Verify and Test Connection
Write-Host ""
Write-Host "Testing connection to VPS..." -ForegroundColor Yellow
try {
    # List the directory structure (limiting results) to check connection
    $testList = & $rclonePath lsf vps_server:/opt/TwitchDownloader/downloads 2>$null
    Write-Host "Connection test: SUCCESS!" -ForegroundColor Green
} catch {
    Write-Host "Connection test: FAILED!" -ForegroundColor Red
    Write-Host "Please check if your VPS IP, username, password, or SSH key are correct, and that port 22 is open." -ForegroundColor Yellow
}

# 7. Start the task immediately
Write-Host ""
Write-Host "Starting the synchronization task for the first time..." -ForegroundColor Yellow
Start-ScheduledTask -TaskName "Twitch Auto Pull"

Write-Host ""
Write-Host "=========================================================" -ForegroundColor Green
Write-Host "                   SETUP COMPLETE!                       " -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Green
Write-Host "Your PC will now automatically pull completed videos."
Write-Host "Logs are saved to: $logPath"
Write-Host "Press any key to close..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
