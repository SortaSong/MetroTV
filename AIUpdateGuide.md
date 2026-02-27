# AI Agent Maintenance Guide: MetroTV

This guide is intended for AI agents tasked with maintaining the MetroTV fork of Metrolist. Follow these instructions carefully to update the codebase from the upstream repository without breaking TV functionality.

## 1. Updating from Upstream (Rebase Workflow)

The goal is to pull in the latest features and fixes from the original Metrolist repository while preserving our TV-specific modifications.

### Step 1: Fetch Upstream Changes
Assume `upstream` points to `https://github.com/MetrolistGroup/Metrolist.git`.
```bash
git fetch upstream
```

### Step 2: Rebase onto Latest Master
We use rebase to keep our history clean and apply our TV patches *on top* of the latest code.
```bash
git checkout feature/AndroidTVInterface
git rebase upstream/master
```

### Step 3: Resolve Conflicts
Conflicts often occur in these files. Here is how to handle them:

*   **`app/src/main/kotlin/com/metrolist/music/MainActivity.kt`**:
    *   **Goal**: Keep the `isTv` check and `TvMainScreen` routing.
    *   **Action**: Accept upstream changes to `setContent` block logic (e.g., `CompositionLocalProvider` additions), but **ensure** they are wrapped in the `if (isTv)` / `else` block we introduced. The `TvMainScreen` call must remain.

*   **`app/build.gradle.kts`**:
    *   **Goal**: Keep `applicationId = "com.metrotv.music"` and `persistentDebug` signing config.
    *   **Action**: Accept upstream dependency updates. Re-apply the `applicationId` change if it gets overwritten.

*   **`app/src/main/res/values/strings.xml`**:
    *   **Goal**: Keep app name as "MetroTV".
    *   **Action**: Accept upstream string additions. Ensure `app_name` remains "MetroTV".

### Step 4: Verify Critical TV Files
After rebase, ensure the following files exist and compile:
- `app/src/main/kotlin/com/metrolist/music/ui/tv/` (entire directory)
- `ForkChanges.md`
- `AIUpdateGuide.md`

## 2. Regression Testing Checklist

After updating, verify these critical paths on an Android TV emulator (e.g., 1080p Android 11+ TV image):

1.  **TV Detection**: App launches into the TV interface (Side Navigation Rail), not the mobile bottom bar.
2.  **Focus Traps**:
    *   Navigate to **Settings**. Use D-pad to enter the menu.
    *   **Test**: Ensure D-pad focus moves *into* the settings list items (Login, Sync, etc.).
    *   **Failure Mode**: Focus stays on the Navigation Rail and cannot move right.
    *   **Fix**: Check `TvAccountMenu.kt`. Ensure `TvMenuRow` uses `clickable` but **NOT** `focusable()`.
3.  **Login Screen**:
    *   Navigate to **Settings > Login**.
    *   **Test**: Verify the Google Login web page appears and is **visible** (not black).
    *   **Failure Mode**: Screen is black, but logs show page loaded.
    *   **Fix**: Ensure `WebView` in `LoginScreen.kt` has `setLayerType(View.LAYER_TYPE_SOFTWARE, null)` and is wrapped in a `Box` with `windowInsetsPadding`.
4.  **Player Controls**:
    *   Play a song.
    *   **Test**: D-pad Center toggles play/pause. Left/Right seeks. Long-press Left/Right triggers skip track (visual arc).
    *   **Failure Mode**: Center button does nothing or double-clicks are interpreted as single clicks.

## 3. Documentation Updates

After a successful update:

1.  **Identify Base Commit**: Find the hash of the upstream commit you rebased onto.
    ```bash
    git log -n 1 upstream/master
    ```
2.  **Update README.md**:
    *   Locate the "About this Fork" section.
    *   Update the line: `Based on Metrolist commit <HASH>`.
3.  **Update ForkChanges.md**:
    *   If you had to modify any new files to make TV work (e.g., a new ViewModel required changes in `TvMainScreen`), add a note to the `Core Modifications` section.

## 4. Common Pitfalls

*   **New Navigation Routes**: If upstream adds new screens (e.g., "Lyrics", "Stats"), they won't automatically appear in the TV `NavigationRail`. You must manually add them to `TvMainScreen.kt` or `TvNavigationRail` if they are relevant for TV.
*   **Theme Changes**: If upstream refactors `MetrolistTheme` or `LocalPlayerAwareWindowInsets`, `TvMainScreen` and `TvPlayerScreen` may break. Check `MainActivity.kt` to see what CompositionLocals are provided.
*   **WindowInsets**: TV handles insets differently. If the UI is pushed off-screen or clipped, check `LocalPlayerAwareWindowInsets` usage in `TvMainScreen`.

---
**End of Guide**
