## Change Type

- [ ] Bug fix (fix)
- [ ] New feature (feat)
- [ ] Documentation (docs)
- [ ] Refactoring (refactor)
- [ ] Other

## Description

Briefly describe the changes and motivation.

## Testing

- [ ] `mvn test` passes locally
- [ ] Core functionality manually verified
- [ ] New features have test coverage

## Checklist

- [ ] Database indexes: new queries have index coverage
- [ ] Workspace isolation: Service layer queries filter by workspace_id
- [ ] Error handling: uses ErrorCode enum
- [ ] Log format: pure JSON format, UTC timezone
- [ ] Config security: no hardcoded passwords / API keys
- [ ] XSS protection: v-html uses DOMPurify.sanitize

## Related Issue

Closes #

## Screenshots (if applicable)

<!-- Paste screenshots of UI changes -->
