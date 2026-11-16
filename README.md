# Matchday

A sport-first IPTV player for Fire TV (Fire OS) and Android TV, for Xtream Codes providers.

## Phase 1 (this build)
- Xtream Codes sign-in (stored on the device only)
- Categories, channel list, Favourites (hold OK on a channel)
- Now / next guide info for the focused channel
- Full-screen playback (Media3), channel up/down, last channel, auto-reconnect
- Refresh-rate matching: switches the TV to 50Hz for UK broadcasts

On Fire TV, turn on **Settings → Display & Sounds → Display → Match Original Frame Rate** for 50Hz switching.

## Builds
Every push to `main` builds a signed APK on GitHub Actions and publishes it as the `latest` release.

## Forking and contributing
1. Fork this repo, then open your fork's **Actions** tab and enable workflows.
2. Every push to your fork's `main` builds **Matchday (dev)** — a separate app (`com.matchday.tv.dev`)
   that installs alongside the official one. Download it from your fork's `latest` release.
3. Send features back with a pull request.

The official app is signed with a private key held as repository secrets
(`MATCHDAY_KEYSTORE_B64`, `MATCHDAY_KEY_PASSWORD`) on the main repo only.
