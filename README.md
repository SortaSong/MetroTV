<div align="center">

<h1>MetroTV</h1>
<p>YouTube Music client optimized for Android TV</p>

[![Release](https://img.shields.io/github/v/release/MetrolistGroup/Metrolist?label=MetroTV&style=for-the-badge)](https://github.com/MetrolistGroup/Metrolist/releases)

> [!NOTE]
> **📺 About this Fork**
>
> **MetroTV** is a fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist), specifically modified to provide a native 10-foot UI experience for Android TV devices. While the original app is fantastic for mobile, this fork adds:
> - Full D-pad navigation support (except login and settings)
> - TV-specific layouts (Side Navigation Rail, Large Album Art)
> - Remote control shortcuts
> - "Keep Screen On" support for TV playback
>
> Based on Metrolist commit `7fb3f5e2` (dev branch).

> [!WARNING]
> If you're in a region where YouTube Music is not supported, you won't be able to use this app **unless** you have a proxy or VPN to connect to a YTM-supported region.

> [!IMPORTANT]
> **🖱️ Mouse Required for Login**
>
> While the main interface is fully optimized for TV remotes, the Google Login screen uses a web view that requires a **mouse** or air mouse to interact with. You can connect a USB mouse or use a remote app on your phone (like the Google TV app) to complete the login process.

</div>

## 🎮 TV Controls

Designed for standard Android TV remotes:

| Button | Action |
| :--- | :--- |
| **D-pad** | Navigate interface |
| **Center / OK** | Select item / Play / Pause |
| **Left / Right** | Seek +/- 10s |
| **Hold Left / Right** | Skip Previous / Next (hold 5s) |
| **Up** (Single Click) | Open Queue / Playlist |
| **Up** (Double Click) | Like / Unlike song |
| **Down** (Single Click) | Toggle Shuffle |
| **Down** (Double Click) | Cycle Repeat Mode |
| **Back** | Minimize player / Go back |

## 🎵 Queue Editor

Open the queue by pressing **Up** once from the player screen. Each row in the queue has three interactive zones, navigated with **Left / Right**:

| Zone | Highlight | Action on Center/OK |
| :--- | :--- | :--- |
| **Song** (leftmost) | 🟢 Green | Play track & close queue |
| **Drag Handle** (middle) | 🟡 Yellow | Enter / exit drag mode |
| **Remove Button** (right) | 🔴 Red | Remove track from queue |

### Navigation

| Input | Result |
| :--- | :--- |
| **Right** from Song | Move focus to Drag Handle |
| **Right** from Drag Handle | Move focus to Remove Button |
| **Left** from Remove Button | Move focus to Drag Handle |
| **Left** from Drag Handle | Move focus to Song |
| **Up / Down** | Move to the same zone in the row above / below |

### Reordering Tracks

1. Navigate to the **Drag Handle** (yellow) of the track you want to move.
2. Press **Center / OK** — the handle turns to the accent color, indicating **drag mode**.
3. Press **Up / Down** to move the track to its new position.
4. Press **Center / OK** or **Back** to drop the track and exit drag mode.

## 📥 Download

MetroTV builds are available exclusively via **GitHub Releases**.

[**Download Latest Release**](../../releases)

> Note: The package name is `com.metrotv.music`, allowing you to install it alongside the original Metrolist app.

## 🤝 Credits & Attribution

This project is a fork of **[Metrolist](https://github.com/MetrolistGroup/Metrolist)**. All core logic, data handling, and the beautiful foundation of this app are the work of the original Metrolist contributors.

Please visit the [original repository](https://github.com/MetrolistGroup/Metrolist) to support the original creators.

## 📄 Disclaimer

This project is not affiliated with, funded, authorized, endorsed by, or in any way associated with YouTube, Google LLC, or the original Metrolist developers.
