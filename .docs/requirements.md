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