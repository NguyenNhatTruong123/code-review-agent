# code-review-web

React and Vite frontend for account access, public GitHub or pasted-code review, personal rules, rule sets, review history, and findings. It runs on port `8000` and proxies `/api` to `http://localhost:8080` during development.

```bash
npm install
npm run dev
```

Use `npm run build` for a production bundle, `npm test` for unit tests, and `npm run coverage` for a coverage report.

Prettier is the source of truth for frontend JavaScript and JSX formatting. Run `npm run format` to format source, test, and Vite/Vitest configuration files, or run `npm run format:check` to validate formatting without changing files.
