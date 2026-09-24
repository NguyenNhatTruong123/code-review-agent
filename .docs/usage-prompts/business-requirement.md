# AI Code Review Agent - Business Requirement

## 1. Scope

Web application for authenticated developers to review public GitHub repositories or pasted source code against coding rules. The system is built with a Spring Boot REST API, a React frontend, and a dedicated rule-processing module. The MVP helps users detect likely code issues, review findings with explanations and suggestions, manage their own rules and rule sets, and keep a history of prior analyses.

## 2. Technology baseline

- Java 17, Spring Boot 3.x, Maven.
- Frontend: React + Vite + JavaScript/JSX.
- Backend: Spring Boot REST API with Controller/Service/Repository/DTO layers.
- Rule engine: dedicated module for deterministic rule matching and optional AI-based review integration.
- Persistence: local H2 file database by default for development and local runs.
- Authentication: session-based login with CSRF protection for mutating requests.
- Public GitHub repository review only; no private repository support in MVP.
- Source code is never executed by the application.

## 3. Business objective

The product aims to help software engineers and teams review code quickly, consistently, and with traceability. It reduces manual inspection effort by checking code against custom rules and shared standards, while keeping review results tied to a specific commit and a specific rule snapshot.

The business value includes:
- Faster code review triage before merge.
- Standardized enforcement of engineering rules and practices.
- Flexible personal rule management without requiring a central admin.
- Review traceability for audit and future reference.
- Support for both repository-based and paste-based source analysis.

## 4. Target users

### 4.1 Authenticated end users
- Software engineers
- Team leads / senior developers
- Engineering managers using code quality rules for personal or team workflows

### 4.2 Primary user goals
- Review a public GitHub repository.
- Paste code for quick analysis.
- Select a rule set relevant to the codebase.
- Understand where the issues are and why they matter.
- Save and revisit past review results.
- Create and maintain personal rules and rule groups.

## 5. User journeys

### 5.1 Review public GitHub repository
1. User signs in.
2. User enters a valid public GitHub repository URL.
3. User optionally chooses a branch or commit reference.
4. User selects a rule set and starts the review.
5. System validates the repository, resolves the target reference to an immutable commit SHA, and downloads supported source files only.
6. System runs analysis against configured rules.
7. User views results, summary, filters, and findings.
8. User can revisit the review later from history.

### 5.2 Review pasted source code
1. User signs in.
2. User pastes code or uploads a snippet in a supported language.
3. User chooses the language/file name if needed.
4. User selects a rule set and submits the review.
5. System analyzes the pasted content and returns findings tied to the pseudo file and line numbers.

### 5.3 Manage rules and rule sets
1. User creates or edits a rule with name, description, severity, language scope, and instruction.
2. User can enable or disable the rule.
3. User creates a rule set by selecting relevant rules.
4. User reuses the rule set in future reviews.
5. Historical reviews remain tied to the snapshot of the rules that were active at review time.

## 6. Functional requirements

### FR-01: User authentication and ownership
- Users must register and sign in before creating reviews, rules, or rule sets.
- All review and rule resources must be scoped to the authenticated user.
- A user must not access or modify another user’s review, rule, or rule set.

### FR-02: GitHub repository validation
- System accepts only valid public GitHub repository URLs.
- System rejects invalid, private, inaccessible, or credentialed URLs.
- System validates the repository and lists available refs/branches when applicable.
- System stores the exact commit SHA captured at review start.

### FR-03: Review creation
- User can create a review from GitHub repository input or pasted code.
- User must select a valid rule set before submitting.
- Review creation returns a review ID and initial status such as queued or running.
- The system must record source type, target ref, commit SHA (if applicable), timestamps, and configuration used.

### FR-04: Source scanning and filtering
- System filters supported text files only.
- Binary files, vendor dependencies, generated artifacts, and unsupported file types are ignored.
- System reports the number of files scanned and skipped, with reasons where available.
- System enforces configurable limits for file count, file size, repository size, and review time.

### FR-05: Rule and rule set management
- User can create, edit, enable/disable, and delete rules.
- User can create, edit, enable/disable, and delete rule sets.
- Rule sets must contain at least one enabled rule when used in a review.
- Historical review results must not change when a rule is edited after the review was created.

### FR-06: Finding generation
- Findings must reference a rule from the review snapshot.
- Each finding must contain at least: rule ID, version, severity, title, explanation, evidence, suggested fix, and file/line information when available.
- The system must avoid duplicate findings for the same rule at the same code region.
- Findings must be tied to actual code or user-pasted code, never fabricated.

### FR-07: Review results and status tracking
- Review status must reflect the workflow: queued, running, completed, completed with warnings, failed, or cancelled.
- User can open a review detail page and see progress summary and result counts.
- System must show warnings and errors clearly without exposing sensitive information or stack traces.

### FR-08: Review history and detail page
- User can view a list of previous reviews with metadata such as date, source type, repository/commit or pasted file, rule set, and status.
- User can open review detail to inspect findings and summary.
- Review detail page must remain consistent after refresh and must be based on saved results, not live assumptions.

### FR-09: Filter and navigation
- User can filter findings by severity, file, and rule.
- Results must be presented in a readable list with issue location, explanation, and remediation suggestion.
- Finding entries must display file path and line numbers when technically known.

### FR-10: Finding feedback
- User can mark a finding as helpful, irrelevant, or false positive.
- Feedback must be associated with the current user and persisted for later analytics or manual review.

### FR-11: Safe rendering and data handling
- All rendered content must be escaped or inserted with safe DOM APIs.
- Raw HTML must not be injected into the frontend from user-provided content.
- No code, rule instructions, or repository sources should be written to logs in raw form.

## 7. Business rules and constraints

- Only public GitHub repositories are supported in MVP.
- The system never executes downloaded code or builds external repositories.
- Each review uses an immutable rule snapshot and, where applicable, an immutable commit SHA.
- Code and custom rules are treated as sensitive data and must not be logged in raw form.
- AI-based review is optional and must be configured explicitly; deterministic rules remain available without an AI key.
- If GitHub is unavailable, the repository is too large, or a provider fails, the system must report the exact operational state rather than returning misleading success data.
- If analysis finds no issues, the system should show a clean result clearly; if no supported files are processed, the review should fail or warn with a clear reason.

## 8. Rule model

A rule contains the following core business data:
- Rule ID
- Name
- Description
- Category
- Severity
- Languages or scope
- Instruction or detection logic
- Enabled status
- Version
- Owner
- Creation and update timestamps

A rule set contains:
- Rule set ID
- Name
- Description
- Owner
- Enabled status
- List of selected rules and their versions

This design ensures that historical review outputs are reproducible even if a user modifies rules later.

## 9. Finding model

A finding is a concrete code issue discovered during review and includes:
- Finding ID
- Review ID
- Rule ID and rule version
- Severity
- Title
- Explanation
- Evidence
- Suggested fix
- File path
- Start/end line numbers when known
- Source type: static or AI-generated
- Status and feedback metadata

The system must not create a finding from untrusted or malformed output. Invalid AI output or bad positions must be rejected and logged as warnings rather than displayed as valid findings.

## 10. UI requirements

The UI should support the following screens:
1. Authentication and account access
2. Review creation
3. Review list
4. Review detail with summary and findings
5. Rule management
6. Rule set management

The interface must clearly communicate:
- validation errors,
- processing progress,
- warnings and failures,
- final findings, and
- historical review state.

## 11. Non-functional requirements

### Security
- Authenticate all protected endpoints.
- Enforce ownership checks for all resources.
- Validate all input server-side.
- Prevent raw HTML injection in frontend rendering.
- Protect against CSRF on mutating requests.
- Store secrets only in secure configuration, never in client-side code.

### Reliability
- Review processing should be asynchronous for long-running tasks.
- Business state should be saved and queryable even when processing continues in the background.
- Review output must be deterministic for a given commit SHA and rule snapshot.

### Performance
- Processing should be bounded by configured limits for file count, payload size, and runtime.
- Frontend filtering and listing should remain responsive even with many findings.

### Observability
- Logs must include review ID and operational status where helpful.
- Errors should be clear to users and should not leak secrets or raw source content.

## 12. Non-goals for MVP

The following are out of scope for the current release:
- Private repository access or GitHub OAuth-based enterprise integration
- Pull request or webhook-based automation
- IDE plugin or VS Code extension
- Automatic patching, commit creation, or push actions
- Security scanning or dependency vulnerability scanning as a primary feature
- Multi-tenant organization management
- Public sharing of review results or team-level collaboration features

## 13. Acceptance criteria

The MVP is considered successful when:
1. A signed-in user can review a valid public GitHub repository and view findings.
2. A signed-in user can review pasted code and receive findings based on selected rules.
3. A user can create and manage personal rules and rule sets.
4. Review history remains available and tied to stable snapshots.
5. A user cannot access another user’s data through API or UI.
6. The system reports validation errors, warnings, and operational failures clearly.
7. The application does not execute downloaded code and does not expose raw code or secrets in logs.

## 14. Summary

This project delivers an MVP AI-assisted code review platform focused on public GitHub repositories and pasted code snippets. The product combines deterministic rules with optional AI-based review, keeps review histories and snapshots, and enables users to manage their own rule sets while operating within strong ownership and security boundaries.
