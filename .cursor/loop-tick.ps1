# UTF-8 loop tick script for mediamanager
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$payload = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'loop-prompt-mediamanager.json') -Raw -Encoding UTF8
while ($true) {
    Start-Sleep -Seconds 30
    Write-Output "AGENT_LOOP_TICK_mediamanager $payload"
}
