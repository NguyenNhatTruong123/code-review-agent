### 1. Upgrade backend by fixing complie error.
There are a lot of complie errors (missing lib, wrong calls,..) in backend (package ai and api) must be fixed and return no error any more.

The backend should be run normal after fixing.


### 2. Adding comment code for project.
There are no any comment code (or javadoc) for any file of code in project.
It should have rule of writing comment on coding-rules.md and should make comment for code follow that.


### 3. Java source formatting
All `.java` files in every module, including test sources, must use readable and consistent block formatting.

- Put each statement on its own line and use braces for all control-flow blocks, including `if`, `else`, `for`, and `while`.
- Use blank lines to separate logical sections within methods and classes.
- Wrap long expressions and method calls so they remain easy to read.
- Follow the existing project's Java style and keep formatting consistent across modules.
- Validate Java changes with the relevant Maven tests.
- Do not add secrets, tokens, internal prompts, or sensitive data to source code or comments.


### 4. GitHub and AI provider credentials
The application uses `GITHUB_TOKEN` and the configured AI provider credential to access source code and AI capabilities. Credentials are supplied through runtime environment configuration and are never exposed to the browser or committed to the repository.


### 5. Branch selector in New review
The branch selector must match the width, height, typography, spacing, and interaction style of other dropdowns in the interface on desktop and mobile.

- After a public repository is inspected, users can select from its available branches.
- The default branch is clearly identified and is used when the user does not select another ref.
- Users can manually enter a valid branch, tag, or commit ref.
- Long branch names remain readable, and the control has an accessible label and supports keyboard navigation.
- Loading, empty branch lists, and repository inspection errors have clear states without preventing the user from using the default branch or entering a ref manually.


### 6. Delete reviews from Recent reviews
Users must be able to delete a review they own from Recent reviews through a clearly labeled action that is separate from opening the review.

- Deletion requires confirmation that identifies the review and explains that its findings and feedback are removed with it.
- The API must enforce review ownership and return the same not-found behavior for missing and foreign reviews.
- Queued and running reviews must not be deleted; users must cancel the review before deletion.
- A successful deletion removes the review, findings, and stored feedback together without orphaned data.
- The review list, count, pagination, and any selected review detail update without a browser reload after deletion.
- Failed delete requests must leave the review visible and show an actionable error.


### 7. Rerun existing reviews
Users must be able to start a new review from an existing repository or pasted-code review without re-entering its source.

- Users can choose the exact rule snapshot stored with the original review, even if its rules or rule set have changed or been deleted.
- Users can instead select an enabled rule set they own; the new review uses a fresh snapshot of that set.
- The rerun flow shows the pinned repository commit/ref or pasted file that will be reviewed before submission.
- Each rerun creates an independent review with a new ID, status, timestamps, findings, and rule snapshot; the original review remains unchanged.
- The API must enforce ownership and return actionable errors when source data, the original snapshot, or a selected rule set is unavailable.
- Review history groups an original review with its reruns, which users can expand and distinguish by their rerun titles and timestamps.
- Selecting an original review group with reruns expands or collapses its rerun list, while a separate action opens the original review details.


### 8. Consistent single-line form controls
Single-line text inputs, select controls, and equivalent pickers must use the same height across authentication, review, rule, rule-set, and filter forms. Textareas, checkboxes, and radio controls retain dimensions appropriate to their interactions.

Field labels and their supporting text must not cause neighboring form controls to become vertically misaligned.


### 9. AI instruction suggestions for rules
Users can request an AI-generated draft instruction from a rule name and description while creating or editing a rule. The draft is reviewable and editable before the user explicitly applies it; manual instruction entry remains available when suggestions cannot be generated.


### 10. Required-field indicators on creation forms
Account registration, review, rule, and rule-set creation forms must visibly mark every required field with a red asterisk, while optional fields remain unmarked. Indicators must reflect source-specific and conditional requirements, including the required rule selection for a rule set. Each form must explain the symbol and preserve accessible required semantics that match client and server validation.


### 11. AI-backed review execution
When the AI provider is configured, every selected rule that applies to a source file must be evaluated by AI. AI-generated findings are retained as AI-sourced results for the UI. Literal matching remains a supplemental deterministic check; it must not prevent AI evaluation. If AI is unavailable, the review must state that AI evaluation was skipped while retaining any deterministic findings.


### 12. OpenRouter AI review integration
Code review and rule-instruction suggestions must use the configured OpenRouter-compatible AI provider and model through server-side requests. The default free-model configuration must select from currently available free provider endpoints. AI review output must be structured so it can be validated and displayed by the application. Review details must show how many files received a successful AI provider response, while unavailable or failed AI evaluation must produce a clear, actionable result.


### 13. AI provider service availability
The backend must register one shared AI provider service during application startup so review execution and rule-instruction suggestions can both access the configured provider. Missing or invalid provider settings must result in a clear configuration error rather than a missing dependency error.


### 14. Consistent frontend JavaScript and JSX formatting
All frontend `.js` and `.jsx` source, test, and configuration files under `code-review-web` must use Prettier as the single source of truth, with the committed settings `printWidth: 100`, `tabWidth: 2`, `useTabs: false`, `singleQuote: true`, `semi: true`, `trailingComma: es5`, `bracketSpacing: true`, `arrowParens: avoid`, and `endOfLine: lf`.

Formatting must use two-space indentation, one statement per line, readable wrapping for long JavaScript expressions and JSX props, and clear JSX nesting without changing semantic HTML, accessibility attributes, API contracts, application logic, routing, state behavior, or UI flow. Generated files, dependencies, coverage output, and build artifacts must remain unformatted, and the frontend must provide `npm run format` and `npm run format:check` scripts. The format check must be runnable as part of the standard frontend verification workflow, and no secrets, tokens, internal prompts, or sensitive data may be introduced.


### 15. AI review provenance and in-progress findings
Review details must refresh findings while a review is running so users can see findings as individual source files complete. The interface must clearly distinguish AI-generated findings from deterministic literal-match findings and display the count of files that received a successful AI provider response. A successful AI response that contains no supported violations must remain distinguishable from an unavailable or failed AI evaluation.


### 16. Rule selection for reviews
When creating a review, users must be able to select exactly one enabled rule set or choose one or more enabled rules directly from their own rule list. The selected rules must be captured as an immutable review snapshot, and ownership, enabled status, duplicate IDs, and empty selections must be validated by the API.
