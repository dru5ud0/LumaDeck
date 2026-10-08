# Android TV deployment guide

This guide describes the deployment options and system boundaries for LumaDeck. The Android app provides the TV interface; Android or Roku remains responsible for the operating system and device-level startup behavior.

## Choose the deployment route

| Display/host | What runs LumaDeck? | Power-on behavior | Status |
| --- | --- | --- | --- |
| Android TV or Google TV device, including an Onn stick | The Android APK runs directly on the stick/TV. | If the device accepts LumaDeck as default Home, Android can resolve Home to it after boot and when Home is pressed. Vendor behavior varies. | Main source tree. |
| Roku TV with an Android TV stick on HDMI | The Android APK runs on the stick; Roku TV displays that HDMI input. | Set the Roku TV's Power on setting to the stick's HDMI input or Last used TV input. The stick separately needs LumaDeck selected as its Home app. | Recommended Roku TV route for Android feature parity. |
| Roku TV with a Windows laptop on HDMI | An Android emulator can run the APK on the laptop, and HDMI displays the laptop. | Windows/emulator startup, full-screen behavior, audio routing, and remote input are separate host configuration. The laptop must stay on. | Experimental local fallback; helper scripts are not included in this public repo. |
| Roku TV without external hardware | A separate Roku SceneGraph/BrightScript app must be built and sideloaded or published. | It appears as a Roku app tile, not a replacement Roku launcher or guaranteed boot app. | A limited local preview existed; it is not included here and is not Android feature-parity. |

## Android/Onn: get from power button to your hub

1. Install a tested LumaDeck APK and confirm it opens normally before changing Home behavior. Keep a working recovery path to Android Settings.
2. On the device, select LumaDeck as the default Home/launcher **if the device offers that choice**. The [manifest](../app/src/main/AndroidManifest.xml) declares an Android HOME activity; it does not force the OS to choose it.
3. Test three separate paths: press Home, restart the stick, and return from an external app. They can behave differently on vendor firmware.
4. Leave the stock launcher installed and enabled. If LumaDeck fails, change the default Home selection back in Android Settings. Do not disable system packages or attempt root/bootloader changes just to use this setup.
5. The app's boot receiver refreshes an Android TV channel; it does **not** directly launch the app UI at BOOT_COMPLETED. Persistent launch depends on the system resolving Home to LumaDeck.

The package ID in the full flavor is still com.nuvio.tv. A future independent package ID is separate engineering work: change namespace/application ID, deep links, provider authorities, signing, update endpoints, and migration behavior together.

## Roku TV: two practical routes

### Use the Roku screen with an Android stick

Connect a compatible Android TV stick to an HDMI input and power it from a reliable supply. On the Roku TV, navigate to **Settings > System > Power > Power on** and choose that HDMI input or **Last used TV input**. Roku's [TV user guide](https://image.roku.com/c3VwcG9ydC1B/Roku-TV-User-Guide-14-5-US.pdf) documents those choices. This makes the TV show the Android stick when powered on; it does not install the APK on Roku.

Then complete the Android Home selection above on the stick. Roku's own Home button or input switching may still return to Roku UI. A Roku remote may not control the stick over HDMI; use the stick's remote, a compatible universal remote, or device-supported HDMI-CEC where available. Test control before relying on a single remote.

### Build a native Roku app

Roku apps use SceneGraph/BrightScript, not Android APKs or Media3. Roku's [developer guide](https://developer.roku.com/dev/docs/getting-started) describes the platform, and its [developer-mode guide](https://developer.roku.com/dev/docs/developer-setup) explains sideloading. Only one developer app can be sideloaded at a time. A sideloaded app is a Roku app tile; the public Roku SDK does not provide the Android HOME role or an equivalent default-launcher flow. Treat that last point as an inference from Roku's documented sandboxed app model, not a promise about undocumented firmware behavior.

The local Roku preview reached a remote-navigable home screen; its code also contains a direct HLS/MP4 playback path, but that path has not been verified end-to-end on the TV. It did **not** implement the Android catalog/addon stack, account sync, Jellyfin browser, debrid workflows, profiles, or app shortcuts. Bringing those features to Roku is a separate port, not a packaging switch. A Roku channel cannot assume that an arbitrary Android-playable stream is Roku-playable.

## What the Android app removes from the daily workflow

| Problem area | LumaDeck approach | Important limit |
| --- | --- | --- |
| Vendor home feed and scattered app tiles | Select the app as Home, use its restrained TV navigation and curated rows. | System-level ads, settings, and vendor overlays are outside the app. |
| Watchlists fragmented across services | Profile-scoped library, watched state, Continue Watching, Stremio export import, optional sync. | Import is best-effort; sync needs a backend. |
| Media trapped on one machine | Connect to a user-run Jellyfin library on the local network. | The server and network must remain available. |
| One forced content source | User-selected addons and optional cloud/debrid services. | Addons and providers are external; no catalog or stream is bundled. |
| Too many remotes/apps | Shortcuts to installed SmartTube, Deezer, and Moonlight/Apollo gaming. | They launch separate apps; remote handoff and return behavior depend on Android. |
| Cluttered playback | Autoplay/next episode, skip markers where available, subtitle/audio controls, and optional CRT effects. | Markers and playback formats depend on services and hardware. |
| Sluggish or stale UI | Manual temporary-cache clear, app restart, playback diagnostics, and configurable player behavior. | No universal automatic cache cleaning is implemented; hardware limits remain. |

## Technical boundaries and next engineering work

- **Independent distribution:** the fork still uses upstream application identifiers and full-flavor updater metadata. Replace those as a coordinated migration before releasing a standalone branded APK.
- **Own backend:** QR login and cross-device sync require a correctly configured Supabase service. This repo does not include an always-on hosted backend; self-hosting on a laptop means the laptop/server must stay on for sync.
- **Privacy:** local library and watch state can live on-device, but addons, artwork, tracking services, and optional sync make network requests. Review each provider and do not commit tokens or private exports.
- **Roku parity:** port discovery, auth, library, playback compatibility, and remote UX as a distinct Roku project if native Roku is a goal. The Android code cannot simply be sideloaded to Roku.
- **Reliability:** test boot, Home, external-app return, network outage, missing addon, cache clearing, and low-memory behavior on the actual target hardware. A successful compile is not proof of device-level smoothness.

This setup does not remove every vendor component from Android or Roku. It does not require rooting, firmware modification, DRM bypass, or unauthorized media access.
