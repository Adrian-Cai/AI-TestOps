# AI TestOps Demo

[English](README.md) | [中文](README_CN.md)

---

AI TestOps Demo is a Spring Boot + React sample project for AI-assisted test design workflows. It connects requirement document ingestion, document parsing, AI requirement extraction, test case draft generation, automated validation, manual review, and test case export into a lightweight end-to-end loop.

The backend provides REST APIs, database persistence, Apache Tika document parsing, OpenAI-compatible LLM integration, and Knife4j API documentation. The frontend is built with Vite, React, and Ant Design. Production frontend assets are emitted to `src/main/resources/static` and served by Spring Boot.

## Features

- **Document Ingestion and Parsing**: Create text documents or upload `md/txt/pdf/doc/docx/xls/xlsx/ppt/pptx` files for parsing.
- **AI Requirement Extraction**: Extract structured requirement data with either MOCK mode or a real OpenAI-compatible model.
- **Test Case Generation**: Generate test case drafts from extracted requirements.
- **Validation and Review**: Store AI validation results, edit drafts, approve drafts, reject drafts, and batch approve drafts.
- **Test Asset Export**: Query formal test cases and export them as JSON or Excel.
- **API Documentation**: Use Knife4j for API inspection and debugging.

## Quick Links

- Frontend: `http://localhost:8080/`
- Knife4j API Docs: `http://localhost:8080/doc.html`
- Run Instructions: [docs/启动运行说明.md](docs/启动运行说明.md)
- Real LLM Integration: [docs/真实大模型联调.md](docs/真实大模型联调.md)
- Prompt Versioning and Quality Loop: [docs/Prompt版本化与质量闭环.md](docs/Prompt版本化与质量闭环.md)

## Requirements

- JDK 17 or later. The project compiles for Java 17.
- Maven is not required globally on Windows because the repository includes `mvnw.cmd`.
- Node.js and npm are required only when developing or rebuilding the frontend.
- MariaDB must be created and migrated before runtime. Connection settings live in the project-root `.env` file.

## Configuration

Database, server port, upload, and LLM settings are loaded from the project-root `.env` file. Start from the example file:

```powershell
Copy-Item docs\.env.example .env
```

Common settings:

```properties
DB_URL=jdbc:mariadb://your-remote-host:3306/ai_testops?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
SERVER_PORT=8080
AI_PROVIDER=MOCK
AI_TESTOPS_UPLOAD_DIR=./data/uploads
```

The default port is `8080`. If that port is already in use, start with another port:

```powershell
$env:SERVER_PORT=8081
.\mvnw.cmd spring-boot:run
```

## Run

### Run Backend and Built Frontend

From the repository root:

```powershell
.\mvnw.cmd spring-boot:run
```

Spring Boot starts the backend APIs and serves the built frontend from `src/main/resources/static`.

### Frontend Development

Run npm commands only inside the `frontend` directory:

```powershell
cd frontend
npm install
npm run dev
```

The Vite dev server runs at `http://localhost:5173/` and proxies API calls to the backend at `http://localhost:8080`.

> Note: The repository root has no `package.json`. Do not run `npm run start` or `npm run build` from the root directory.

## Build

### Build Backend Jar

```powershell
.\mvnw.cmd clean package
```

Run the packaged application:

```powershell
java -jar target/ai-testops-0.0.1-SNAPSHOT.jar
```

### Rebuild Frontend Static Assets

```powershell
cd frontend
npm install
npm run build
cd ..
```

`frontend/vite.config.ts` emits the production frontend build to:

```text
src/main/resources/static
```

To include the latest frontend assets in the backend Jar, run:

```powershell
cd frontend
npm install
npm run build
cd ..
.\mvnw.cmd clean package
```

## Verify

Run backend tests:

```powershell
.\mvnw.cmd test
```

Verify the frontend production build:

```powershell
cd frontend
npm run build
```

## Troubleshooting

- **Port 8080 is already in use**: Stop the process using the port, or set `$env:SERVER_PORT=8081` before starting the app.
- **Database connection fails**: Check `DB_URL/DB_USERNAME/DB_PASSWORD` in `.env`, and confirm that the remote MariaDB schema and tables already exist.
- **AI returns MOCK content**: The default `AI_PROVIDER=MOCK` is for local testing without an API key. Configure `AI_PROVIDER/AI_API_BASE/AI_API_KEY/AI_MODEL_NAME` and restart the service to use a real model.
