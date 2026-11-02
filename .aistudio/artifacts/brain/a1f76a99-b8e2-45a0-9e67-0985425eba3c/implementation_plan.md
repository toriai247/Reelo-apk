# Connect Reelo to Live Supabase & Remove All Mock Data

This plan outlines the exact changes to connect the **Reelo** Android application exclusively to the live Supabase database (`ffbmozwsasbhevtehbit`) using the provided publishable credentials, removing all mock, fake, and local fallback data across the entire codebase.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The app will connect strictly to the live Supabase instance:
> - **SUPABASE_URL:** `https://ffbmozwsasbhevtehbit.supabase.co`
> - **SUPABASE_ANON_KEY (Publishable Key):** `sb_publishable_kzXQl0X6eNtzFrAuY-3EFw_pYh6H-qr`
> - All mock data, sample series, and fallback stubs in `FakeCatalogData.kt` will be permanently deleted.
> - When the remote database has no rows for a query or RPC (e.g., empty array `[]`), screens will render clean, production-grade empty and error states instead of falling back to fake content.

---

## 1. Files to Delete & Modify

### Files to Delete:
- **`app/src/main/java/com/example/data/remote/FakeCatalogData.kt`**
  - Contains all hardcoded series cards (`cyber-samurai`, `chronicles-of-the-abyss`, etc.), fake categories, fake episodes, fake stream links, and fake comments.
  - Will be deleted completely.

### Files to Create / Configure:
- **`/.env` & `/.env.example`**
  - Recreate `/.env` and update `/.env.example` with the exact live credentials:
    ```properties
    SUPABASE_URL="https://ffbmozwsasbhevtehbit.supabase.co"
    SUPABASE_ANON_KEY="sb_publishable_kzXQl0X6eNtzFrAuY-3EFw_pYh6H-qr"
    ```
  - Loaded safely via the Android Secrets Gradle plugin into `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`.

### Files to Update:
1. **`app/src/main/java/com/example/data/remote/SupabaseApiClient.kt`**:
   - Strip out every fallback to `FakeCatalogData` across all methods:
     - `getCategories()`: Returns `from('content_categories')` directly or empty list on network error.
     - `getSeriesHome(category)`: Calls `rpc('get_series_home', { p_category })` directly; returns empty list if no shelves exist in the database.
     - `getContinueWatching()`: Calls `rpc('get_continue_watching')` directly for authenticated user.
     - `searchSeries(query, category, limit, offset)`: Calls `rpc('search_series')` directly.
     - `getSeriesBySlug(slug)`: Queries `from('series')` where `slug=eq.<slug>` directly; returns null if not found.
     - `getSeasons(seriesId)`: Queries `from('series_seasons')` directly.
     - `getEpisodes(seriesId)`: Queries `from('episodes')` directly.
     - `getEpisodeSources(episodeId)`: Queries `from('episode_sources')` directly.
     - `getRelatedSeries(seriesId)`: Calls `rpc('get_related_series')` directly.
     - `getComments(seriesId, episodeId)`: Queries `from('series_comments')` directly.
   - Clean up imports and remove all broken references to `FakeCatalogData`.

2. **`app/src/main/java/com/example/data/local/PreferencesManager.kt`**:
   - Return `BuildConfig.SUPABASE_ANON_KEY` directly without placeholder filtering.

3. **`app/src/test/java/com/example/ExampleRobolectricTest.kt`**:
   - Remove assertions on `FakeCatalogData`.
   - Add unit tests verifying schema model serialization and Supabase query construction.

4. **`README.md`**:
   - Update documentation to reflect pure live Supabase connectivity with zero mock data.

---

## 2. Database Schema & RLS Policy Mapping

Every query and RPC will strictly execute against the live schema specified in `REELO_SERIES_SCHEMA.md`:

| Screen / Feature | Live Call | Schema Object | RLS Policy Expected |
|---|---|---|---|
| **Categories** | `from('content_categories')` | `content_categories` | `categories_select` (anon/auth: `is_active = true`) |
| **Home Shelves** | `rpc('get_series_home', {p_category})` | RPC `get_series_home` | Executed as anon/auth |
| **Continue Watching** | `rpc('get_continue_watching', {p_limit})` | RPC `get_continue_watching` | Executed with user auth token |
| **Series Details** | `from('series')` by `slug` | `series` | `series_select` (`is_published and not is_blocked`) |
| **Seasons & Episodes** | `from('series_seasons')`, `from('episodes')` | `series_seasons`, `episodes` | `seasons_select`, `episodes_select` |
| **Episode Sources** | `from('episode_sources')` by `episode_id` | `episode_sources` | `sources_select` (`is_active = true`) |
| **Search** | `rpc('search_series', {q, p_category, lim, off})` | RPC `search_series` | Executed as anon/auth |
| **Watch Progress** | upsert `watch_progress` | `watch_progress` | `progress_insert` / `progress_update` (`auth.uid() = user_id`) |
| **Watch Views** | `rpc('record_view', {p_series_id, p_episode_id, p_viewer_key})` | RPC `record_view` | Executed as anon/auth |
| **Watchlist** | `from('series_watchlist')` | `series_watchlist` | `watchlist_select` / `watchlist_insert` (`auth.uid() = user_id`) |
| **Ratings** | `from('series_ratings')` | `series_ratings` | `ratings_select` / `ratings_insert` (`auth.uid() = user_id`) |
| **Comments** | `from('series_comments')` | `series_comments` | `comments_select` (anon/auth) |
| **Source Reports** | `from('source_reports')` | `source_reports` | `reports_insert` (`auth.uid() = user_id`) |

---

## 3. Architecture & Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                       Reelo UI Layer                        │
│   HomeScreen · DetailsScreen · WatchScreen (WebView)        │
│   SearchScreen · WatchlistScreen · ProfileScreen            │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / Coroutines
┌──────────────────────────────▼──────────────────────────────┐
│                    Repository Layer                         │
│   HomeRepository · SeriesRepository · EpisodeRepository     │
│   SourceRepository · UserActionsRepository · AuthRepository │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                  SupabaseApiClient (OkHttp)                 │
│   • Base URL: https://ffbmozwsasbhevtehbit.supabase.co       │
│   • Headers: apikey + Bearer token (anon or session JWT)    │
│   • Pure PostgREST REST endpoints & RPCs                     │
│   • NO local/mock/fake fallbacks                            │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS
┌──────────────────────────────▼──────────────────────────────┐
│              Live Supabase Database & Auth                  │
│   • 16 Series Tables with RLS                               │
│   • 5 Public RPCs                                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Verification Plan

1. **Gradle Build & Compilation:**
   - Execute `compile_applet` and `gradle assembleDebug` to verify complete build health.
2. **Unit & Robolectric Tests:**
   - Run `gradle :app:testDebugUnitTest` to verify test suite passing.
3. **Database Connectivity Smoke Test:**
   - Run a live test against `https://ffbmozwsasbhevtehbit.supabase.co/rest/v1/content_categories?select=*` with the publishable key.
4. **Final Checklist & Report:**
   - Verify zero occurrences of `FakeCatalogData` or mock arrays in the entire codebase.
   - Provide summary of deleted files, updated files, and schema validation.
