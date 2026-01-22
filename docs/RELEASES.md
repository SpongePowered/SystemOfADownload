# Release Process

This project uses [release-please](https://github.com/googleapis/release-please) to automate version management and releases.

## How It Works

Release-please follows the [Conventional Commits](https://www.conventionalcommits.org/) specification to automatically determine version bumps and generate changelogs.

### Commit Message Format

Use the following format for your commit messages:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

#### Types

- **feat**: A new feature (triggers MINOR version bump)
- **fix**: A bug fix (triggers PATCH version bump)
- **perf**: Performance improvement (triggers PATCH version bump)
- **docs**: Documentation changes (no version bump)
- **style**: Code style changes (no version bump)
- **refactor**: Code refactoring (no version bump)
- **test**: Adding or updating tests (no version bump)
- **build**: Build system changes (no version bump)
- **ci**: CI configuration changes (no version bump)
- **chore**: Other changes (no version bump)

#### Breaking Changes

To trigger a MAJOR version bump, include `BREAKING CHANGE:` in the footer or add `!` after the type:

```
feat!: remove support for legacy API
```

or

```
feat: add new authentication system

BREAKING CHANGE: old authentication tokens are no longer supported
```

### Examples

```
feat: add group filtering endpoint

Adds a new /v1/groups/filter endpoint that allows filtering
groups by various criteria.
```

```
fix: correct artifact version sorting

Previously, artifact versions were sorted lexicographically
instead of semantically. This fix uses proper semver sorting.
```

```
docs: update installation instructions in README
```

## Release Workflow

1. **Make changes** following conventional commit format
2. **Push to main branch** (or merge PR)
3. **Release-please bot creates/updates a release PR** with:
   - Updated version number
   - Generated CHANGELOG.md
   - Version tag preparation
4. **Review the release PR** to ensure changelog is accurate
5. **Merge the release PR** to trigger:
   - Git tag creation
   - GitHub Release creation
   - Binary builds for multiple platforms
   - Asset uploads to the release

## Binaries

The release process automatically builds binaries for:

- **Server** (`server`) - Web API server
- **Worker** (`worker`) - Background worker

### Supported Platforms

- Linux (amd64, arm64)
- macOS (amd64, arm64)
- Windows (amd64)

Binaries are packaged as:
- `.tar.gz` for Linux and macOS
- `.zip` for Windows

## Manual Release (if needed)

If you need to create a release manually:

```bash
# Tag the release
git tag v1.2.3
git push origin v1.2.3

# The GitHub Actions workflow will handle building and uploading binaries
```

## Version File

The current version is tracked in `.release-please-manifest.json`. This file is automatically updated by release-please when a release is created.

## Configuration Files

- `.release-please-config.json` - Release-please configuration
- `.release-please-manifest.json` - Current version tracking
- `.github/workflows/release-please.yml` - GitHub Actions workflow

## First Release

For the first release, simply merge a PR with conventional commits. Release-please will:
1. Create a release PR bumping from `0.0.0` to the appropriate first version
2. Generate an initial CHANGELOG.md
3. Once merged, create the first tagged release with binaries
