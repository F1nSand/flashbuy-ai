# GoodSkill UI

Vue 3 + Vite 前端包含两个隔离入口：`npm run dev` 用于仓库现有本地演示；`npm run build` 生成面向真实交易 API 的生产前端。生产构建不包含演示用 localStorage 会话、模拟秒杀路由或模拟数据入口。

## 本地开发

需要 Node.js 20.19+ 或 22.12+。

```powershell
npm ci
npm run dev
```

需要修改本地 API 代理时，先复制 `.env.example` 为 `.env`，再设置 `VITE_API_TARGET`。Vite 默认运行在 `http://localhost:5173`，`/api` 请求代理到 `http://127.0.0.1:18080`。开发入口连接仓库的本地演示后端，不等于生产交易流程。不要把密码、API 密钥或其他秘密放进任何 `VITE_*` 变量，它们会进入浏览器构建环境。

## 生产前端

```powershell
npm ci
npm run build
```

产物写入 `dist/`。`npm run build:demo` 单独构建本地演示前端。生产构建依赖同源 `/api` 路由、HTTPS Cookie 会话及服务端 CSRF/XSRF 配置；静态站点需经 `Dockerfile.production` 和 `nginx.production.conf.template` 提供 SPA 回退、API 反向代理及安全响应头。反向代理的 API 上游通过 `GOODSKILL_GATEWAY_UPSTREAM` 配置。

生产路由包含公开活动目录、买家下单与订单，以及按角色显示的活动、订单/退款/发货、库存流水、审计和实时交易通知界面。只有连接已正确配置的 production Gateway 与后端时这些页面才可用；本地 demo profile 不提供生产 API。源码或静态构建通过，不代表支付、订单、实时通知、权限、数据迁移或部署已完成验收。发布前必须满足根目录生产门槛文档中的验证要求。

生产支付、图片上传默认关闭；服务端秘密必须在后端环境配置，不能写入前端 `.env` 或构建变量。详见仓库根目录 `.env.example` 与运维文档。
