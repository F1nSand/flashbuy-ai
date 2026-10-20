param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$uiRoot = Join-Path $repoRoot 'goodskill-ui'
$stateRoot = Join-Path $repoRoot '.demo'
$logRoot = Join-Path $stateRoot 'logs'
$pidFile = Join-Path $stateRoot 'frontend.pid'
$projectName = 'goodskill-demo'
$mysqlHostPort = $null
$script:enableAi = $false
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) {
    $script:dockerCli = $dockerCommand.Source
} else {
    $desktopDockerCli = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (Test-Path -LiteralPath $desktopDockerCli -PathType Leaf) { $script:dockerCli = $desktopDockerCli }
}
$composeFiles = @(
    '-f', (Join-Path $repoRoot 'goodskill-simple.yml'),
    '-f', (Join-Path $repoRoot 'docker-compose.demo.yml')
)

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Invoke-Compose([string[]]$ComposeArgs) {
    $previousMysqlHostPort = $env:GOODSKILL_MYSQL_HOST_PORT
    $env:GOODSKILL_MYSQL_HOST_PORT = $script:mysqlHostPort.ToString()
    try {
        & $script:dockerCli compose --project-name $projectName @composeFiles @ComposeArgs
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose $($ComposeArgs -join ' ') 执行失败，退出码 $LASTEXITCODE。请查看 Docker Desktop 和 .demo/logs。"
        }
    } finally {
        if ($null -eq $previousMysqlHostPort) {
            Remove-Item Env:\GOODSKILL_MYSQL_HOST_PORT -ErrorAction SilentlyContinue
        } else {
            $env:GOODSKILL_MYSQL_HOST_PORT = $previousMysqlHostPort
        }
    }
}

function Invoke-AiCompose([string[]]$ComposeArgs) {
    $previousProfiles = $env:COMPOSE_PROFILES
    $env:COMPOSE_PROFILES = 'ai'
    try {
        Invoke-Compose $ComposeArgs
    } finally {
        if ($null -eq $previousProfiles) {
            Remove-Item Env:\COMPOSE_PROFILES -ErrorAction SilentlyContinue
        } else {
            $env:COMPOSE_PROFILES = $previousProfiles
        }
    }
}

function Wait-TcpPort([string]$Name, [int]$Port, [int]$TimeoutSeconds = 300) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $connection = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($connection) { return }
        Start-Sleep -Seconds 2
    }
    throw "$Name 未能在 $TimeoutSeconds 秒内监听端口 $Port。请查看 docker compose logs $Name。"
}

function Get-NacosToken([string]$BaseUrl) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $lastError = $null
    while ($watch.Elapsed.TotalSeconds -lt 60) {
        try {
            $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/nacos/v1/auth/login" `
                -ContentType 'application/x-www-form-urlencoded' -Body @{ username = 'nacos'; password = 'nacos' }
            if ($login.accessToken) { return $login.accessToken }
            $lastError = 'Nacos 登录响应未包含 accessToken。'
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 3
    }
    throw "Nacos 登录在 60 秒内未成功，请检查 Nacos 启动日志和 nacos/nacos 演示账号。最后错误：$lastError"
}

function Publish-NacosConfig([string]$BaseUrl, [string]$Token, [string]$DataId, [string]$Content) {
    $body = @{
        dataId = $DataId
        group = 'DEFAULT_GROUP'
        type = 'yaml'
        content = $Content
        accessToken = $Token
    }
    $result = Invoke-RestMethod -Method Post -Uri "$BaseUrl/nacos/v1/cs/configs" `
        -ContentType 'application/x-www-form-urlencoded' -Body $body
    if ($result -ne 'true') { throw "导入 Nacos 配置 $DataId 失败：$result" }
    Write-Host "已导入 Nacos 配置：$DataId"
}

function Wait-NacosService([string]$BaseUrl, [string]$Token, [string]$ServiceName, [int]$TimeoutSeconds = 240) {
    $encodedName = [Uri]::EscapeDataString($ServiceName)
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $instances = Invoke-RestMethod -Uri "$BaseUrl/nacos/v1/ns/instance/list?serviceName=$encodedName&groupName=DEFAULT_GROUP&healthyOnly=true&accessToken=$Token"
            if (@($instances.hosts | Where-Object { $_.healthy }).Count -gt 0) {
                Write-Host "服务已在 Nacos 注册：$ServiceName"
                return
            }
        } catch {
            # Nacos may still be starting or refreshing its auth token.
        }
        Start-Sleep -Seconds 3
    }
    throw "服务 $ServiceName 未能在 $TimeoutSeconds 秒内注册到 Nacos。请查看该服务日志：docker compose logs $($ServiceName -replace '^goodskill-', '')"
}

try {
    Set-Location $repoRoot

    if (-not (Test-Path -LiteralPath '.env')) {
        Copy-Item -LiteralPath '.env.example' -Destination '.env'
        Write-Host '已创建根目录 .env，请填写 DeepSeek API Key 后重新运行即可启用 AI 服务。'
    }
    $aiApiKey = $env:DEEPSEEK_API_KEY
    if ([string]::IsNullOrWhiteSpace($aiApiKey)) {
        foreach ($line in Get-Content -LiteralPath '.env') {
            if ($line -match '^\s*DEEPSEEK_API_KEY\s*=\s*(.*?)\s*$') {
                $aiApiKey = $Matches[1].Trim()
                if ($aiApiKey.Length -ge 2 -and (($aiApiKey.StartsWith('"') -and $aiApiKey.EndsWith('"')) -or ($aiApiKey.StartsWith("'") -and $aiApiKey.EndsWith("'")))) {
                    $aiApiKey = $aiApiKey.Substring(1, $aiApiKey.Length - 2)
                }
                break
            }
        }
    }
    $script:enableAi = -not [string]::IsNullOrWhiteSpace($aiApiKey)
    if ($script:enableAi) {
        $env:DEEPSEEK_API_KEY = $aiApiKey
        Write-Host '检测到 DeepSeek API Key，将在本次演示中启动 goodskill-ai。'
    } else {
        Write-Host 'DEEPSEEK_API_KEY 为空；跳过 AI 服务启动，其他演示功能照常启动。'
    }

    $requiredFiles = @(
        'goodskill-simple.yml',
        'docker-compose.demo.yml',
        '.env.example',
        '.dockerignore',
        'Dockerfile.demo',
        'goodskill-common.yml',
        'goodskill-common-connection.yml',
        'doc/docker/nacos/Dockerfile',
        'doc/docker/nacos/conf/application.properties',
        'doc/docker/nacos/init.d/custom.properties',
        'goodskill-web/src/main/sql/seckill.sql',
        'goodskill-web/src/main/sql/nacos_config.sql',
        'goodskill-ui/package.json',
        'goodskill-ui/package-lock.json'
    )
    foreach ($path in $requiredFiles) {
        if (-not (Test-Path (Join-Path $repoRoot $path))) { throw "缺少必需文件：$path" }
    }

    foreach ($tool in @('java', 'mvn', 'node', 'npm', 'docker')) {
        if ($tool -eq 'docker') { continue }
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "找不到 $tool。请先安装并将其加入 PATH。" }
    }
    if (-not $script:dockerCli) { throw '找不到 Docker CLI。请启动 Docker Desktop，或将 docker.exe 加入 PATH。' }
    $javaVersion = (& java -version 2>&1 | Select-Object -First 1).ToString()
    if ($javaVersion -notmatch '"(?<version>\d+)' -or [int]$Matches.version -lt 21) {
        throw "本地演示需要 JDK 21 或更高版本；当前检测到：$javaVersion"
    }
    & $script:dockerCli compose version *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 不可用。请启动 Docker Desktop 并启用 Linux 容器。' }
    & $script:dockerCli info *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Engine 未运行。请启动 Docker Desktop 后重新运行此脚本。' }

    $mysqlPorts = @(3306) + @(13306..13315)
    foreach ($candidatePort in $mysqlPorts) {
        $listener = Get-NetTCPConnection -State Listen -LocalPort $candidatePort -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $listener) {
            $mysqlHostPort = $candidatePort
            break
        }
    }
    if (-not $mysqlHostPort) { throw '主机 MySQL 端口 3306 和备用端口 13306-13315 均被占用。请释放其中一个端口后重试。' }
    if ($mysqlHostPort -ne 3306) { Write-Host "主机端口 3306 已占用，演示 MySQL 将映射到 $mysqlHostPort。" }

    $ports = @(5173, 18080, 8848, 9848, $mysqlHostPort, 6379, 5672, 15672, 2181, 9092, 1099, 9200, 9300, 27017, 19000, 19001)
    $conflicts = foreach ($port in $ports) {
        $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($listener) { "${port} (PID $($listener.OwningProcess))" }
    }
    if ($conflicts) {
        throw "以下演示端口已被占用：$($conflicts -join ', ')。请先停止占用这些端口的程序后重试。"
    }

    Write-Step '校验演示 Compose 配置'
    Invoke-Compose @('config', '--quiet')

    Write-Step '构建 Java 服务'
    New-Item -ItemType Directory -Path $stateRoot -Force | Out-Null
    $mavenRepo = Join-Path $stateRoot 'maven-repository'
    $mavenSettings = Join-Path $stateRoot 'maven-settings.xml'
    $mavenRepoForXml = $mavenRepo.Replace('\', '/')
    @"
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>$mavenRepoForXml</localRepository>
</settings>
"@ | Set-Content -LiteralPath $mavenSettings -Encoding utf8
    $mavenProjects = 'goodskill-gateway,goodskill-auth/auth-service,goodskill-common/goodskill-common-service,goodskill-order-provider/goodskill-order-service,goodskill-seckill-provider/goodskill-service,goodskill-web,goodskill-ai'
    & mvn -s $mavenSettings -B -ntp '-Dmaven.test.skip=true' -pl $mavenProjects -am package
    if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败，退出码 $LASTEXITCODE。" }

    $jarFiles = @(
        'goodskill-gateway/target/goodskill.jar',
        'goodskill-auth/auth-service/target/goodskill.jar',
        'goodskill-common/goodskill-common-service/target/goodskill.jar',
        'goodskill-order-provider/goodskill-order-service/target/goodskill-mongo-service.jar',
        'goodskill-seckill-provider/goodskill-service/target/goodskill-service.jar',
        'goodskill-web/target/goodskill.jar',
        'goodskill-ai/target/goodskill.jar'
    )
    foreach ($jarFile in $jarFiles) {
        $jarPath = Join-Path $repoRoot $jarFile
        if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
            throw "缺少服务构建产物：$jarFile。Maven 构建结束但 JAR 名称不符合预期。"
        }
        if ((Get-Item -LiteralPath $jarPath).Length -eq 0) { throw "服务构建产物为空：$jarFile" }
    }

    Write-Step '安装前端锁定依赖并构建'
    Push-Location $uiRoot
    try {
        $npmCache = Join-Path $stateRoot 'npm-cache'
        & npm ci --cache $npmCache
        if ($LASTEXITCODE -ne 0) { throw "npm ci 失败，退出码 $LASTEXITCODE。" }
        & npm run build:demo
        if ($LASTEXITCODE -ne 0) { throw "前端演示构建失败，退出码 $LASTEXITCODE。" }
    } finally {
        Pop-Location
    }

    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null

    Write-Step '启动数据库和中间件'
    Invoke-Compose @('up', '-d', 'mysql', 'redis', 'rabbitmq-management', 'zookeeper', 'kafka', 'elasticsearch', 'mongo', 'minio')
    foreach ($dependency in @(
        @{ Name = 'MySQL'; Port = $mysqlHostPort },
        @{ Name = 'Redis'; Port = 6379 },
        @{ Name = 'RabbitMQ'; Port = 5672 },
        @{ Name = 'ZooKeeper'; Port = 2181 },
        @{ Name = 'Kafka'; Port = 9092 },
        @{ Name = 'Elasticsearch'; Port = 9200 },
        @{ Name = 'MongoDB'; Port = 27017 },
        @{ Name = 'MinIO'; Port = 19000 }
    )) {
        Write-Host "等待 $($dependency.Name) :$($dependency.Port) ..."
        Wait-TcpPort $dependency.Name $dependency.Port
    }

    Write-Step '迁移演示 Nacos 数据库结构'
    foreach ($table in @('config_info', 'config_info_beta', 'his_config_info')) {
        $migrationSql = @"
SET @column_count = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'gs_config' AND table_name = '$table' AND column_name = 'encrypted_data_key');
SET @ddl = IF(@column_count = 0, 'ALTER TABLE gs_config.$table ADD COLUMN encrypted_data_key TEXT NULL', 'SELECT 1');
PREPARE nacos_migration FROM @ddl;
EXECUTE nacos_migration;
DEALLOCATE PREPARE nacos_migration;
UPDATE gs_config.$table SET encrypted_data_key = '' WHERE encrypted_data_key IS NULL;
ALTER TABLE gs_config.$table MODIFY COLUMN encrypted_data_key TEXT NOT NULL COMMENT '密钥';
"@
        Invoke-Compose @('exec', '-T', '-e', 'MYSQL_PWD=Password123', 'mysql', 'mysql', '-uroot', 'gs_config', '-e', $migrationSql)
    }

    Write-Step '启动 Nacos'
    Invoke-Compose @('up', '-d', 'nacos')
    Write-Host '等待 Nacos :8848 ...'
    Wait-TcpPort 'Nacos' 8848

    $nacosUrl = 'http://127.0.0.1:8848'
    $nacosReady = $false
    $nacosWatch = [Diagnostics.Stopwatch]::StartNew()
    while (-not $nacosReady -and $nacosWatch.Elapsed.TotalSeconds -lt 300) {
        try {
            $health = Invoke-RestMethod -Uri "$nacosUrl/nacos/v1/console/health/readiness" -TimeoutSec 5
            $nacosReady = $health.status -eq 'UP' -or $health.status -eq 'OK' -or $health -eq 'UP' -or $health -eq 'OK'
        } catch { }
        if (-not $nacosReady) { Start-Sleep -Seconds 3 }
    }
    if (-not $nacosReady) { throw 'Nacos 端口已打开，但尚未就绪。请检查 docker compose logs nacos mysql。' }

    Write-Step '导入仓库公共配置并初始化 MinIO 桶'
    $nacosToken = Get-NacosToken $nacosUrl
    $connectionConfig = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'goodskill-common-connection.yml')
    $connectionConfig = $connectionConfig -replace '(?m)^  endpoint: http://localhost:19000\r?$', '  endpoint: http://minio:9000'
    if ($connectionConfig -notmatch '(?m)^  endpoint: http://minio:9000\r?$') {
        throw 'goodskill-common-connection.yml 中缺少可转换的 minio.endpoint 配置，无法从容器访问 MinIO。'
    }
    Publish-NacosConfig $nacosUrl $nacosToken 'goodskill-common-connection.yml' $connectionConfig
    $commonConfig = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'goodskill-common.yml')
    Publish-NacosConfig $nacosUrl $nacosToken 'goodskill-common.yml' $commonConfig

    $minioReady = $false
    $minioWatch = [Diagnostics.Stopwatch]::StartNew()
    while (-not $minioReady -and $minioWatch.Elapsed.TotalSeconds -lt 180) {
        try {
            $null = Invoke-WebRequest -Uri 'http://127.0.0.1:19000/minio/health/ready' -TimeoutSec 5
            $minioReady = $true
        } catch { Start-Sleep -Seconds 3 }
    }
    if (-not $minioReady) { throw 'MinIO 未就绪。请查看 docker compose logs minio。' }
    Write-Step '构建 Gateway 和业务服务镜像'
    foreach ($service in @('auth', 'common', 'order', 'seckill', 'web', 'gateway')) {
        Write-Host "构建 $service 镜像..."
        Invoke-Compose @('build', $service)
    }
    if ($script:enableAi) {
        Write-Host '构建 AI 镜像...'
        Invoke-AiCompose @('build', 'ai')
    } else {
        Invoke-AiCompose @('stop', 'ai')
    }

    Write-Step '启动 Gateway 和业务服务'
    Invoke-Compose @('up', '-d', 'auth', 'common', 'order', 'seckill', 'web', 'gateway')
    if ($script:enableAi) {
        Invoke-AiCompose @('up', '-d', 'ai')
    }

    Write-Step '等待应用健康并验证演示账号'
    $nacosToken = Get-NacosToken $nacosUrl
    foreach ($service in @('goodskill-auth', 'goodskill-common', 'goodskill-order', 'goodskill-seckill', 'goodskill-web', 'goodskill-gateway')) {
        Wait-NacosService $nacosUrl $nacosToken $service
    }
    if ($script:enableAi) {
        Wait-NacosService $nacosUrl $nacosToken 'goodskill-ai'
    }
    $gatewayHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/actuator/health' -TimeoutSec 15
    if ($gatewayHealth.status -ne 'UP') { throw 'Gateway Actuator health 未返回 UP。' }
    $loginBody = @{ username = 'admin123'; password = 'aa123456' } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:18080/api/auth/login' `
        -ContentType 'application/json' -Body $loginBody -TimeoutSec 20
    if ($login.code -ne 0 -or -not $login.data) {
        throw '仓库演示账号登录检查失败（admin123 / aa123456）。请查看 auth、mysql、nacos 日志。'
    }

    Write-Step '启动本地管理界面'
    $node = (Get-Command node).Source
    $viteCli = Join-Path $uiRoot 'node_modules/vite/bin/vite.js'
    $stdout = Join-Path $logRoot 'frontend.out.log'
    $stderr = Join-Path $logRoot 'frontend.err.log'
    $viteArgs = '"{0}" --configLoader runner --host localhost --strictPort' -f $viteCli
    $frontend = Start-Process -FilePath $node -ArgumentList $viteArgs `
        -WorkingDirectory $uiRoot -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
        -PassThru -WindowStyle Hidden
    Set-Content -LiteralPath $pidFile -Value $frontend.Id -Encoding ascii
    Start-Sleep -Seconds 2
    $frontend.Refresh()
    if ($frontend.HasExited) { throw "前端进程立即退出。请查看 $stdout 和 $stderr。" }
    $null = Invoke-WebRequest -Uri 'http://localhost:5173/' -TimeoutSec 20

    Write-Host "`n演示已启动： http://localhost:5173" -ForegroundColor Green
    Write-Host 'Gateway：   http://localhost:18080'
    Write-Host '演示账号： admin123 / aa123456'
    Write-Host '停止服务： .\stop-demo.ps1（保留数据库和对象存储数据）'
} catch {
    if (Test-Path -LiteralPath $pidFile) {
        $failedFrontendPid = 0
        if ([int]::TryParse((Get-Content -Raw -LiteralPath $pidFile), [ref]$failedFrontendPid)) {
            Stop-Process -Id $failedFrontendPid -Force -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
    Write-Warning '若部分容器已启动，可运行 .\stop-demo.ps1 停止它们；数据卷会保留。'
    Write-Error $_
    exit 1
}
