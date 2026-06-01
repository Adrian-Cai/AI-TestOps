# AGENTS.md

本文件为 AI 在此仓库中工作时提供指导。

## 语言与输出

- 使用中文进行所有交流、注释和文档输出
- 保持输出简洁，避免冗余解释

## 工作流程

- 先规划再编码：动手实现前先梳理思路和方案
- 有歧义时先确认，不要猜测需求
- 完成任务前必须验证（运行构建和测试）

## 构建与测试

- 后端测试：`./mvnw test`（JUnit 5，Jacoco 覆盖率门槛 line≥50% branch≥30%）
- 前端构建：`cd frontend && npm run build`（TypeScript 类型检查 + Vite 构建）
- 前端开发服务器：`cd frontend && npm run dev`（端口 5173，API 代理到 8080）
- 完整验证需同时运行后端测试和前端构建

## 项目架构

- 后端：Spring Boot 3 + MyBatis-Plus + MariaDB，Java 17
- 前端：React 18 + TypeScript (strict) + Ant Design + Vite
- 前端构建产物输出到 `src/main/resources/static/`（`emptyOutDir: true`，每次构建会清空该目录）
- `data/uploads/` 存放上传文件，运行时生成

## 分支规范

- 分支命名：`type/ticket-description`，如 `feature/JIRA-123-login`、`bugfix/JIRA-456-null-check`

## 安全注意

- `.env` 包含数据库和 API 密钥等真实凭据，禁止将其提交到版本控制
- `.gitignore` 已配置忽略 `.env`，但历史提交中可能已包含

## 注意事项

- 前端无测试框架（无 Jest/Vitest/RTL）
- Docker 构建跳过测试（`mvn package -DskipTests`），CI 中需单独运行测试
- 配置通过环境变量注入，本地开发依赖 `.env` 文件（参考 `docs/.env.example`）
