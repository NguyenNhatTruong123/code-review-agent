# code-review-ai

Java module containing deterministic literal matching and an optional OpenAI adapter for semantic review. The API module validates AI candidates against the selected rule snapshot and source line map before storing findings.

Deterministic rules do not need an AI key. Configure `OPENAI_API_KEY` in the API process to use semantic rules.
