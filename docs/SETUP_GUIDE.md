# LumaDeck setup and integration guide

This guide describes a reproducible Android TV setup, not a preconfigured account. The public repository intentionally contains no service keys, personalized addon manifests, passwords, or private media URLs. Features that depend on third-party services can change independently of this code.

## Recommended configuration

Start small and add only what solves a specific need. The suggested stack for this fork is:

| Component | Recommendation | Purpose | Required? |
| --- | --- | --- | --- |
| Cinemeta | Keep the built-in default addon | Catalog and title metadata used by the curated home rows | Recommended for the current home layout |
| OpenSubtitles v3 | Keep the built-in default addon | Subtitle results where available | Optional |
| Jellyfin | Use for your own local video files | Browse and play media on a laptop or always-on server | Optional |
| TorBox | Preferred connected cloud/debrid service for this build | Device-code sign-in, supported direct-link resolution, and cloud-library browsing | Optional; separate account/plan may be needed |
| AIOStreams | Use when you need to aggregate multiple Stremio-compatible addons | One configurable addon manifest with filtering and sorting | Optional |
| TMDB | Enable when you want richer details and the built-in More Like This row | Metadata and recommendations | Optional API key at build time |
| Trakt **or** Simkl | Pick one primary tracking service if you use cross-app history | Watch-state sync/scrobbling | Optional |
| SmartTube, Deezer, Moonlight | Install separately if you want their TV shortcuts | External apps launched from LumaDeck navigation | Optional |

The project ships with Cinemeta and OpenSubtitles v3 as default addon URLs in [AddonPreferences](../app/src/main/java/com/nuvio/tv/data/local/AddonPreferences.kt). The popular-series/movie/anime home rows are composed from Cinemeta in the [home catalog pipeline](../app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelCatalogPipeline.kt). Adding many more catalog addons is usually counterproductive if your goal is a short home screen.

For a first run, configure in this order: verify the default catalogs; play one known-good local or authorized stream; connect Jellyfin if you have personal files; connect TorBox only if you need its cloud library or supported link resolution; add AIOStreams only if you need to combine other addons. Turn on optional metadata, tracking, and playback automation after basic playback works. This order makes failures easier to isolate.

### Addons versus plugins

LumaDeck exposes two different extension systems:

- **Addons** are Stremio-compatible HTTP manifests that can supply catalog, metadata, stream, or subtitle resources. [Stremio's protocol documentation](https://stremio.github.io/stremio-addon-sdk/protocol.html) defines those resource types. The recommendation above is about this system.
- **Plugins** are a separate in-app repository/scraper feature available in the full build. They can have different trust and maintenance risks. This guide does **not** recommend installing an arbitrary third-party scraper repository. Verify its author, source, permissions, and update history before adding it.

Neither kind of extension is a guarantee that a specific title has a playable, authorized stream.

## 1. Build a clean APK

Open the repository in Android Studio and let it install the SDK/NDK versions requested by Gradle. Use the full debug flavor:

~~~powershell
.\gradlew.bat :app:assembleFullDebug
~~~

Android Studio usually writes the SDK location to ignored local.properties. Other optional build inputs also belong in ignored local.properties or local.dev.properties; see [local.example.properties](../local.example.properties). For example, this code reads TMDB_API_KEY from local.properties at build time for the [TMDB service](../app/src/main/java/com/nuvio/tv/core/tmdb/TmdbMetadataService.kt). Setting only the in-app TMDB toggle is not enough if the APK was built without a working key.

Do not copy keys from chats or screenshots into this public repository. BuildConfig values end up in the APK and should not be treated as unextractable secrets; use a key appropriate for a client app and follow the provider's terms. TMDB's [API documentation](https://developer.themoviedb.org/docs/getting-started) explains key registration, and its [FAQ](https://developer.themoviedb.org/docs/faq) covers noncommercial use and attribution.

The debug build signs with the Android debug key. It may conflict with a differently signed app using the same application ID. Back up data and understand the package/signature relationship before replacing an existing installation.

## 2. Install and select the Home experience

1. Install the APK on an Android TV/Google TV device and open it normally first.
2. Complete a basic navigation test with the remote: Home, Search, Library, Settings, Back.
3. If the device exposes a default Home/launcher choice, select LumaDeck. The [manifest](../app/src/main/AndroidManifest.xml) declares a HOME activity but does not force Android to use it.
4. Test pressing Home, rebooting, and returning from an external app. Keep the stock launcher enabled so you can recover through Android Settings.

The BOOT_COMPLETED receiver in this source only schedules Android TV channel refresh. It does not force-start the app UI. See the [deployment guide](ANDROID_DESLOP.md) for the Onn, Roku HDMI, laptop HDMI, and native Roku routes. Root access or firmware changes are not required for the documented setup.

## 3. Set up addons

Open **Settings > Content & Discovery > Add-ons**. Confirm that Cinemeta and OpenSubtitles v3 are present before adding anything else. If the curated home is empty, first verify Cinemeta is enabled and reachable; adding more stream providers will not repair a missing catalog.

Use one addon for each job unless you intentionally want overlapping results:

| Need | First choice | Check before adding another |
| --- | --- | --- |
| Curated home metadata/catalogs | Built-in Cinemeta | Can you see Popular Series and Popular Movies? |
| Subtitles | Built-in OpenSubtitles v3 | Does the selected title have subtitle results? |
| Multiple user-chosen stream addons | AIOStreams aggregator | Is its hosted/self-hosted instance reachable, and are those sources enabled? |
| Personal local files | Jellyfin integration, not a Stremio addon | Can Jellyfin play the file in its own browser UI? |
| Detail-page recommendations | Built-in TMDB More Like This | Was the APK built with a TMDB key, and is the toggle on? |

Avoid installing a catalog through both AIOStreams and directly unless duplicate rows are intended. A catalog addon supplies browsing results; a stream addon supplies playable candidates for a selected title. One does not replace the other.

For AIOStreams, use the [maintainer's documentation](https://docs.aiostreams.viren070.me/configuration/setup/) to choose a trusted hosted instance or self-host one, then configure only the addon sources you actually want. Its purpose is aggregation, filtering, de-duplication, and formatting—not a second copy of the same catalogs everywhere. Install the resulting **manifest URL** in LumaDeck's Add-ons screen and confirm it appears in the installed list. If you want a minimal home, keep extra AIOStreams catalog rows disabled unless you deliberately want them.

A configured manifest URL may encode credentials or access to your private AIOStreams instance. Treat it as a secret: do not paste it into issues, README files, screenshots, or public chats. If you self-host AIOStreams on your laptop, that laptop must remain online for its addon to answer requests. A hosted instance removes that laptop dependency but adds a third-party trust dependency.

If a provider offers only a configuration webpage, finish configuration there and use the generated manifest endpoint—not the /configure page—as the installed addon URL. Addon results and streams are returned by the provider; LumaDeck does not create them.

## 4. Connect TorBox

TorBox is the preferred optional debrid/cloud provider **for this codebase** because the visible integration supports device-code authorization, direct-link resolution, and cloud-library browsing. It is not required for Jellyfin or basic catalog browsing, and this is not a claim that it is the cheapest or best service for everyone.

1. Create or use your own TorBox account and check the provider's current terms and plan capabilities.
2. In LumaDeck, open **Settings > Integrations > Connected Services**, select **TorBox**, and complete the TV device-code sign-in. The [provider definition](../app/src/main/java/com/nuvio/tv/core/debrid/DebridProvider.kt) identifies the supported capabilities; the [settings screen](../app/src/main/java/com/nuvio/tv/ui/screens/settings/DebridSettingsScreen.kt) exposes the connection.
3. Enable direct-link resolution only if your configured stream addons supply inputs the resolver understands. Enable Cloud Library if you want to browse files already present in your TorBox account.
4. Verify the account reports connected, then test with a file you are authorized to access. A successful account link does not guarantee every addon stream can be resolved.

The app's connected-service credential and any credential you enter into AIOStreams are **separate**. AIOStreams may require its own TorBox authorization to query or format results; LumaDeck's TorBox connection is for the app's direct resolver/cloud-library paths. Configure both only when you need both, and trust the AIOStreams host before entering a key there. TorBox's [API documentation](https://api.torbox.app/docs) lists its device-authorization and account endpoints.

Premiumize is another visible provider. Real-Debrid code is present but its provider is marked not visible in this UI, so this guide does not present it as a primary setup path. Avoid configuring several overlapping resolver services until one works reliably.

## 5. Add a local Jellyfin library

Jellyfin is the most direct path for personal video files on a laptop. Install Jellyfin Server on the machine holding the files, create a media library, and confirm its browser UI can play a file. Jellyfin's [library guide](https://jellyfin.org/docs/general/server/libraries/) covers adding folders.

On the Android TV device, open **Local** in LumaDeck and enter the server's reachable LAN address, the Jellyfin username, and password. The [client implementation](../app/src/main/java/com/nuvio/tv/data/jellyfin/JellyfinLocalLibraryClient.kt) fetches videos and builds playback URLs. The laptop/server must stay powered on and reachable whenever you use Local. A local address normally uses Jellyfin's HTTP port 8096; [Jellyfin's networking guide](https://jellyfin.org/docs/general/post-install/networking/) explains that port and warns against direct public port forwarding.

If Local cannot connect, test the server URL from another device on the same Wi-Fi, verify the laptop is awake, and check the local firewall. Keep the server on the LAN or use a secure remote-access design; do not publish an unprotected personal library to the internet.

## 6. Enable metadata, recommendations, and playback helpers

- **More Like This:** the app already has a TMDB-backed row on detail pages. Build with a valid TMDB API key, then enable TMDB and More Like This in **Settings > Integrations > TMDB**. A separate third-party More Like This addon is not required for this built-in row. Missing TMDB IDs or API responses can still leave it empty.
- **Skip intro/endings:** enable the player control. It depends on matching external timestamp data, including IntroDB and anime-specific providers; it will not work for every title or episode. AnimeSkip may need its own client ID in **Settings > Integrations > AnimeSkip**.
- **Autoplay/next episode:** configure in Playback settings, then test on one series before relying on it. Stream availability, provider ordering, and file metadata affect the result.
- **Tracking:** choose Trakt or Simkl as a primary service if you want history outside this app. Duplicate tracking services can create confusing state.
- **Stremio migration:** use **Settings > Advanced > Import Stremio export** with a user-selected JSON export. The importer handles library items, progress, watched items, and addon URLs on a best-effort basis; it does not sign into Stremio or continuously mirror the account.
- **Account sync:** QR login requires a working configured Supabase backend. A public source checkout alone does not provide one.

## 7. Optional apps and Roku TV

SmartTube, Deezer, and Moonlight are **shortcuts**, not embedded modules. Install each app independently on Android TV if you want its navigation entry. Moonlight can connect to a game-streaming host such as [Apollo](https://github.com/ClassicOldSong/Apollo) on a PC; that PC must be running, and network/hardware latency still applies. The [Moonlight Android project](https://github.com/moonlight-stream/moonlight-android) documents its client.

On a Roku TV, the feature-complete route is to display the Android TV stick through HDMI and set Roku's power-on destination to that input. The Android APK cannot be installed natively on Roku. The separate Roku preview is not included in this repository and does not provide these Android integrations; see the [deployment guide](ANDROID_DESLOP.md).

## 8. Troubleshooting checklist

| Symptom | First checks |
| --- | --- |
| Home rows missing | Confirm Cinemeta is enabled/reachable; refresh addons; verify network access. |
| AIOStreams installed but no stream results | Confirm the installed URL ends at a manifest endpoint, the instance is online, selected sources are enabled, and the addon declares a stream resource for that content type. |
| TorBox connected but no playable link | Verify resolver toggle, provider/account status, and whether the selected addon result is a supported input. Test a known authorized file. |
| More Like This empty | Confirm TMDB_API_KEY was present **when the APK was built**, TMDB and More Like This are enabled, and the title has a usable TMDB ID. |
| Jellyfin Local cannot connect | Confirm laptop/server awake, LAN address and port, firewall, credentials, and playback in Jellyfin's own web UI. |
| Skip button absent | No matching marker may exist; check episode IDs, provider availability, and feature toggles. |
| App does not open after boot | Recheck default Home selection and vendor restrictions. The boot receiver does not launch the UI. |
| Roku TV shows its own Home | Set Roku TV Power on to the Android stick's HDMI input. This does not replace Roku Home or make the Android APK native to Roku. |
| Slow/crashing playback | Test a smaller known-good file, a different player engine, wired/strong Wi-Fi, and the manual cache/restart controls. Cache clearing cannot solve unsupported codecs or insufficient hardware. |

Use only media, addons, and services you are authorized to access. The recommendations here describe integration compatibility and maintainability, not access to any particular copyrighted catalog.
