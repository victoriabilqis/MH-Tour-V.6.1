# MH Tour — GitHub Build Ready

This package is prepared for GitHub Actions.

## Expected repository root

- `.github/workflows/build-release-signed.yml`
- `android/`
- `server/`
- `tools/`
- `.gitignore`
- `README.md`

## Build

1. Upload the extracted contents to the repository root.
2. Commit to `main`.
3. Open **Actions**.
4. Select **MH Tour APK Build** for Debug, or **MH Tour APK Release** for Release.
5. Click **Run workflow**.
6. Select `main` and run it.
7. Wait for the selected job to finish.
8. Download the artifact.

No `MH_TOUR_API_BASE_URL` repository variable is required by this Android build because `MainActivity.kt` uses the retained LiveKit Development Token Server ID.


Build CI fix: Android BuildConfig is explicitly enabled and the GitHub Actions workflow uses a valid workflow_dispatch trigger.
