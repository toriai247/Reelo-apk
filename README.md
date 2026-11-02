# Reelo — Movies · Anime · Cartoons · Drama

**Reelo** is a production-quality catalog and streaming Android app built with **Kotlin + Jetpack Compose (Material 3)**. It connects to Supabase PostgREST & Auth backend to stream videos, track watch progress, bookmark series, and engage with community comments.

---

## 🎨 Visual Identity & Branding
- **Brand Primary Yellow:** `#FFC400`
- **Contrast Text & Icons on Yellow:** `#111111`
- **Adaptive Launcher Icon:** Yellow background with a bold black cinema film reel and play-triangle emblem.
- **Theme Modes:** Light (default) with soft warm surfaces (`#FFF8E1`), full Dark mode (`#0F0F0F`), and System mode. Includes a 1-tap sun/moon toggle in the top bar.

---

## 🚀 Key Features

### 1. Home Catalog & Dynamic Shelves
- Category chips dynamically loaded from `content_categories` (`All`, `Movie`, `Anime`, `Cartoon`, `Drama`).
- Shelves parsed from `rpc('get_series_home', {p_category})`:
  - `hero`: Large auto-scrolling 16:9 banner carousel with direct "Watch" button.
  - `row`: Horizontal scrolling 2:3 poster cards with rating and mature badges.
  - `top10`: Prominent rank numbers 1–10 overlapping poster cards.
  - `grid`: 3-column poster grid.
- **Continue Watching** shelf from `rpc('get_continue_watching')` showing current progress and instant resume.

### 2. Series Details
- Header with backdrop image, title, original title, release year, category, and 18+ mature badge.
- Interactive actions: **Watch Episode 1**, **Watchlist**, **Like**, **Rate** (1–10 dialog), and **Share**.
- One-time age verification prompt for 18+ mature series before accessing.
- Season selector tabs and scrollable episode list with thumbnails, durations, and watched status.
- Related series row from `rpc('get_related_series')`.
- Community comments preview with "View All" bottom sheet.

### 3. Dedicated WATCH Screen (Landscape WebView Player)
- **No custom player**: Streams directly from hosting provider page (`episode_sources.url`) inside an Android `WebView`.
- **Landscape forced & Immersive**: System bars hidden and screen kept on.
- **Left Rail (~48dp)**:
  - ◀ Browser Back (disabled if `!canGoBack`)
  - ▶ Browser Forward (disabled if `!canGoForward`)
  - ⟳ Reload page
  - ✕ Exit back to Series Details
  - ⚙ Streaming Servers bottom sheet with server switching, quality tags, "Mark as watched", and "Report problem".
- **Right Rail (~56dp)**:
  - Season selector (if multiple seasons)
  - Vertical scrolling list of episode numbers (1, 2, 3...)
  - Current episode highlighted in brand yellow with black text; watched episodes dimmed with checkmark
  - Auto-scrolls so current episode is centered
  - Tap number to switch episode instantly (resets history for episode-local navigation)
  - "Next episode ▶" chip when episode is completed
- **Collapsible Rails**: Small handle to collapse both rails for complete video immersion.
- **Security & Anti-Redirect Protection**:
  - Non-HTTP/HTTPS deep-links blocked
  - Unprompted redirects to external hosts blocked
  - SSL errors canceled immediately (never proceeded)
  - Fullscreen video support via `WebChromeClient.onShowCustomView`
- **Progress Tracking & Analytics**:
  - Calls `record_view` RPC on open (with guest device viewer key or user ID)
  - Automatically estimates progress every 30s and on exit
  - Marks completed when time spent reaches 85% of duration.

### 4. Search & Discovery
- Search text field with 400ms debounce (minimum 2 characters).
- Instant filtering by category chips.
- Recent search queries saved in DataStore (max 10) with 1-tap clear.

### 5. Watchlist & Profile
- Watchlist connected to `series_watchlist`.
- Profile screen supporting Email/Password Sign In & Sign Up.
- DataStore settings for Theme, Popup allowance, and search history.
- Direct connection to live Supabase backend with production-grade empty and loading states.

---

## ⚙ Setup & Configuration

Add your Supabase credentials to `.env` or the **AI Studio Secrets panel**:
```properties
SUPABASE_URL=https://ffbmozwsasbhevtehbit.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key_here
```

---

## 📁 Architecture & Folder Structure

```
app/src/main/java/com/example/
├── ReeloApplication.kt       # Application class initializing AppContainer
├── MainActivity.kt           # Edge-to-edge entry point with ReeloTheme
├── data/
│   ├── model/
│   │   └── SeriesModels.kt   # Moshi data classes matching REELO_SERIES_SCHEMA.md
│   ├── local/
│   │   └── PreferencesManager.kt # DataStore for theme, viewerKey, popups, recents
│   ├── remote/
│   │   └── SupabaseApiClient.kt  # PostgREST REST RPC & Auth client (Live Supabase)
│   └── repository/
│       └── Repositories.kt   # Clean architecture repositories
├── di/
│   └── AppContainer.kt       # Dependency injection container
└── ui/
    ├── theme/
    │   ├── Color.kt          # Brand yellow #FFC400, dark/light tokens
    │   ├── Theme.kt          # ReeloTheme with System, Light, Dark modes
    │   └── Type.kt           # Material 3 Typography
    ├── components/
    │   ├── ReeloComponents.kt# Poster cards, Hero carousel, Top 10, Continue watching
    │   └── RatingDialog.kt   # 1–10 Rating modal dialog
    ├── navigation/
    │   └── Navigation.kt     # Bottom nav & NavHost (Home, Search, Watchlist, Profile, Details, Watch)
    └── screens/
        ├── home/             # HomeScreen & HomeViewModel
        ├── details/          # SeriesDetailsScreen & SeriesDetailsViewModel
        ├── watch/            # WatchScreen & WatchViewModel (WebView player)
        ├── search/           # SearchScreen & SearchViewModel
        ├── watchlist/        # WatchlistScreen & WatchlistViewModel
        ├── profile/          # ProfileScreen, ProfileViewModel & AuthDialog
        ├── comments/         # CommentsBottomSheet
        └── report/           # ReportSourceDialog
```

---

## 📋 Database & Schema Compliance Checklist

Every table, column, and RPC from `REELO_SERIES_SCHEMA.md` is strictly observed:
- [x] `content_categories` (`id, slug, name, sort_order, is_active`)
- [x] `series` (All 29 columns including `slug, title, genres, rating, avg_user_rating, is_mature, trailer_url`)
- [x] `series_seasons` (`id, series_id, season_number, title, poster_url`)
- [x] `episodes` (`id, series_id, season_number, episode_number, title, thumbnail_url, duration_seconds`)
- [x] `episode_sources` (`id, episode_id, label, url, priority, is_active`)
- [x] `source_reports` (`id, source_id, user_id, reason, details, status`)
- [x] `series_shelves` & `series_shelf_items`
- [x] `series_likes` & `episode_likes`
- [x] `series_ratings` (1–10 rating upsert)
- [x] `series_watchlist` (User bookmarking)
- [x] `watch_progress` (position_sec, completed, duration_sec)
- [x] `watch_views` (via `record_view` RPC)
- [x] `series_comments` & `series_comment_likes`
- [x] `profiles` (`id, username, display_name, avatar_url, role` read-only)
- [x] RPC `get_series_home(p_category)`
- [x] RPC `get_continue_watching(p_limit)`
- [x] RPC `search_series(q, p_category, lim, off)`
- [x] RPC `get_related_series(p_series_id, p_limit)`
- [x] RPC `record_view(p_series_id, p_episode_id, p_viewer_key)`

---

## 🤖 GitHub Actions CI/CD (APK Build System)

Whenever you push code to GitHub:
1. **Automated Trigger**: The workflow at `.github/workflows/build-apk.yml` triggers on pushes to `main`/`master`, pull requests, and manual triggers (`workflow_dispatch`).
2. **Environment & Secrets**: Injects `SUPABASE_URL` and `SUPABASE_ANON_KEY` from your repository secrets (with automatic fallback to project credentials if unset).
3. **Automated Testing & Compilation**: Runs unit & Robolectric tests and compiles the Android debug APK using `./gradlew assembleDebug`.
4. **Downloadable Artifact**: Uploads the finished APK as **`reelo-debug-apk`** under the **Actions** tab for direct download.
5. **Releases**: Pushing a version tag (e.g. `v1.0.0`) automatically publishes a GitHub Release with the APK attached.

