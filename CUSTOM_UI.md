# Custom UI notes

This repo is CloudStream plus a custom UI. `.github/workflows/custom_release.yml` builds a signed APK and
publishes it as the `pre-release` release. The app's built-in updater reads that release, so installed copies
update themselves and keep the custom UI.

* **Push to `main`** builds and publishes your own changes. Markdown-only pushes do not build.
* **Actions tab > Run workflow** first merges the newest official CloudStream into `main`, then builds.
  There is no daily schedule, the merge only happens when you start it.

If the merge opens an issue called "CloudStream update needs a manual merge", the official change overlaps
with a line we changed. Resolve it using the hook tables below, then run the workflow again.

Branding: the app calls itself "CloudStream Custom" (`app/src/prerelease/res/values/strings.xml`,
`versionNameSuffix = "-custom"` in `app/build.gradle.kts`). The package stays
`com.lagradost.cloudstream3.prerelease` so new APKs install over old ones.

## Rules of thumb for changes

* The phone, TV and emulator main layouts share one view binding (`ActivityMainBinding`, the TV layout is
  bound with `ActivityMainBinding.bind`). **Any id added to `activity_main.xml` must also exist in
  `activity_main_tv.xml`**, and the same goes for the library and search layouts, or TV and emulator crash with
  "Missing required view with ID".
* Themes are `*Mode` styles overlaid on `AppTheme` by `CommonActivity.loadThemes`. A theme only has to set the
  attributes it changes. Never hard code a colour in a layout, use a `?attr/...`.
* The settings screens are Compose (`shared/`). They take colours from `CloudStreamThemeMode`, not from the XML
  theme, so every theme needs a Compose scheme too (see Themes).

## New files (never conflict)

| File | What it does |
| --- | --- |
| `ui/UiPrefs.kt` | Per-profile options: search provider, home style, tiles, focus style, provider switcher, floating nav |
| `ui/FloatingNav.kt` | Phone nav bar that floats over the page (see below) |
| `ui/StartupTimer.kt` | Optional launch timing toast (Settings > Layout > Startup timing): window, plugins, home rows, hero |
| `app/src/main/baseline-prof.txt` | Baseline profile so a fresh install starts compiled (needs `androidx.profileinstaller` in `app/build.gradle.kts`) |
| `ui/home/HomeBrowse.kt` + `layout/browse_sheet.xml`, `browse_chip.xml` | Home category tiles and the browse sheet |
| `ui/result/ProviderSwitcher.kt` | Result page dropdown to open the same title on another provider |
| `ui/search/TopCropImageView.kt` | Banner image view that crops toward the top, used by `search_result_grid*.xml` |
| `utils/CustomCollections.kt` | Custom named collections: storage, picker, create/rename/delete dialogs |
| `drawable/card_focus_outline.xml` | Accent outline for focused cards |
| `drawable/nav_pill_bg.xml`, `nav_fade.xml` | Nav pill background, fade used when the nav bar is not floating |
| `drawable/glass_backdrop*.xml` | Window gradients for the three glass themes |
| `.github/workflows/custom_release.yml` | Build, sign and publish, optional upstream merge |

## Features and where they live

### Floating nav bar (phone, portrait)
`ui/FloatingNav.kt`, called from `MainActivity.updateNavBar` (`configure`), the nav pill inset listener
(`setSystemBottom`) and `onCreate` (`attach`). When on, `nav_host_fragment` is constrained to the bottom of the
screen instead of the top of the pill, so pages draw behind it. A throttled scan after layouts:
* adds bottom padding (and turns off `clipToPadding`) on vertical `RecyclerView`, `ScrollView`,
  `NestedScrollView` and `ComposeView` that reach the bottom of the page,
* adds bottom margin to children of a `FrameLayout` or `CoordinatorLayout` with `gravity=bottom`
  (FABs, bottom bars).

Everything is undone in landscape, on pages without the nav bar, or when Settings > Layout > Floating nav bar
is Off. If a page has a bottom element hidden behind the pill, it is probably not a `gravity=bottom` child.

### Custom collections
`utils/CustomCollections.kt` keeps a JSON list per account under the key `custom_collections_v1`.
* `LibraryViewModel.reloadPages` appends one page per collection after the normal library lists.
* The bookmark dialog (`ResultFragmentPhone`, `ResultFragmentTv`) has a last row "Add to collection..." that
  opens `showCollectionPicker`. `ResultViewModel2.collectionItem()` builds the stored snapshot.
* `fragment_library.xml` and `fragment_library_tv.xml` have the `library_collections` button (create, rename,
  delete).

### Themes
Android side: `res/values/colors.xml` (palette), `styles.xml` (`*Mode` styles), `array.xml` plus the four
`values-b+*/array.xml` files (`themes_names` and `themes_names_values` must have the same length in every
language file), `CommonActivity.loadThemes` (key to style).
Compose side: `CloudStreamThemeMode` in `shared/.../theme/Theme.kt`, schemes in `Scheme.kt`,
key mapping in `ColorPreference.kt` (`perfToMode`). Without an entry there the settings screens fall back to Dark.

Added themes: Liquid Glass (`Glass`), Aurora Glass (`GlassAurora`), Sunset Glass (`GlassSunset`), Ocean,
Ember, Crimson, Graphite.

To add a plain dark theme: seven colours in `colors.xml`, a `*Mode` style, a key in `loadThemes`, two array
entries (every language), an enum entry and a `solidScheme(...)` line, a `perfToMode` line.

### Liquid Glass attributes
Glass makes `primaryBlackBackground` and `iconGrayBackground` transparent so the window gradient shows. Anything
that used them as a text, tint or dialog colour breaks, so purpose specific attributes exist (`values/attrs.xml`).
Their defaults point at the old attributes, so other themes look unchanged.

| Attribute | Use |
| --- | --- |
| `glassWindow` | Page background in dialogs and loaded screens |
| `glassDialogSurface` | Dialogs, popup menus, snackbars |
| `glassSheetSurface` | Bottom sheets |
| `glassBar` | Opaque bars on busy pages (extensions bottom bar, top bars) |
| `fillColor`, `onFillColor`, `fillStroke` | Filled and selected buttons, tab and rail highlights |
| `onWhiteColor` | Text drawn on a filled (white or light) surface |

The glass colour variants only override the backdrop and `glassDialogSurface`, `glassSheetSurface`, `glassBar`
(`parent="GlassMode"`).
Compose: `LocalTransparentScreens` (set for all glass modes) makes `Scaffold` transparent; the glass scheme
keeps an opaque `background` because dialogs and button text use it.

### Startup speed
A freshly installed APK runs uncompiled code until Android optimizes it, which made the first launches
laggy compared with an app installed for days. `baseline-prof.txt` marks the app's code for compile at
install. If the build ever fails in `compileReleaseArtProfile`, delete that file, nothing else depends on it.
Home loads the rows first, the hero titles start 500 ms later in the background.
Turn on Startup timing to see where a slow launch spends its time.

### Search
Banner search with a single provider (`UiPrefs.searchProvider`), live search while typing, on-screen keyboard on
TV and emulator (`SearchFragment.setupTvKeyboard`). Banner cards use fixed heights computed from the screen
(`bannerSpan`, `bannerHeightPx`), not measured widths, because the first layout pass reports wrong widths.
Voice search is the original mic button (`voice_search`).

## Small hooks in official files (where merge conflicts can happen)

| File | Hook |
| --- | --- |
| `app/build.gradle.kts` | Update repo build config fields, `-custom` version suffix |
| `utils/InAppUpdater.kt` | Repo owner and name from the build config |
| `CommonActivity.kt` | Extra theme keys in `loadThemes` |
| `MainActivity.kt` | Nav pill (height, margin, insets), label mode, start tab, `nav_fade`, `FloatingNav` calls |
| `res/layout/activity_main.xml`, `activity_main_tv.xml` | `nav_fade` view (both files!) |
| `ui/settings/SettingsUI.kt`, `SettingsUIScreen.kt` | Profile layout group: search provider, home style, tiles, focus, provider switcher, floating nav, subtitle language, nav labels, start tab |
| `ui/search/SearchFragment.kt` | Banner search, live search, TV keyboard |
| `ui/search/SearchAdaptor.kt`, `SearchResultBuilder.kt`, `SearchViewModel.kt` | `forceTitle`, `fixedCoverHeight`, focus lift, `saveToHistory` |
| `res/layout/search_result_grid.xml`, `search_result_grid_expanded.xml` | `TopCropImageView` |
| `ui/home/HomeParentItemAdapterPreview.kt`, `HomeViewModel.kt` | Cinematic hero, browse tiles, rows posted first, hero loads in the background with a timeout |
| `res/layout/fragment_home_head.xml`, `fragment_home_head_tv.xml` | `home_browse_holder` view |
| `ui/result/ResultFragmentPhone.kt`, `ResultFragmentTv.kt` | `ProviderSwitcher.bind`, related list swipe, clickable tags, collections row in the bookmark dialog |
| `ui/result/ResultViewModel2.kt` | `collectionItem()` |
| `ui/library/LibraryViewModel.kt`, `LibraryFragment.kt` | Collection pages, `library_collections` button |
| `res/layout/fragment_library.xml`, `fragment_library_tv.xml` | `library_collections` button, glass tab text colour |
| `ui/player/GeneratorPlayer.kt` | `showMirrorsDialogue` groups sources by resolution |
| `utils/AppContextUtils.kt` | `sortSubs` puts the preferred language first |
| `utils/UIHelper.kt` | `populateChips` optional click callback |
| `utils/SnackbarHelper.kt` | Snackbar tint uses `glassDialogSurface` |
| `shared/.../settings/PreferenceScreen.kt`, `PreferenceGroupHeader.kt` | Card style for settings |
| `shared/.../theme/Theme.kt`, `Scheme.kt`, `ColorPreference.kt`, `mihon/material/Scaffold.kt` | Theme modes and transparent glass screens |
| `res/values/styles.xml`, `attrs.xml`, `colors.xml`, `array.xml`, `strings.xml` | Themes, glass attributes, strings |
| `res/layout/fragment_plugins.xml`, `fragment_extensions.xml`, `fragment_result.xml` | Glass attribute swaps |

## Signing

Builds are signed with a fixed key so new versions install over old ones. The key is stored in the
repository secrets `SIGNING_KEYSTORE_BASE64` and `SIGNING_PASSWORD`, never in the repo.
If the key is replaced, installed apps cannot update and must be reinstalled once.
Keep a backup of the key file and password somewhere safe.
