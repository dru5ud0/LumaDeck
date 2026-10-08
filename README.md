# LumaDeck

### One screen. Your sources. No walled garden.

LumaDeck is a source-driven Android TV media hub for people who want their living-room screen back. It brings discovery, personal libraries, playback, and external app launchers into one remote-friendly interface. Anti-subscription-stack, anti-platform-lock-in, high-seas energy; user-controlled infrastructure. The app does **not** ship movies, shows, streams, provider credentials, or a subscription service.

This is an independent, experimental derivative of [NuvioTV](https://github.com/NuvioMedia/NuvioTV), not an official release or a complete rebrand of its Android package. The source is public under [GPL-3.0](LICENSE).

## Feature map

The features below are present in the source tree. Availability depends on the build flavor, device capabilities, configured providers, and whether a given service returns usable data. This is a development snapshot, not a promise that every path has been validated on every TV.

### Interface and discovery

| Capability | Implementation |
| --- | --- |
| Remote-first TV UI | Jetpack Compose/Compose for TV navigation, focus handling, glass-style panels, multiple home layouts, and theme controls. |
| Curated home | Continue Watching followed by Popular Series, Popular Movies, Popular Anime Shows, and Popular Anime Movies. Curated rows are derived from Cinemeta catalogs when available. |
| Detail pages | Metadata, episodes, cast, trailers, ratings, and a **More like this** row with optional TMDB/Trakt enrichment. |
| Library | Saved titles, watched state, watch progress, collections/folders, and search/discover flows. |
| Profiles | Profile picker plus profile-scoped preferences and credentials. |
| TV Home integration | Android HOME intent support and a Continue Watching channel. The user/device must select LumaDeck as the default launcher; installing it alone does not replace Android TV Home. |
| External apps | SmartTube, Deezer, and Moonlight shortcuts launch their separately installed apps. They are not embedded or bundled. |

### Sources and local media

| Capability | Implementation |
| --- | --- |
| Addon ecosystem | Stremio-compatible addon manifests, catalogs, metadata, and stream sources can be added by the user. Addon availability and safety are outside this repo's control. |
| Debrid/cloud | Optional TorBox, Real-Debrid, and Premiumize integration paths; TorBox and Premiumize cloud-library code is included. These require the user's own accounts and configuration. |
| Local library | Jellyfin client for browsing and playing files hosted on a laptop or server on the same reachable network. The Jellyfin server must stay online while it is used. |
| Direct playback | Media3/ExoPlayer pipeline, optional mpv engine, external-player handoff, subtitle selection/timing/style, stream switching, and playback recovery logic. |
| Torrent transport | TorrServer native libraries and torrent playback code are included for user-supplied sources. This repo does not provide torrent indexes or content. |

### Playback and personalization

- Resume and Continue Watching, watched-state tracking, next-episode logic, autoplay controls, and a still-watching prompt.
- Skip-intro/skip-outro UI backed by external marker providers where matching timestamps exist; no marker means no automatic skip.
- Subtitle fetching, local subtitle cache, ASS/SSA rendering options, audio-track selection, audio delay, aspect ratio, and playback diagnostics.
- CRT Lite and CRT Full video effects. Full mode applies a curved-screen shader, rounded mask, scanlines/vignette, and caps its output at 480 vertical pixels before display scaling. Rendering support varies by player engine and hardware.
- Advanced maintenance actions for clearing temporary image/subtitle/Continue Watching caches and restarting the app. Clearing cache is not the same as deleting accounts or library data.

### Data portability and sync

- User-selected Stremio JSON export import for library items, watch progress, watched items, and addon URLs. Import is best-effort; it is **not** a live Stremio account mirror.
- Optional QR/account sync backed by a configured Supabase service; profile, library, progress, collection, addon, and selected settings sync paths exist in source.
- Optional Trakt and Simkl tracking/sync integrations.
- Local data is profile-scoped where implemented. Do not place exports, access tokens, addon configuration URLs, or real account screenshots in Git.

## The de-slop stack

The goal is a TV that opens to **your** library and tools, not a vendor's ad shelf. LumaDeck is one layer of that system, not a magic Android debloater:

| Layer | What we do | Boundary |
| --- | --- | --- |
| Home | Use LumaDeck's HOME activity, restrained navigation, curated catalog rows, and Continue Watching as the daily TV entry point. | The device must allow choosing a default Home app. System screens and vendor overlays remain outside the app. |
| Apps | Launch installed SmartTube, Deezer, and Moonlight from the same TV navigation. | They remain separate apps with their own updates, permissions, and accounts. |
| Media | Prefer a user-managed Jellyfin library, user-selected addons, and optional cloud/debrid providers over one forced catalog. | Providers may still require accounts, internet, or subscriptions. |
| Data | Keep profiles, watch progress, collections, and imports portable; make sync optional and configurable. | The current QR/account path needs a working backend; it is not automatically self-hosted. |
| Performance | Offer cache-clear and restart controls, player settings, and TV-focused focus/navigation behavior. | Cache clearing is manual in this snapshot; it cannot fix hardware limits or a failing service. |
| Look | Minimal glass-style surfaces and optional CRT playback effects instead of a crowded platform feed. | CRT Full affects video output; it does not change the TV's physical resolution or operating system. |

See the [Android TV de-slop playbook](docs/ANDROID_DESLOP.md) for the exact app/device/service split, a reversible setup path, and the remaining engineering work.

### What happens when the TV turns on?

- **Android/Onn:** after LumaDeck is selected as the default Home app, Android can open it whenever the system resolves Home, including after startup on devices that permit it. The boot receiver itself only refreshes the TV channel.
- **Roku TV + Android stick:** set Roku TV's power-on destination to the stick's HDMI input (or last-used input), then configure LumaDeck as Home on the stick. The Roku TV is the display; the Android stick runs the app.
- **Roku TV alone:** a native Roku app is possible, but it is a separate, currently limited project and cannot replace Roku Home like an Android launcher. A laptop-over-HDMI fallback also exists, but it requires the laptop to stay on.

## Screenshots

Anime catalog and home navigation:

![Anime catalog with the TV navigation bar](docs/screenshots/anime-catalog.png)

Settings and account screen:

![Settings and account screen](docs/screenshots/settings.png)

These screenshots were selected to avoid exposing private watch history, credentials, or device identifiers.

## Technical layout

| Layer | Main components |
| --- | --- |
| UI | Kotlin, Jetpack Compose, Compose for TV, Navigation Compose |
| State/DI | ViewModels, Coroutines/Flow, Hilt, DataStore |
| Network/data | Retrofit, OkHttp, Moshi, Gson, Supabase/Ktor clients |
| Playback | AndroidX Media3/ExoPlayer, mpv, FFmpeg decoder integration, libass/ASS rendering, native TorrServer |
| Images/effects | Coil, OpenGL/Media3 shader effects, Haze blur, Lottie |
| Modules | app, baselineprofile, ffmpeg-decoder-downmix |

Useful entry points: [app manifest](app/src/main/AndroidManifest.xml), [main TV activity](app/src/main/java/com/nuvio/tv/MainActivity.kt), [home catalog pipeline](app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelCatalogPipeline.kt), [player controller](app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt), [Stremio importer](app/src/main/java/com/nuvio/tv/data/migration/StremioExportImporter.kt), and [Gradle build configuration](app/build.gradle.kts).

The Android namespace and full-flavor application ID are still com.nuvio.tv. Treat this as a fork with its own repository identity, not as an independently packaged app. The full flavor enables plugins and in-app updates; the Play Store flavor disables those features. The full flavor's update metadata still targets upstream releases, so do not assume its updater delivers LumaDeck builds.

## Build

Requirements: Android Studio, a current Android SDK, and the Android/NDK components requested by Gradle. The project declares minSdk 24, compileSdk 36, and targetSdk 36. Android TV/Google TV is the intended interface.

1. Clone this repository and open it in Android Studio.
2. Put optional local service settings in ignored local.dev.properties or local.properties; use [local.example.properties](local.example.properties) as a template. Never commit real values.
3. Build the full debug APK:

   ```bash
   ./gradlew :app:assembleFullDebug
   ```

   On Windows, run `gradlew.bat :app:assembleFullDebug` instead.

4. Sideload the APK to your Android TV device. Add only the services and sources you use. To use LumaDeck as Home, select it in the device's default-launcher flow; device vendors may restrict this.

The debug build uses the Android debug signing key. Release signing requires your own keystore. Optional account login/sync will not work without a reachable, correctly configured backend. A TMDB key and some provider client IDs are optional inputs, not credentials supplied by this repo. Some upstream defaults still reference Nuvio-hosted endpoints and should be reviewed before distributing a custom build.

## Scope and status

This repository contains source and selected public UI screenshots. It intentionally excludes APKs, signing keys, service secrets, account exports, logs, private screenshots, local server URLs, and the separate Roku preview. The Roku TV prototype is **not** an Android build and is not feature-parity with this app.

LumaDeck does not bypass DRM, provide copyrighted video/audio content, guarantee access to any catalog, or make a third-party service free. Use sources, files, and services you are authorized to access. The anti-walled-garden stance is about control of the interface, the library, and the choice of providers—not a claim of rights over someone else's work.

## Credits and license

Based on [NuvioTV](https://github.com/NuvioMedia/NuvioTV), with additional project-specific modifications. Licensed under the [GNU GPL v3](LICENSE); retain the upstream notices and any other attributions in the source tree. Not affiliated with NuvioTV's maintainers, streaming providers, addon authors, or the brands visible in screenshots.
