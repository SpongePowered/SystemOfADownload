# Development Tools

## Tool Management with Mise

This project uses [Mise](https://mise.jdx.dev/) to manage development tools and their versions. Mise ensures that all developers use the same tool versions, avoiding "works on my machine" issues.

### Setup

Install mise if you haven't already:

```bash
curl https://mise.run | sh
```

Or on macOS with Homebrew:

```bash
brew install mise
```

Then run mise to install all required tools for this project:

```bash
mise install
```

This will install all tools defined in `mise.toml`, including:
- Go 1.25
- golang-migrate (with postgres tags)
- golangci-lint 2
- mockery (latest)
- oapi-codegen (latest)
- sqlc (latest)

### Tool Versions

All tool versions are defined in `mise.toml`. To update a tool version, edit that file and run `mise install` again.

### Running Tools

Mise-managed tools can be run directly (mise adds them to your PATH), or you can use `mise exec` to ensure you're using the project-specified version:

```bash
# Direct execution (relies on mise shell integration)
go version
mockery --version

# Explicit execution with mise
mise exec go -- version
mise exec mockery -- --version
```

### Code Generation

See [AGENTS.md](./AGENTS.md) for the standard operating procedures for code generation.
