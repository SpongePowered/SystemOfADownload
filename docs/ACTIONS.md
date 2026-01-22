# Pinned GitHub Actions SHAs

This document explains how to find and update GitHub Actions to pinned commit SHAs.

## Why Pin SHAs?

Using commit SHAs instead of version tags (like `@v4`) provides:
- **Security**: Exact control over what code runs in your CI/CD
- **Reproducibility**: Guaranteed same version across all runs
- **Auditability**: Clear commit history of what versions were used
- **Protection**: Prevents accidental major version changes

## Current Pinned Actions

### Release Workflow (.github/workflows/release-please.yml)
- `googleapis/release-please-action@16a9c90856f42705d54a6fda1823352bdc62cf38` # v4.4.0
- `actions/checkout@8e8c483db84b4bee98b60c0593521ed34d9990e8` # v6.0.1
- `actions/setup-go@7a3fe6cf4cb3a834922a1244abfce67bcef6a0c5` # v6.2.0

### CI Workflow (.github/workflows/ci.yml)
- `actions/checkout@8e8c483db84b4bee98b60c0593521ed34d9990e8` # v6.0.1
- `actions/setup-go@7a3fe6cf4cb3a834922a1244abfce67bcef6a0c5` # v6.2.0
- `codecov/codecov-action@0561704f0f02c16a585d4c7555e57fa2e44cf909` # v5.5.2
- `golangci/golangci-lint-action@1e7e51e771db61008b38414a730f564565cf7c20` # v9.2.0
- `jdx/mise-action@6d1e696aa24c1aa1bcc1adea0212707c71ab78a8` # v3.6.1

## How to Find SHAs for Actions

### Method 1: Using curl (command line)

```bash
# Get the latest release tag
REPO="owner/repo"  # e.g., "actions/checkout"
TAG=$(curl -s https://api.github.com/repos/$REPO/releases/latest | grep -o '"tag_name":"[^"]*' | cut -d'"' -f4)

# Get the commit SHA for that tag
SHA=$(curl -s https://api.github.com/repos/$REPO/git/refs/tags/$TAG | grep -o '"sha":"[^"]*' | cut -d'"' -f4 | head -c 40)

echo "$REPO@$TAG -> $REPO@$SHA"
```

### Method 2: Using the GitHub Web Interface

1. Navigate to `https://github.com/owner/repo/releases/latest`
2. Find the release tag (e.g., `v4.1.1`)
3. Click on the tag to see the commit
4. The commit SHA will be in the URL: `https://github.com/owner/repo/commit/COMMIT_SHA`
5. Use the full 40-character SHA in your workflow

### Method 3: Using provided script

Run the `update-actions.sh` script to check all current actions:

```bash
bash update-actions.sh
```

This script will output the latest versions for all pinned actions.

## Updating Actions

To update an action to a newer version:

1. Find the desired version number or latest release using one of the methods above
2. Get the commit SHA for that tag
3. Update the workflow file:

   **Before:**
   ```yaml
   - uses: actions/checkout@v4
   ```

   **After:**
   ```yaml
   - uses: actions/checkout@8e8c483db84b4bee98b60c0593521ed34d9990e8 # v6.0.1
   ```

4. The comment after the SHA indicates the version tag, for easy reference

## Recommended Update Cadence

- **Weekly**: Check for security updates to actions (especially checkout, setup-go)
- **Monthly**: Review all pinned actions for new releases
- **As needed**: Update if critical bugs are fixed or security vulnerabilities are found

## GitHub Action Version References

When looking for specific actions, these are commonly used in Go projects:

- **[actions/checkout](https://github.com/actions/checkout)** - Clone repository
- **[actions/setup-go](https://github.com/actions/setup-go)** - Set up Go environment
- **[googleapis/release-please-action](https://github.com/googleapis/release-please)** - Automated releases
- **[golangci/golangci-lint-action](https://github.com/golangci/golangci-lint-action)** - Lint Go code
- **[codecov/codecov-action](https://github.com/codecov/codecov-action)** - Coverage reporting
- **[jdx/mise-action](https://github.com/jdx/mise-action)** - Mise tool installation

## Related Files

- `.github/workflows/release-please.yml` - Release workflow with pinned SHAs
- `.github/workflows/ci.yml` - CI workflow with pinned SHAs
- `update-actions.sh` - Script to check for new action versions
