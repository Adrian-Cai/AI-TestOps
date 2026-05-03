# AI TestOps Demo

[English](README.md) | [中文](README_CN.md)

---

AI TestOps Demo 是一个面向测试设计流程的 Spring Boot + React 示例项目。系统将需求文档接入、文档解析、AI 需求抽取、测试用例草稿生成、自动校验、人工评审确认和用例导出串联成一条轻量闭环，适合用于验证 AI 辅助测试设计、需求到用例生成、Prompt 版本管理和测试资产沉淀等场景。

后端提供 REST API、数据库持久化、Apache Tika 文档解析、OpenAI-compatible 大模型调用适配和 Knife4j 接口文档；前端由 Vite + React + Ant Design 构建，生产构建产物会输出到 `src/main/resources/static`，由 Spring Boot 统一托管。

## 功能特性

- **文档接入与解析**：支持文本录入和 `md/txt/pdf/doc/docx/xls/xlsx/ppt/pptx` 文件上传解析。
- **AI 需求抽取**：从需求文档中提取结构化需求信息，支持 MOCK 和真实 OpenAI-compatible 模型联调。
- **测试用例生成**：基于需求抽取结果生成测试用例草稿。
- **校验与评审**：保存 AI 校验结果，支持草稿编辑、确认、驳回和批量确认。
- **测试资产导出**：支持正式测试用例查询，并导出 JSON 或 Excel。
- **接口文档**：集成 Knife4j，便于调试和查看 API。

## 快速入口

- 前端页面：`http://localhost:8080/`
- Knife4j 接口文档：`http://localhost:8080/doc.html`
- 启动运行说明：[docs/启动运行说明.md](docs/启动运行说明.md)
- 真实大模型联调：[docs/真实大模型联调.md](docs/真实大模型联调.md)
- Prompt 版本化与质量闭环：[docs/Prompt版本化与质量闭环.md](docs/Prompt版本化与质量闭环.md)

## 环境要求

- JDK 17 或更高版本，项目编译目标为 Java 17。
- Windows 可直接使用项目内 `mvnw.cmd`，不要求提前安装 Maven。
- Node.js 和 npm 仅在开发或重新构建前端时需要。
- MariaDB 数据库需要提前建库建表，连接信息写入项目根目录 `.env`。

## 配置

数据库、服务端口和大模型配置统一放在项目根目录 `.env`。可参考：

```powershell
Copy-Item docs\.env.example .env
```

常用配置项：

```properties
DB_URL=jdbc:mariadb://your-remote-host:3306/ai_testops?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
SERVER_PORT=8080
AI_PROVIDER=MOCK
AI_TESTOPS_UPLOAD_DIR=./data/uploads
```

默认端口是 `8080`。如果端口被占用，可以临时换端口启动：

```powershell
$env:SERVER_PORT=8081
.\mvnw.cmd spring-boot:run
```

## 启动方式

### 启动后端和已构建前端

在项目根目录执行：

```powershell
.\mvnw.cmd spring-boot:run
```

Spring Boot 会启动后端 API，并托管 `src/main/resources/static` 下的前端静态页面。

### 前端开发联调

只有需要修改前端源码时才进入 `frontend` 目录执行 npm 命令：

```powershell
cd frontend
npm install
npm run dev
```

前端开发服务默认运行在 `http://localhost:5173/`，并通过 Vite 代理访问 `http://localhost:8080` 的后端 API。

> 注意：项目根目录没有 `package.json`，不要在根目录执行 `npm run start` 或 `npm run build`。

## 构建方式

### 构建后端 Jar

```powershell
.\mvnw.cmd clean package
```

构建完成后运行：

```powershell
java -jar target/ai-testops-0.0.1-SNAPSHOT.jar
```

### 重新构建前端静态资源

```powershell
cd frontend
npm install
npm run build
cd ..
```

`frontend/vite.config.ts` 会把前端生产包输出到：

```text
src/main/resources/static
```

如果要把新的前端静态资源一起打入后端 Jar，按下面顺序执行：

```powershell
cd frontend
npm install
npm run build
cd ..
.\mvnw.cmd clean package
```

## 验证

运行后端测试：

```powershell
.\mvnw.cmd test
```

验证前端生产构建：

```powershell
cd frontend
npm run build
```

## 常见问题

- **8080 端口被占用**：停止占用端口的进程，或设置 `$env:SERVER_PORT=8081` 后重新启动。
- **数据库连接失败**：检查 `.env` 中 `DB_URL/DB_USERNAME/DB_PASSWORD`，并确认远程 MariaDB 已建表。
- **AI 返回 MOCK 内容**：默认 `AI_PROVIDER=MOCK`，需要真实模型时请配置 `AI_PROVIDER/AI_API_BASE/AI_API_KEY/AI_MODEL_NAME` 后重启。
