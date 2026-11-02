# Reelo — Android App Build Prompt (Kotlin)

> Paste everything below the line into Claude Code / your AI coding tool.
> Keep `REELO_SERIES_SCHEMA.md` in the project folder so the AI can read it.

**App name:** **Reelo** — *Movies · Anime · Cartoons · Drama*
Short, easy to say, sounds like "reel", works as a brand. (Backup names: **Flixo**, **Binjo**, **Telly**. Check Play Store / domain availability before final.)

---

## PROMPT (copy from here)

You are a senior Android engineer. Build a production-quality Android app called **Reelo** in **Kotlin + Jetpack Compose (Material 3)**. Reelo is a catalog app for Movies, Anime, Cartoons and Drama series. It connects to an existing Supabase project. The full database schema for this app is in `REELO_SERIES_SCHEMA.md`. Read it completely before writing any code.

### 0. Golden rules
1. **Use only the series tables and RPCs listed in the schema file.** Do not touch social, groups, posts, wallet, monetization or playlist tables. They exist in the same database but this app must never read or write them.
2. **Do not guess column names or RPC parameters.** Use exactly the names in the schema file. If something is unclear or missing, stop and ask me.
3. **Do not use any custom video player** (no ExoPlayer / Media3 / YouTube player). Video is watched inside a **WebView browser** that opens the page URL stored in `episode_sources.url`.
4. Only the Supabase **anon key** goes in the app, read from `local.properties` via `BuildConfig`. Never put a service-role key or any secret in code or git.
5. This app is for **viewers only**. No upload/admin/creator screens (a separate Studio app will do that).
6. Show your plan first (modules, screens, data models), wait for my OK, then build step by step.

### 1. Tech stack
- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- MVVM + Clean-ish layers: `data` (Supabase, repositories), `domain` (models, use-cases if useful), `ui` (screens, viewmodels)
- Hilt (DI), Kotlin Coroutines + Flow, kotlinx.serialization
- `supabase-kt` (postgrest, auth) with the Ktor Android engine
- Coil (images), DataStore (settings), Android `WebView` (watch screen)
- minSdk 24, latest stable compile/target SDK. Use latest stable library versions (check, don't assume).
- Package: `com.reelo.app`

### 2. Branding & theme
- **Primary color: yellow.** Brand yellow `#FFC400`. Text/icons placed on yellow are **black** (`#111111`) for contrast.
- **Light theme is the default.** Also provide a full **Dark theme**.
- Settings option: **System / Light / Dark**, saved in DataStore. Also a quick sun/moon toggle in the Home top bar.
- Suggested tokens
  - Light: background `#FFFFFF`, surface `#FFF8E1` (soft yellow tint), card `#FFFFFF`, outline `#E8E0C8`, text `#1A1A1A`, secondary text `#6B6B6B`
  - Dark: background `#0F0F0F`, surface `#1A1A1A`, card `#222222`, outline `#333333`, text `#F5F5F5`, secondary text `#A8A8A8`, accent stays `#FFC400`
- Rounded corners (12–16dp), clean spacing, poster cards with 2:3 ratio, banners 16:9.
- Simple wordmark "Reelo" (text logo) + adaptive launcher icon: yellow background with a black play-triangle / reel shape.
- Smooth shimmer skeletons while loading. No heavy animations.

### 3. Navigation
Bottom navigation (4 items): **Home · Search · Watchlist · Profile**

Screens: Splash → Home → Series Details → Watch. Plus Search, Watchlist, Profile/Settings, Login/Sign-up, Comments (bottom sheet).

### 4. Home screen
- Top bar: Reelo wordmark, theme toggle, (optional) search icon.
- **Category chips** from `content_categories` (`is_active = true`, order by `sort_order`). First chip is "All". Selecting a chip reloads the home with that category slug.
- Content comes from **`rpc('get_series_home', {p_category})`**. The result is a JSON array:
  `[{ "shelf": {id, slug, title, type, layout, position}, "items": [seriesCard, ...] }]`
- Render each shelf by `layout`:
  - `hero` → large auto-scrolling banner carousel (uses `banner_url`, falls back to `poster_url`), with title + **Watch** button
  - `row` → horizontal poster row
  - `top10` → horizontal row with big rank numbers 1–10
  - `grid` → 3-column poster grid
- Above shelves (only when logged in and result is non-empty): **Continue Watching** row from `rpc('get_continue_watching', {p_limit: 12})`. Each item opens the Watch screen at that episode.
- Pull-to-refresh, shimmer loading, friendly empty/error state with Retry.
- Tapping any series → **Series Details**.

`seriesCard` fields: `id, slug, title, original_title, description, poster_url, banner_url, category{id,slug,name}, genres[], languages[], rating, avg_user_rating, ratings_count, status, release_year, age_rating, is_mature, episodes_count, last_episode_at, views_count, likes_count, created_at`

### 5. Series Details screen
Load by `slug` from table `series` (select `*`; RLS only returns published, non-blocked series).
- Banner/poster header with gradient, back button
- Title, original title, year, category, status (Ongoing/Completed/Upcoming), age rating, 18+ badge if `is_mature`
- Rating chips: `rating` (external score, if present) and `avg_user_rating` (+ `ratings_count`)
- Genres as chips, languages, studio, short description with "Read more"
- Counters: likes, views, comments
- **Big yellow "▶ Watch" button** → opens Watch screen for the first unwatched episode (or Continue if progress exists). Secondary buttons: **Watchlist** (add/remove), **Like**, **Rate** (1–10 dialog), **Share**.
- If `trailer_url` exists: "Trailer" button → opens the same in-app browser screen.
- **Seasons & Episodes**: if more than one season (`series_seasons` / distinct `episodes.season_number`) show a season selector. Episodes list from `episodes` (`is_published`), ordered by `season_number, episode_number`; each row shows thumbnail, "E{number} · title", duration, watched check (from `watch_progress`). Tapping an episode opens Watch.
- **Related**: `rpc('get_related_series', {p_series_id, p_limit: 12})` as a poster row.
- **Comments** section preview + "View all" bottom sheet (see section 9).
- If `is_mature = true`: show a one-time 18+ confirmation dialog before showing content.
- Guests can browse; actions that need an account (like, watchlist, rate, comment, progress) open the Login screen.

### 6. WATCH screen (most important — read carefully)
**No custom player.** The video is played **inside a WebView**. The page that hosts the video (`episode_sources.url`) is opened in the browser, and the user watches from that page using the website's own controls.

Layout (force **landscape**, immersive/fullscreen system bars hidden):

```
┌────┬─────────────────────────────────────┬────┐
│ ←  │                                     │ S1 │
│ →  │                                     │ 1  │
│ ⟳  │          WebView (browser)          │ 2  │
│ ✕  │          loads source URL           │ 3  │
│ ⚙  │                                     │ 4  │
│    │                                     │ 5  │
└────┴─────────────────────────────────────┴────┘
 LEFT RAIL              CENTER              RIGHT RAIL
```

**Center:** `WebView` filling all remaining space. Thin yellow progress bar at the top while a page is loading.

**Left rail (narrow, always visible, ~48dp, semi-transparent dark with yellow icons):**
- **◀ Back** → `webView.goBack()`, disabled if `!canGoBack`
- **▶ Forward** → `webView.goForward()`, disabled if `!canGoForward`
- **⟳ Reload**
- **✕ Exit** → leave the Watch screen (back to Series Details)
- **⚙ Servers** → bottom sheet listing all `episode_sources` for this episode (label, quality, language). Selecting one loads that URL. Also contains **"Report problem"**.
- Back/Forward exist so that if an ad or another site opens, the user can come back to the same video page without leaving the app.

**Right rail (narrow, ~56dp, vertically scrollable):**
- Top: season chip (tap to switch season) only if the series has more than one season
- Below: **episode numbers 1, 2, 3, 4, 5 …** as a vertical scrolling list (`LazyColumn`) for the selected season
- Current episode highlighted in **yellow** with black text; watched episodes shown dimmed with a small check
- Auto-scroll so the current episode is visible
- Tapping a number switches to that episode instantly (loads its source in the same WebView, **clears the WebView history** after the new page loads so Back only goes within that episode)

**Rail visibility:** small handle to collapse/expand each rail to give the video more space. Remember state in DataStore.

**Source selection:**
- Query `episode_sources` where `episode_id = current` ordered by `priority` ascending. Lower number = tried first (RLS already hides inactive sources).
- Load the first source automatically. If the page fails to load (network/HTTP error / SSL error), show an in-screen message with **"Try next server"** and **"Retry"**.
- If an episode has no source: show "No source available yet" with a Report button.

**WebView configuration (security matters):**
- Enable JavaScript, DOM storage, `mediaPlaybackRequiresUserGesture = false`, third-party cookies on, mixed content = compatibility mode
- Disable file access, content access, and geolocation. Deny all `onPermissionRequest`s (camera/mic).
- **Fullscreen video:** implement `WebChromeClient.onShowCustomView / onHideCustomView`. When the site's video goes fullscreen, hide both rails and show the custom view; restore on exit.
- **Popups / new windows:** block `window.open` popups by default (`onCreateWindow` returns false). Add a setting "Allow popups" (default OFF).
- **Redirect protection:** in `shouldOverrideUrlLoading`
  - Block any non-`http/https` scheme (`intent://`, `market://`, app deep-links, `tel:` …) — do not launch other apps
  - If the navigation has **no user gesture** and goes to a **different host** than the current page, block it (this stops ad auto-redirects)
  - Everything else loads inside the same WebView
- **SSL errors:** cancel the load and show the error state. Never call `handler.proceed()`.
- **Downloads:** ignore/block download requests.
- Hardware/system **Back button**: (1) exit custom fullscreen if active, else (2) `goBack()` if possible, else (3) close Watch screen.
- Keep screen on while the Watch screen is open. Pause WebView in `onPause`, resume in `onResume`, destroy properly to avoid leaks.
- Desktop-like user agent is **not** needed; use the default mobile UA unless a source fails (optional per-source toggle).

**Tracking from the Watch screen**
- On open: call `rpc('record_view', {p_series_id, p_episode_id, p_viewer_key})`. For guests pass `p_viewer_key` = a random UUID generated once and stored in DataStore. For logged-in users it can be null.
- **Progress:** a WebView cannot report the video position, so use an **estimate**: upsert `watch_progress` (`user_id, episode_id, series_id, position_sec, duration_sec, completed`) when the episode opens with `position_sec = max(6, secondsSpentSoFar)` (the schema's *Continue Watching* only lists rows with `position_sec > 5` and `completed = false`), then update every ~30 s while the screen is in foreground and on exit. Set `completed = true` when time spent ≥ 85% of `episodes.duration_seconds` (or when the user taps "Mark as watched" in the Servers sheet). Logged-in users only. Clearly document in code that position is an estimate.
- Auto-advance: when marked completed, show a small "Next episode ▶" chip on the right rail.

### 7. Search screen
- Search field with 400 ms debounce; minimum 2 characters.
- `rpc('search_series', {q, p_category, lim: 20, off})` → list of series cards. Category chips under the field. Infinite scroll via `off`.
- Empty state, recent searches (DataStore, max 10).

### 8. Watchlist & Profile
- **Watchlist** (login required): read from `series_watchlist` (`user_id = me`) and embed `series(*)`, newest first. Swipe/long-press to remove.
- **Profile/Settings:**
  - Guest: "Log in / Sign up" card
  - Logged in: avatar, display name, username (read from `profiles` — only `id, username, display_name, avatar_url, role`)
  - Theme (System/Light/Dark), Allow popups switch, Clear search history, About, Privacy/Terms links, Log out
- **Auth:** Supabase Auth email + password sign-up/login; optionally Google sign-in. On sign-up pass `username` and `display_name` in user metadata (the database trigger creates the profile). Persist session. Guests can use the app without logging in.

### 9. Comments (bottom sheet)
- Table `series_comments` for `series_id` (and optionally `episode_id`). Load top-level (`parent_id is null`) newest first, with embedded author: `select('*, profiles(username, display_name, avatar_url)')`. Replies load on tap ("View replies").
- Post a comment / reply (insert with `series_id`, optional `episode_id`, `parent_id`, `content` 1–3000 chars). Edit/delete own comment.
- Like/unlike a comment via `series_comment_likes` (`comment_id`, `user_id`). `likes_count` is maintained by the database — do not update it from the app.
- Login required to post/like.

### 10. Report broken source
- From the Servers sheet: reasons `broken`, `wrong_episode`, `copyright`, `other` + optional details (max 1000 chars) → insert into `source_reports` (`source_id`, `user_id`, `reason`, `details`). Login required. The database increments `fail_count` for `broken` automatically.

### 11. Data layer rules
- One `SupabaseClient` provided by Hilt. Repositories: `HomeRepository`, `SeriesRepository`, `EpisodeRepository`, `SourceRepository`, `UserActionsRepository` (like/watchlist/rating/progress), `CommentRepository`, `AuthRepository`, `SettingsRepository`.
- Models with `@Serializable` + `@SerialName` matching DB column names exactly. Nullable where the schema says nullable. UUIDs as `String`, timestamps as `kotlinx.datetime.Instant` (or ISO string).
- **Never write** counters (`likes_count`, `views_count`, `comments_count`, `avg_user_rating`, `ratings_count`, `episodes_count`, `fail_count`). Triggers/RPCs do it.
- Like/rating/watchlist: optimistic UI update, rollback on error. Use `upsert` for `series_ratings` and `watch_progress`.
- Handle RLS errors, expired sessions and no-network gracefully (snackbar + retry). Cache the last Home result for offline display.
- Do not call tables outside this list: `content_categories, series, series_seasons, episodes, episode_sources, source_reports, series_shelves, series_shelf_items, series_likes, episode_likes, series_ratings, series_watchlist, watch_progress, series_comments, series_comment_likes` and `profiles` (read-only, 5 columns). RPCs: `get_series_home, search_series, get_related_series, get_continue_watching, record_view`.

### 12. Quality bar
- Handles rotation and process death on the Watch screen; no WebView memory leaks.
- Accessibility: content descriptions on all icons, minimum 48dp touch targets (rails included), good contrast in both themes.
- Unit tests for ViewModels / repositories (fake data), a UI test for the Watch screen controls (back/forward enabled state, episode switching).
- ProGuard/R8 rules for serialization and supabase-kt. Release build must work.
- README: setup steps (`local.properties` keys `SUPABASE_URL`, `SUPABASE_ANON_KEY`), how to run, folder structure.

### 13. Deliverables (in this order)
1. Short architecture plan + folder structure + list of screens → **wait for my approval**
2. Gradle setup, theme (light/dark/yellow), navigation shell
3. Data layer (models, repositories, Supabase setup) matching `REELO_SERIES_SCHEMA.md` 1:1
4. Home, Series Details, Watch (WebView), Search, Watchlist, Profile/Auth, Comments
5. Tests, README, release config
6. A final **checklist** that proves every table/column/RPC used by the app exists in the schema file, and lists any assumption you had to make.

### 14. Out of scope
Uploading/editing series, admin tools, monetization, wallet, social feed, groups, notifications, downloads, offline video, Chromecast, custom video player.
