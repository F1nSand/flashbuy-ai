$ErrorActionPreference = 'Continue'
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pidFile = Join-Path $repoRoot '.demo/frontend.pid'
$projectName = 'goodskill-demo'
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) {
    $dockerCli = $dockerCommand.Source
} else {
    $desktopDockerCli = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (Test-Path -LiteralPath $desktopDockerCli -PathType Leaf) { $dockerCli = $desktopDockerCli }
}
$composeFiles = @(
    '-f', (Join-Path $repoRoot 'goodskill-simple.yml'),
    '-f', (Join-Path $repoRoot 'docker-compose.demo.yml')
)

if (Test-Path -LiteralPath $pidFile) {
    $frontendPid = 0
    if ([int]::TryParse((Get-Content -Raw -LiteralPath $pidFile), [ref]$frontendPid)) {
        $frontend = Get-Process -Id $frontendPid -ErrorAction SilentlyContinue
        if ($frontend) {
            Stop-Process -Id $frontendPid -Force -ErrorAction SilentlyContinue
            Write-Host "已停止本地前端进程 $frontendPid。"
        }
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

if ($dockerCli) {
    $previousProfiles = $env:COMPOSE_PROFILES
    $env:COMPOSE_PROFILES = 'ai'
    & $dockerCli compose --project-name $projectName @composeFiles down
    if ($null -eq $previousProfiles) {
        Remove-Item Env:\COMPOSE_PROFILES -ErrorAction SilentlyContinue
    } else {
        $env:COMPOSE_PROFILES = $previousProfiles
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'Compose 停止过程中出现错误；未删除任何数据卷。'
        exit $LASTEXITCODE
    }
    Write-Host '演示容器和网络已停止。MySQL、Redis、MongoDB 与 MinIO 数据卷均已保留。'
} else {
    Write-Warning '找不到 Docker 命令；只停止了本地前端进程。'
}
