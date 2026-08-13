# VPN Only Firefox

A lightweight Android browser powered by Mozilla GeckoView. It only creates a
browser session when Android reports that the app's validated default network
uses `TRANSPORT_VPN`. When that condition disappears, the current load is
stopped, the Gecko session is closed, and the browser is covered by a lock
screen.

## Important security setting

An ordinary Android app cannot provide a mathematically leak-proof kill switch:
there can be a tiny delay between a VPN failure and the network callback. For
strong enforcement, enable Android's system kill switch for your VPN:

1. Open **Settings > Network & internet > VPN** (wording varies by manufacturer).
2. Tap the gear beside your VPN.
3. Enable **Always-on VPN**.
4. Enable **Block connections without VPN**.

This project adds a second browser-level gate on top of that system protection.

## Build

1. Open this folder in a current Android Studio release with JDK 17.
2. Allow Gradle to download Android dependencies and GeckoView.
3. Build **app** or choose **Build > Generate App Bundles or APKs > Generate APKs**.

The GeckoView dependency uses `latest.release` for convenient first setup. After
the first successful sync, replace it with the exact resolved GeckoView version
before publishing reproducible production builds.

## Current foundation

- HTTP and HTTPS browsing (`usesCleartextTraffic=true`)
- Any validated Android VPN is accepted
- Address entry, back, forward, and reload
- Gecko session is destroyed when VPN disappears or the app goes to background
- No history, bookmarks, downloads, permissions UI, or multiple tabs yet

## Planned Firefox-stable phase

The full product will use Firefox for Android as its application base so it can
retain tabs, downloads, history, bookmarks, private browsing, site permissions,
desktop mode, add-ons and the other stable Firefox interface features. The VPN
policy layer will automatically trust NordVPN, Surfshark and ExpressVPN. When a
different VPN owns the active network, the user will be offered **Allow once**,
**Always allow**, or **Deny** before a Gecko session is created.

## Limitation

VPN detection confirms that Android routes this app's default network through a
VPN; it does not prove that the VPN provider is trustworthy. System **Block
connections without VPN** is required for OS-level leak prevention.
