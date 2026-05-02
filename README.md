# AI TestOps Demo

[English](README.md) | [中文](README_CN.md)

---

AI TestOps Demo backend and static frontend page, covering document ingestion and parsing, AI requirement extraction, test case draft generation, validation, manual review and confirmation, and export.

## Features

- **Document Parsing**: Support for multiple document formats ingestion and parsing
- **AI Requirement Extraction**: Intelligent extraction of key information from requirement documents
- **Test Case Generation**: Automatic generation of test case drafts
- **Validation & Review**: Automatic validation and manual review confirmation
- **Export Function**: Support for test case export

## Quick Start

- Frontend: `http://localhost:8080/`
- Knife4j API Docs: `http://localhost:8080/doc.html`
- Run Instructions: [docs/启动运行说明.md](docs/启动运行说明.md)

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

Database and LLM configurations are stored in the `.env` file in the project root. Once the remote database is set up, no SQL files are needed for startup. See [docs/启动运行说明.md](docs/启动运行说明.md) for details.

## Verify

```powershell
.\mvnw.cmd test
```
