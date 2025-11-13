# Version Management Guide

This project uses semantic versioning in the format `x.x.x` (Major.Minor.Patch).

## Version Storage

The current version is stored in **`version.txt`** at the project root.

## Automatic Version Increment

### Option 1: GitHub Actions (Recommended)

When you push to `main`, `master`, or `develop` branches, a GitHub Actions workflow automatically:
1. Reads the current version from `version.txt`
2. Increments the patch version (3rd number)
3. Commits the version bump
4. Creates a release tag

**No manual action needed** – just push your code!

### Option 2: Local Git Hooks

For local development, you can set up git hooks to auto-increment version after pushing:

#### Setup (Run once):

**Linux/Mac:**
```bash
bash setup-git-hooks.sh
```

**Windows:**
```bash
setup-git-hooks.bat
```

After setup, the version will auto-increment each time you push.

### Option 3: Manual Increment

To manually increment the patch version:

**Linux/Mac:**
```bash
bash scripts/increment-version.sh
```

**Windows:**
```bash
scripts\increment-version.bat
```

## Version Format

- **Major (1st number)**: Major feature releases or breaking changes
- **Minor (2nd number)**: Minor features or non-breaking enhancements
- **Patch (3rd number)**: Bugfixes, auto-incremented on push

## Incrementing Major/Minor Versions

To manually change Major or Minor versions:

1. Edit `version.txt` directly
   ```
   1.2.0  →  2.0.0   (for major release)
   1.2.5  →  1.3.0   (for minor release)
   ```

2. Commit and push:
   ```bash
   git add version.txt
   git commit -m "chore: bump version to 2.0.0"
   git push
   ```

The next push will then auto-increment from 2.0.0 to 2.0.1.

## Integration with Gradle

To use the version in your Gradle builds, add this to `settings.gradle.kts`:

```kotlin
val versionFile = File(rootDir, "version.txt")
val appVersion = versionFile.readText().trim()
rootProject.ext["app_version"] = appVersion
```

Then in `app/build.gradle.kts`:

```kotlin
version = rootProject.ext["app_version"]
```

## Viewing Releases

All releases are tagged and available on GitHub:
```
https://github.com/YOUR_ORG/YOUR_REPO/releases
```

Each tagged version corresponds to a version bump after a push.

## Workflow Summary

| Action | Result |
|--------|--------|
| Push to `main/master/develop` | Patch version auto-incremented |
| Manual edit `version.txt` | Custom version for next cycle |
| Create release tag | GitHub tracks version history |

## Notes

- The GitHub Actions workflow requires push permissions. Ensure your repository settings allow it.
- Windows git hooks may require special configuration depending on your git client.
- If you prefer more control, use manual incrementing and skip the automatic workflow.
