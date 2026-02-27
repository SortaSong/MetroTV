# MetroTV Fork Changes

This document outlines the modifications made to transform Metrolist into **MetroTV**, an Android TV-optimized YouTube Music client.

## Core Modifications

The application now detects if it is running on an Android TV device and switches to a completely new UI paradigm (`TvMainScreen`) instead of the standard mobile `Scaffold`.

### 1. TV Detection & Routing
*   **File**: `app/src/main/kotlin/com/metrolist/music/MainActivity.kt`
*   **Change**: Added `isTv` boolean check using `TvUtils.isAndroidTv(context)`.
*   **Change**: Conditional rendering in `setContent`:
    ```kotlin
    if (isTv) {
        TvMainScreen(...)
    } else {
        Scaffold(...) // Original mobile UI
    }
    ```
*   **Change**: Added `FLAG_KEEP_SCREEN_ON` for TV to prevent sleep during playback.

### 2. New TV User Interface (`ui/tv/`)
All new TV-specific UI components are isolated in the `ui/tv` package to minimize impact on the existing mobile codebase.

*   **`TvMainScreen.kt`**:
    *   **Root Layout**: Uses a `Row` with a permanent `NavigationRail` on the left and a content area on the right.
    *   **Navigation**: Replaces the bottom bar with a side rail optimized for D-pad navigation.
    *   **`TvAccountMenu`**: A custom, focus-aware replacement for the mobile `AccountSettings` screen. It solves the issue where D-pad focus would get stuck in the nav rail because mobile list items weren't focusable.
    *   **`TvNowPlayingBar`**: A slim bottom bar that shows the current track and basic controls, always accessible via D-pad.

*   **`TvPlayerScreen.kt`**:
    *   **Full-Screen Player**: A completely new player UI built for 10-foot experience.
    *   **D-pad Controls**:
        *   **Center**: Play/Pause.
        *   **Left/Right**: Seek +/- 10s.
        *   **Hold Left/Right**: Seek to Previous/Next track with a visual arc progress indicator (5s hold).
        *   **Up**: Open Queue (single click) / Like song (double click).
        *   **Down**: Shuffle (single click) / Repeat mode (double click).
    *   **Double-Tap Detection**: Implemented custom `Job`-based timing to distinguish single vs. double clicks on D-pad.

*   **`TvQueueScreen.kt`**:
    *   **Focus Management**: A specialized list where `userScrollEnabled` is disabled, and scrolling is handled programmatically via `FocusRequester` and `onKeyEvent` to ensure the selected item stays centered and focus never gets lost.
    *   **Queue Editing**: Added full support for drag-and-drop reordering (D-pad Up/Down in "Move Mode") and track removal via dedicated buttons in each list item.

*   **`TvUtils.kt`**:
    *   Helper class for detecting TV configuration (`UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION`).

### 3. Build & Configuration Changes
*   **`app/build.gradle.kts`**:
    *   Changed `applicationId` to `com.metrotv.music` to allow parallel installation with the original Metrolist app.
    *   Added a persistent debug keystore for consistent signing.
*   **`app/src/main/res/values/app_name.xml`**: Renamed app to "MetroTV".
*   **`app/src/debug/res/values/app_name.xml`**: Renamed debug build to "MetroTV".

### 4. Critical Fixes
*   **Login Screen WebView**: Fixed a black screen issue on TV emulators by forcing software rendering (`LAYER_TYPE_SOFTWARE`) on the `WebView` and correctly applying `windowInsetsPadding` to a parent container instead of the WebView itself.
*   **Focus Traps**: Removed `focusable()` modifiers that were conflicting with `clickable()` modifiers, ensuring D-pad clicks correctly trigger actions in the Settings menu.

## Rationale
The goal was to create a native-feeling TV experience without rewriting the core business logic or data layer. By branching at the top-level UI (`MainActivity`), we reuse all existing ViewModels, repositories, and player logic while providing a UI that works with a remote control.
