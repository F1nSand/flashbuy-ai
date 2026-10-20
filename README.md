# flashbuy-ai

flashbuy-ai 是一个面向本地演示的高并发秒杀与订单系统项目，基于 [techa03/goodsKill](https://github.com/techa03/goodsKill)。当前仓库提供可运行的管理演示和秒杀模拟；历史方案与代码模块不代表已具备完整生产交易能力。

## 本地演示

Windows 演示使用 Docker Compose 启动 MySQL、Redis、RabbitMQ、ZooKeeper、Kafka、Elasticsearch、MongoDB、MinIO、Nacos 和后端服务，管理界面运行于本机。需要 Docker Desktop、JDK 21、Maven、Node.js 20.19+ 或 22.12+、npm，并确保演示所需端口可用。

在仓库根目录的 PowerShell 中运行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\start-demo.ps1
```

启动完成后打开 <http://localhost:5173>。本地演示账号为 `admin123` / `aa123456`，仅供本地使用。界面提供控制台、用户和商品管理、秒杀模拟；当前只开放已验收的同步锁策略，其他策略保持禁用。停止演示：

```powershell
.\stop-demo.ps1
```

停止会保留数据库和对象存储数据卷。完整步骤、依赖和故障提示见 [Windows 演示脚本](start-demo.ps1) 与 [前端说明](goodskill-ui/README.md)。

## 可选 AI 配置

AI 聊天可连接 DeepSeek API，不配置时其他本地演示功能仍可使用。首次配置时仅在根目录 `.env` 不存在的情况下复制示例：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

在 `.env` 中填写 `DEEPSEEK_API_KEY`，并按需调整 `DEEPSEEK_BASE_URL` 或 `DEEPSEEK_MODEL`；Key 留空时 AI 服务不会启动。脚本只将该 Key 提供给 AI 后端，不放进前端构建。示例文件中的支付、文件上传等生产选项保持关闭；仓库内演示账号和中间件密码仅用于本机演示。

## 技术栈

- 后端：Java 21、Spring Boot 3、Spring Cloud 2023、MyBatis-Plus、Sa-Token
- 前端：Vue 3、Vite、npm
- 本地依赖：Docker Compose、MySQL、Redis、RabbitMQ、ZooKeeper、Kafka、Elasticsearch、MongoDB、MinIO、Nacos
- AI：Spring AI 与 DeepSeek OpenAI 兼容 API（可选）

## 生产边界

生产能力尚未完成验收，当前版本不能据此上线。支付、商品文件上传和交易受理默认关闭；支付流程、对象存储、外部中间件、多实例恢复、容量和浏览器买家流程仍需逐项验证。生产源码和设计文档不构成已验收能力证明。

## 文档

- [历史技术快速开始](docs/project/quickstart.md)
- [项目测试说明](docs/project/test.md)
- [生产交易架构契约](docs/architecture/production-commerce-contract.md)
- [生产 API 草案](docs/api/production-api-draft.md)
- [生产验收门槛](docs/operations/production-readiness-gates.md)
- [生产管理员初始化](docs/operations/production-admin-bootstrap.md)
- [环境变量示例](.env.example)

## 来源与许可

本项目源自 [techa03/goodsKill](https://github.com/techa03/goodsKill)，保留上游来源说明及 [MIT License](LICENSE)。
