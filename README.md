# AI Code Review Agent

An early-stage code review assistant project. The current repository contains the application scaffold for a React web client, a Spring Boot API, and a Java module reserved for AI review functionality. Repository analysis, rule management, and live AI review are not implemented yet.

## Project Structure

| Path | Purpose |
|---|---|
| `code-review-web/` | React and Vite web frontend, served locally on port `8000`. |
| `code-review-api/` | Spring Boot REST API, served locally on port `8080`. |
| `code-review-ai/` | Java module for future code analysis and AI integration. |
| `.docs/` | Product and engineering requirements used to guide implementation and verify generated or hand-written code. |
| `usage-prompts/` | Archive of prompts that have been used for this project, so they can be reviewed and reused. |

### Engineering Documents

The `.docs/` folder contains:

- [`api-spec.md`](.docs/api-spec.md): API design, validation, authorization, response, error, and asynchronous processing requirements.
- [`coding-rules.md`](.docs/coding-rules.md): General coding conventions and module-specific implementation rules.
- [`security-rules.md`](.docs/security-rules.md): Security requirements for authentication, GitHub repository access, source code handling, AI inputs, and data protection.

Read the relevant documents before implementing or reviewing code. Keep them aligned with the product requirements and the actual project structure.

### Prompt Archive

Store prompts used to generate, modify, or review project artifacts in `usage-prompts/`. Keep each prompt as a readable text or Markdown file with a descriptive filename. This folder is an archive for traceability and reuse; it is not runtime application configuration.

## Prerequisites

- Java 17
- Maven 3.9 or later
- Node.js 18 or later and npm

## Build the Java Modules

From the repository root:

```bash
mvn clean verify
```

## Run the API

From the repository root:

```bash
mvn -pl code-review-api -am spring-boot:run
```

The API listens on `http://localhost:8080` by default. Available scaffold endpoints:

- `GET http://localhost:8080/api/health`
- `GET http://localhost:8080/api/greeting`

## Run the Web Frontend

In a separate terminal:

```bash
cd code-review-web
npm install
npm run dev
```

Open `http://localhost:8000`. The Vite development proxy forwards `/api` requests to the API at `http://localhost:8080`.

## Current Limitations

The current scaffold does not yet provide repository analysis, code paste review, rule or rule-set management, authentication, database-backed history, file upload, or a live AI provider integration. The `code-review-ai` module is currently a placeholder and does not require an AI API key.
