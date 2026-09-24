# AI Code Review Agent

A Java and React application for reviewing public GitHub repositories or pasted source code against user-selected coding rules. Reviews are tied to a Git commit and a snapshot of the rules used, so later rule changes do not alter prior results.

The application includes deterministic literal-match rules that work without an AI key. Rules without a literal match use the configured OpenRouter-compatible AI provider. Each new account receives a starter rule set containing Java `System.out`, JavaScript `console.log`, and `TODO` checks.

## Project Structure

| Path | Purpose |
|---|---|
| `code-review-web/` | React and Vite frontend. |
| `code-review-api/` | Spring Boot API, session authentication, GitHub retrieval, persistence, and review orchestration. |
| `code-review-ai/` | Deterministic rule matching and optional AI provider adapter. |
| `.docs/` | Requirements used to implement and verify code. |
| `usage-prompts/` | Archive of prompts previously used for this project. |
| `AI_Code_Review_Agent_BRD_SRS.md` | Business and software requirements. |

Read `.docs/api-spec.md`, `.docs/coding-rules.md`, and `.docs/security-rules.md` before changing implementation.

## Prerequisites

- Java 17
- Maven 3.9 or later
- Node.js 22 or later and npm
- Internet access from the API process to GitHub for repository reviews

## Configuration

The API runs on port `8080` and uses a local H2 file database (`./code-review-data`) by default. It creates the database on first start. The AI integration uses Spring AI's OpenAI-compatible client to call the configured OpenRouter chat-completions endpoint. Set environment variables as needed:

| Variable | Purpose |
|---|---|
| `OPENROUTER_API_KEY` | API key mapped to `spring.ai.openai.api-key`; enables semantic review and AI rule-instruction suggestions. Omit it for deterministic rules only. |
| `OPENROUTER_MODEL` | Model mapped to `spring.ai.openai.chat.options.model`; defaults to `openrouter/free`. |
| `OPENROUTER_BASE_URL` | OpenRouter chat-completions endpoint mapped to `spring.ai.openai.base-url`; defaults to `https://openrouter.ai/api/v1/chat/completions`. |
| `GITHUB_TOKEN` | Optional token for higher GitHub API rate limits. Only public repositories are accepted. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Override the H2 connection. |
| `SESSION_COOKIE_SECURE` | Set to `true` when serving through HTTPS in a deployed environment. |

The corresponding Spring AI configuration is in `code-review-api/src/main/resources/application.yml`:

```yaml
spring:
	ai:
		openai:
			base-url: ${OPENROUTER_BASE_URL:https://openrouter.ai/api/v1/chat/completions}
			api-key: ${OPENROUTER_API_KEY:}
			chat:
				options:
					model: ${OPENROUTER_MODEL:openrouter/free}
```

Input limits are also defined there: pasted source is limited to 100,000 characters, archives to 20 MB, supported source files to 300, each file to 200 KB, and total source content to 5 MB by default. Oversized repositories fail with a visible error rather than producing an incomplete clean report.

Source code submitted for semantic review is sent to the configured AI provider. Do not enable semantic rules for code that your organization prohibits sending to that provider.

## Run Locally

In the repository root, package the Java modules and start the API:

```bash
mvn -pl code-review-api -am package -DskipTests
java -jar code-review-api/target/code-review-api-0.0.1-SNAPSHOT.jar
```

In a second terminal, start the frontend:

```bash
cd code-review-web
npm install
npm run dev
```

Open `http://localhost:8000`. Register an account with a password of at least 12 characters, then sign in. The frontend uses the Vite proxy to reach the API at `http://localhost:8080`.

To review a public repository, enter a URL such as `https://github.com/owner/repo`, optionally select a branch or commit, then choose either one enabled rule set or one or more enabled individual rules. The API records an immutable snapshot of the selected rules before downloading the archive, so later edits do not alter that review. Reviews run in the background and show findings as files complete. For pasted code, select the language explicitly or use a recognized file extension.

## API Overview

All application endpoints except registration, login, CSRF initialization, and health require an authenticated session. Mutating requests require the CSRF token returned by `GET /api/v1/auth/csrf` in the `X-XSRF-TOKEN` header.

| Route | Purpose |
|---|---|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` | Account and session. |
| `GET /api/v1/github/inspect?url=...` | Validate a public repository and list up to 100 branch names. |
| `POST /api/v1/reviews/repository`, `POST /api/v1/reviews/paste` | Start a review; return `202` and a review ID. |
| `GET /api/v1/reviews?page=0&size=50`, `GET /api/v1/reviews/{id}` | Paginated history and review details. |
| `GET /api/v1/reviews/{id}/findings` | Paginated findings with severity, rule, and file filters. |
| `POST /api/v1/reviews/{id}/cancel` | Cancel a queued or running review. |
| `POST /api/v1/reviews/{id}/findings/{findingId}/feedback` | Mark a finding helpful, irrelevant, or a false positive. |
| `GET/POST/PUT/DELETE /api/v1/rules` and `/api/v1/rule-sets` | Manage personal rules and rule sets. |

The client can send an optional `Idempotency-Key` header when creating a review to prevent duplicate submissions. A key reused with different input returns a conflict.

Review creation requests must provide exactly one rule selection: `ruleSetId` for an enabled rule set, or `ruleIds` containing one or more enabled rules owned by the authenticated user. The selected rules are stored as the review snapshot; the API rejects rules owned by another user or disabled rules.

When rerunning a review, users can reuse the original snapshot, choose an enabled current rule set, or select enabled individual rules. Each rerun stores its own immutable snapshot.

## Tests and Coverage

The repository includes unit tests for rule matching, repository archive filtering, rule snapshots, review orchestration, and API ownership behavior. JaCoCo creates coverage reports at `code-review-ai/target/site/jacoco/index.html` and `code-review-api/target/site/jacoco/index.html` when Maven verification is run:

```bash
mvn clean verify
```

The frontend build command is:

```bash
cd code-review-web
npm run build
```

Frontend unit tests and a V8 coverage report can be run with `npm run coverage` from `code-review-web/`.
Maven and Vitest are configured to fail verification when instruction/statement coverage is below 80% in their respective modules.

These commands are provided for local verification. They were not run as part of the code generation request.

## Current Scope

The application accepts public `github.com` repositories and a defined set of text source file extensions. It does not review private repositories, pull requests, dependencies, or runtime behavior, and it never executes downloaded code. Processing limits and GitHub API rate limits can prevent very large repositories from being reviewed with default settings.
