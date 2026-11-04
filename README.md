# CloudStream Custom

A custom-styled version of [CloudStream](https://github.com/recloudstream/cloudstream), the open source
Android media player. It has a streaming-service style interface for phones, Android TV and emulator
layouts, plus extra themes and features.

> **This is an unofficial fork and is not affiliated with the CloudStream team.**
> Please do not report problems with this version to the official project.

> **⚠️ By default the app has no video sources.** Like CloudStream, it only plays what extensions
> provide. You add extensions yourself, and you are responsible for what you use. Please don't use
> extensions that host or link to copyrighted media you have no right to watch.

## Download

Get the newest APK from the **[releases page](../../releases/tag/pre-release)** and install it.
Android will ask you once to allow installing apps from your browser or file manager.

After that the app updates itself: when a new build is published you get an update popup with a list of
what changed. You never need to come back here to update.

The app installs next to the official CloudStream, it does not replace it.

## What's different from CloudStream

**Look and layout**
- Themes, including **Liquid Glass**, Aurora Glass and Sunset Glass (translucent panels), plus Ocean,
  Ember, Crimson and Graphite
- Floating navigation bar on phones (can be turned off in Settings > Layout)
- Restyled settings, home, search and title pages
- Cinematic hero on the home screen, browse tiles by category and sort

**Features**
- **Custom collections**: make your own named lists like "Horror Night" and add titles from the
  bookmark button. They show up as tabs in the Library
- Banner search that searches one provider, with an on-screen keyboard on TV and emulator layouts
- Provider switcher on the home and title pages: open the same title on another provider
- Related titles row and clickable tags on title pages
- Player sources grouped by resolution
- Preferred subtitle language that is picked automatically
- Navigation label options and a choice of start screen
- Self-updating releases

Most options are in **Settings > Layout**.

## Reporting problems

Open an [issue](../../issues) and include:
- what screen you were on and what you expected to happen
- a screenshot if it is a visual problem
- your phone or TV model and which layout you use (phone, TV or emulator)

## Building it yourself

The repository builds like CloudStream does (Android Studio or `./gradlew assemblePrereleaseDebug`).
`CUSTOM_UI.md` lists every file this version adds or changes, and is the place to start if you want to
change something or merge a newer CloudStream.

The release workflow in `.github/workflows` signs builds with a key kept in repository secrets. If you
fork this, add your own `SIGNING_KEYSTORE_BASE64` and `SIGNING_PASSWORD` secrets.

## Credits and license

All the real work belongs to the [CloudStream](https://github.com/recloudstream/cloudstream) developers
and contributors. This project only changes how it looks and adds a few features on top.

Like CloudStream, this project is licensed under the
[GNU General Public License v3.0](LICENSE). The full source is in this repository.
