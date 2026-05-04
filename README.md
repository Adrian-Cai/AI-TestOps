# AI TestOps

[English](README.md) | [中文](README_CN.md)

---

AI TestOps is an end-to-end **AI-assisted test design** demo built with Spring Boot 3 + React. It connects requirement document ingestion, document parsing, AI requirement extraction, test case draft generation, automated validation, manual review, and test asset export into a lightweight closed loop.

The backend is built with **Java 17 + Spring Boot 3.5.9**, uses **MyBatis-Plus** for database persistence, **Apache Tika 3.2.3** for document parsing, and provides an **OpenAI-compatible REST client** for LLM integration (works with OpenAI, Qwen, DeepSeek, and any provider that supports `/v1/chat/completions`). API documentation is powered by **Knife4j**.

The frontend is built with **React 18 + TypeScript + Ant Design 5 + Vite**. Production assets are emitted to `src/main/resources/static` and served directly by Spring Boot.

## Features

- **Document Ingestion and Parsing**: Create text documents or upload `md/txt/pdf/doc/docx/xls/xlsx/ppt/pptx` files. Apache Tika extracts and chunks the content.
- **AI Requirement Extraction**: Extract structured requirements (business rules, API list, field constraints, exception cases, risks) with either MOCK mode or a real OpenAI-compatible model.
- **Test Case Generation**: Generate test case drafts from extracted requirements, covering normal, exceptional, and boundary scenarios.
- **Prompt Versioning**: Version-controlled prompt templates with activation/deactivation per version.
- **AI Validation**: Auto-validate AI-generated content, tracking pass/fail rates across template versions.
- **Manual Review**: Edit drafts, approve, reject, or batch-approve test cases with full review records.
- **Test Asset Export**: Query formal test cases and export as JSON or Excel.
- **API Documentation**: Browse and debug all endpoints at Knife4j (`/doc.html`).

## Tech Stack

| Layer      | Technology                                    |
| ---------- | --------------------------------------------- |
| Backend    | Java 17, Spring Boot 3.5.9, Maven             |
| ORM        | MyBatis-Plus 3.5.9                            |
| Database   | MariaDB 10.11+ (H2 for tests)                 |
| Document   | Apache Tika 3.2.3                             |
| AI Client  | OpenAI-compatible HTTP (`/v1/chat/completions`) |
| API Docs   | Knife4j 4.4.0 (Swagger UI)                    |
| Frontend   | React 18, TypeScript, Ant Design 5, Vite      |

## Quick Links

- Frontend: `http://localhost:8080/`
- Knife4j API Docs: `http://localhost:8080/doc.html`
- Run Instructions: [docs/启动运行说明.md](docs/启动运行说明.md)
- Real LLM Integration: [docs/真实大模型联调.md](docs/真实大模型联调.md)
- Prompt Versioning: [docs/Prompt版本化与质量闭环.md](docs/Prompt版本化与质量闭环.md)

## Requirements

- **JDK 17+** — the project compiles for Java 17.
- **MariaDB 10.11+** — schema must be created before runtime. See `docs/sql/ai_testops_mariadb_schema.sql`.
- **Maven** — optional on Windows as `mvnw.cmd` is included.
- **Node.js + npm** — only needed when developing or rebuilding the frontend.

## Configuration

All configuration is loaded from the project root `.env` file via `java-dotenv`. Start by copying the example:

```powershell
Copy-Item docs\.env.example .env
```

### Database

```properties
DB_URL=jdbc:mariadb://your-host:3306/ai_testops?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=your_user
DB_PASSWORD=your_password
```

### AI / LLM

```properties
# MOCK mode — no API key needed, returns simulated data
AI_PROVIDER=MOCK

# Real OpenAI-compatible API (OpenAI, Qwen, DeepSeek, etc.)
AI_PROVIDER=OPENAI_COMPATIBLE
AI_API_BASE=https://api.openai.com
AI_API_KEY=sk-...
AI_MODEL_NAME=gpt-4.1-mini
AI_TEMPERATURE=0.2
AI_MAX_TOKENS=4096
AI_JSON_MODE=true
```

> `AI_JSON_MODE=true` sends `response_format: {type: "json_object"}` to the model, ensuring valid JSON output. This is compatible with most providers (OpenAI, Qwen, DeepSeek). If the provider does not support `json_object`, set it to `false`.

### Server & Uploads

```properties
SERVER_PORT=8080
AI_TESTOPS_UPLOAD_DIR=./data/uploads
UPLOAD_MAX_FILE_SIZE=50MB
```

## Run

### Backend + Built Frontend (Single Command)

```powershell
.\mvnw.cmd spring-boot:run
```

Spring Boot starts the backend API and serves the built frontend from `src/main/resources/static`.

### Frontend Development

Only run these when modifying frontend source. The Vite dev server at `http://localhost:5173/` proxies API calls to `http://localhost:8080`.

```powershell
cd frontend
npm install
npm run dev
```

> The project root has **no** `package.json`. Do not run `npm` commands from the root directory.

### Change Port

```powershell
$env:SERVER_PORT=8081
.\mvnw.cmd spring-boot:run
```

## Build

### Backend Jar

```powershell
.\mvnw.cmd clean package
java -jar target/ai-testops-0.0.1-SNAPSHOT.jar
```

### Backend Jar + Frontend Assets

```powershell
cd frontend
npm install && npm run build
cd ..
.\mvnw.cmd clean package
java -jar target/ai-testops-0.0.1-SNAPSHOT.jar
```

The frontend build output goes to `src/main/resources/static` (configured in `frontend/vite.config.ts`).

## Verify

```powershell
# Backend tests (JUnit 5 + H2 in-memory database)
.\mvnw.cmd test

# Frontend production build
cd frontend
npm run build
```

## Project Structure

```
├── src/main/java/.../aitestops/
│   ├── ai/             # AI client, services, controllers, prompt templates
│   ├── document/       # Document upload, parsing, chunking
│   ├── testcase/       # Test case draft, formal case management
│   ├── review/         # Manual review records
│   ├── export/         # JSON / Excel export
│   └── common/         # Config, enums, exceptions, utilities
├── src/main/resources/static/   # Built frontend assets
├── frontend/           # React + Vite source
├── docs/               # Documentation, SQL schema, env example
└── data/uploads/       # Uploaded file storage
```

## API Overview

| Endpoint                                          | Description                    |
| ------------------------------------------------- | ------------------------------ |
| `POST /api/ai-testops/documents/text`             | Create a text document         |
| `POST /api/ai-testops/documents/upload`           | Upload a file                  |
| `POST /api/ai-testops/documents/{id}/parse`       | Parse and chunk a document     |
| `POST /api/ai-testops/ai/requirements/extract`    | Extract structured requirements |
| `POST /api/ai-testops/testcases/generate`         | Generate test case drafts      |
| `GET  /api/ai-testops/testcases/drafts`           | List drafts                    |
| `POST /api/ai-testops/testcases/drafts/{id}/approve` | Approve a draft             |
| `GET  /api/ai-testops/export/testcases/json`      | Export test cases as JSON      |
| `GET  /api/ai-testops/export/testcases/excel`     | Export test cases as Excel     |

Full endpoint list: [docs/启动运行说明.md](docs/启动运行说明.md) §7.

## Troubleshooting

- **Port 8080 in use**: Stop the process or set `$env:SERVER_PORT=8081`.
- **Database connection fails**: Check `.env` settings and confirm MariaDB is running and migrated.
- **AI returns MOCK data**: Set `AI_PROVIDER` to a non-MOCK value and configure `AI_API_BASE`/`AI_API_KEY`/`AI_MODEL_NAME`.
- **AI 400 error on `response_format`**: Some providers only support `json_object`, not `json_schema`. This project now uses `json_object` by default when `AI_JSON_MODE=true`. If your provider doesn't support `json_object`, set `AI_JSON_MODE=false`.

---

## More Resources

- **Automation Testing Platform**: [https://autotest.wiac.xyz/](https://autotest.wiac.xyz/) — My automation testing platform portal
- **GitHub Project**: [https://github.com/acai1998/AI-TestOps](https://github.com/acai1998/AI-TestOps)
