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


### 4. Reasearch using Github and OpenAI key.
Code using GITHUB_TOKEN and OPENAI_API_KEY to connect source code with model and AI feature.
Research API key can create and using normal or not.


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


### 9. Consistent frontend JavaScript and JSX formatting
All frontend `.js` and `.jsx` source, test, and configuration files under `code-review-web` must use Prettier as the single source of truth, with the committed settings `printWidth: 100`, `tabWidth: 2`, `useTabs: false`, `singleQuote: true`, `semi: true`, `trailingComma: es5`, `bracketSpacing: true`, `arrowParens: avoid`, and `endOfLine: lf`.

Formatting must use two-space indentation, one statement per line, readable wrapping for long JavaScript expressions and JSX props, and clear JSX nesting without changing semantic HTML, accessibility attributes, API contracts, application logic, routing, state behavior, or UI flow. Generated files, dependencies, coverage output, and build artifacts must remain unformatted, and the frontend must provide `npm run format` and `npm run format:check` scripts. The format check must be runnable as part of the standard frontend verification workflow, and no secrets, tokens, internal prompts, or sensitive data may be introduced.
