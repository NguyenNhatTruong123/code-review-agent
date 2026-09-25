## Summary

Describe the change and the project behavior it affects.

## Validation

- [ ] `mvn test`
- [ ] `mvn javadoc:aggregate` (when Java documentation or public API changes)
- [ ] `npm run format:check` (when frontend files change)
- [ ] `npm test` (when frontend files change)
- [ ] `npm run build` (when frontend files change)

## Review checklist

- [ ] The change preserves API contracts, authentication, ownership checks, and source provenance.
- [ ] No secrets, tokens, credentials, private source code, or internal prompts are committed.
- [ ] Documentation and tests were updated when behavior or public contracts changed.
- [ ] Generated files, dependencies, coverage output, and build artifacts are not included.

## Notes for reviewers

Mention migration, configuration, security, compatibility, or deployment considerations.
