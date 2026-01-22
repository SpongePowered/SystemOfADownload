# AI Agent Guidelines

This document provides standard operating procedures (SOPs) for AI agents working on this codebase.

## Environment Setup

Before making any code changes, ensure the development environment is properly set up:

1. **Install tools**: Run `mise install` (or `mise i`) to install all required development tools
   - This installs Go, mockery, oapi-codegen, sqlc, and other tools defined in `mise.toml`
   - See [TOOLS.md](./TOOLS.md) for detailed information about tool management

2. **Verify installation**: Ensure all tools are available and at the correct versions

## Code Generation

This project uses Go's built-in code generation system. **Always use the standard generation workflow**:

### Standard Operating Procedure

1. Make changes to source files (OpenAPI specs, SQL queries, interface definitions, etc.)
2. Run code generation: `go generate .`
3. Verify generated files are updated correctly
4. Commit both source changes and generated code

### What Gets Generated

- **API code** (from `openapi.yaml`): Generated using oapi-codegen → `api/api.gen.go`
- **Database code** (from `db/query.sql`): Generated using sqlc → `internal/db/*.go`
- **Mocks** (from interface definitions): Generated using mockery → `*/mocks/*_mocks.go`

### Important Notes

- Never manually edit generated files (they have headers indicating they're generated)
- The `generate.go` file contains all `//go:generate` directives
- Generated code should be committed to the repository

## Testing Standards

### Test Structure

All tests should follow **table-driven test patterns** to ensure comprehensive behavior coverage:

```go
func TestFeature_Behavior(t *testing.T) {
    t.Parallel()
    
    tests := []struct {
        name      string
        input     InputType
        mockSetup func(t *testing.T, m *MockType)
        want      ExpectedType
        wantErr   error
    }{
        {
            name: "success case",
            // ... test case fields
        },
        {
            name: "error case",
            // ... test case fields
        },
    }
    
    for _, tt := range tests {
        tt := tt
        t.Run(tt.name, func(t *testing.T) {
            t.Parallel()
            
            // Test implementation
        })
    }
}
```

### Test Guidelines

- **Use table tests**: All test functions should use table-driven patterns for testing multiple behaviors
- **Test behaviors, not implementation**: Focus on what the code does, not how it does it
- **Parallel execution**: Mark tests as parallel with `t.Parallel()` for faster test runs
- **Clear test names**: Use descriptive names that explain the scenario being tested

### Mocking

- **Prefer mockery-generated mocks**: Use mockery to generate mocks from interfaces
- **Do not write mocks manually** unless absolutely necessary
- **Mock locations**: 
  - Interface-specific mocks: `internal/<package>/mocks/<interface>_mocks.go`
  - Shared mocks: `mocks/<interface>_mocks.go`
- **Regenerate mocks**: When interfaces change, run `go generate .` to update mocks

### Testing Best Practices

- Keep tests focused and isolated
- Use `github.com/google/go-cmp/cmp` for deep comparisons
- Use `github.com/stretchr/testify/mock` for mock assertions
- Test error cases as thoroughly as success cases
- Avoid test interdependencies

## Project Architecture

This project follows Domain-Driven Design (DDD) principles:

- `internal/domain`: Pure domain entities (no external dependencies)
- `internal/app`: Application services orchestrating domain logic
- `internal/httpapi`: HTTP handlers and routing
- `internal/repository`: Data access layer
- `cmd/server`: HTTP API server entrypoint
- `cmd/worker`: Background worker entrypoint

When adding new features, respect these boundaries and keep dependencies flowing inward (toward domain).

## Commit Message Guidelines

This project uses [Conventional Commits](https://www.conventionalcommits.org/) for automated releases.

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Common Types

- **feat**: New feature (bumps MINOR version)
- **fix**: Bug fix (bumps PATCH version)
- **perf**: Performance improvement (bumps PATCH version)
- **docs**: Documentation only
- **refactor**: Code refactoring
- **test**: Adding or updating tests
- **chore**: Maintenance tasks

### Breaking Changes

Add `!` after type or include `BREAKING CHANGE:` in footer to bump MAJOR version:

```
feat!: remove deprecated API endpoint
```

### Examples

```
feat: add artifact search endpoint
fix: correct version comparison logic
docs: update API documentation
test: add table tests for group service
```

See [RELEASES.md](./RELEASES.md) for complete release process documentation.

## Workflow Summary

1. `mise install` → Set up development environment
2. Make changes to source files
3. `go generate .` → Regenerate code
4. `go test ./...` → Run tests
5. Commit changes with conventional commit message (including generated code)
6. Push to main → Release-please will create/update release PR automatically
