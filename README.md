# AI TestOps Demo

AI 测试设计 Demo 后端与静态前端页面，覆盖文档接入解析、AI 需求抽取、测试用例草稿生成、校验、人工评审确认和导出。

## 快速入口

- 前端页面：`http://localhost:8080/`
- Knife4j 接口文档：`http://localhost:8080/doc.html`
- 启动运行说明：[docs/启动运行说明.md](docs/启动运行说明.md)

## 启动

```powershell
.\mvnw.cmd spring-boot:run
```

数据库和大模型配置统一写在项目根目录 `.env` 中。远程数据库已建库建表后，不需要项目内 SQL 文件参与启动。详细说明见 [docs/启动运行说明.md](docs/启动运行说明.md)。

## 验证

```powershell
.\mvnw.cmd test
```
