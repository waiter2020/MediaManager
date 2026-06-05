# 国内环境一键构建（启用 BuildKit + 默认 .env）
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "已创建 .env（来自 .env.example）"
}

$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_DOCKER_CLI_BUILD = "1"

docker compose build @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "构建完成。启动: docker compose up -d"
