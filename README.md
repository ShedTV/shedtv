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
