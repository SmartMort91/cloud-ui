# Custom UI notes

This repo is CloudStream plus a custom UI. A scheduled workflow (`.github/workflows/custom_release.yml`)
merges the newest official CloudStream into `main` every day, builds a signed APK and publishes it as
the `pre-release` release. The app's built-in updater reads that release, so installed copies update
themselves and keep the custom UI.

If the workflow opens an issue called "CloudStream update needs a manual merge", the official change
overlaps with a line we changed. Resolve it using the table below, then run the workflow again.

## What was added (new files, never conflict)

| File | What it does |
| --- | --- |
| `ui/UiPrefs.kt` | Per-profile options (search provider, home style, tiles, focus style, provider switcher) |
| `ui/home/HomeBrowse.kt` + `layout/browse_sheet.xml`, `browse_chip.xml` | Home category tiles and the browse sheet (category + sort by rating, release date, name) |
| `ui/result/ProviderSwitcher.kt` | Dropdown on the result page to open the same title on another provider |
| `drawable/card_focus_outline.xml` | Accent outline for focused cards |
| `.github/workflows/custom_release.yml` | Daily upstream merge + signed build + release |

## Small hooks in official files (where conflicts can happen)

| File | Hook |
| --- | --- |
| `app/build.gradle.kts` | `UPDATE_REPO_OWNER` / `UPDATE_REPO_NAME` build config fields |
| `utils/InAppUpdater.kt` | Two lines: repo owner and name come from the build config |
| `CommonActivity.kt` | Six extra theme names in `loadThemes` |
| `MainActivity.kt` | Bottom bar label mode, start tab, stronger active indicator |
| `ui/settings/SettingsUI.kt` | Navigation and profile layout settings dialogs |
| `ui/search/SearchFragment.kt` | Single-provider banner search, live search, TV keyboard (`setupTvKeyboard`) |
| `ui/search/SearchAdaptor.kt`, `SearchResultBuilder.kt`, `SearchViewModel.kt` | `forceTitle`, focus lift, `saveToHistory` parameters |
| `ui/home/HomeParentItemAdapterPreview.kt` | Cinematic hero and the browse tile holder |
| `ui/result/ResultFragmentPhone.kt`, `ResultFragmentTv.kt` | One `ProviderSwitcher.bind(...)` call each |
| `res/layout/fragment_home_head.xml`, `fragment_home_head_tv.xml` | `home_browse_holder` view |
| `res/values/*.xml`, `res/xml/settings_ui.xml`, `drawable/outline_drawable.xml` | Themes, strings, settings entries, focus outline |

## Signing

Builds are signed with a fixed key so new versions install over old ones. The key is stored in the
repository secrets `SIGNING_KEYSTORE_BASE64` and `SIGNING_PASSWORD`, never in the repo.
If the key is replaced, installed apps cannot update and must be reinstalled once.
