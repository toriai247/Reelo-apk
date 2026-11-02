# Reelo - Comments, Likes, Report & Views System Polish

Activate and polish all interactive capabilities across Reelo: **Series & Episode Likes**, **Views Counter**, **Comments Bottom Sheet**, and **Server Problem Reporting** with optimistic state updates, heart animations, and instant feedback toasts.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following confirmed interactive behaviors will be implemented:

- **Optimistic UI Updates**: Toggling Like or opening an episode updates the UI state and counters instantly with spring animations, handling API persistence asynchronously in the background.
- **Comments Bottom Sheet in Video Player & Details**:
  - Accessible directly from the **WatchScreen right action rail (💬)** and **SeriesDetailsScreen**.
  - Supports instant comment posting, optimistic comment liking, avatar badges, and empty states.
- **Interactive Problem Reporting**:
  - Modernized `ReportSourceDialog` featuring choice chips, optional details text input, and animated confirmation toast upon submission.

---

## 1. Overview & Core Concept

### Key Capabilities Delivered
1. **Series & Episode Likes**:
   - Instant heart scale animation upon liking.
   - Live counter updates across WatchScreen, SeriesDetailsScreen, and Watchlist.
2. **Views Counter Tracking**:
   - Automatic view recording when an episode starts playing, incrementing view counts in local state and database.
3. **Comments System**:
   - Full comment listing, posting, and comment-level liking via an animated M3 `ModalBottomSheet`.
4. **Server Problem Reporting**:
   - Intuitive issue reporting with reason chips (Broken Video, Mismatched Audio, Copyright, Other) and success toast feedback.

---

## 2. User Experience & Visual Design

```
┌──────────────────────────────────────────────┐
│  Comments (14)                           [X] │
├──────────────────────────────────────────────┤
│ 👤 @alex                                    │
│    Episode 3 was mind blowing! 🔥           │
│    ❤️ 24                                    │
│                                              │
│ 👤 @sam                                     │
│    When does episode 4 air?                 │
│    ❤️ 5                                     │
├──────────────────────────────────────────────┤
│ [ Add a comment...                   ]  [ ➔ ]│
└──────────────────────────────────────────────┘
```

---

## 3. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                        WatchScreen.kt                           │
│  • Right Rail: Heart (Like) ❤️ • Comments 💬 • Report ⚠️        │
│  • Trigger CommentsBottomSheet & ReportSourceDialog             │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       WatchViewModel.kt                         │
│  • comments: List<SeriesComment>                                │
│  • postComment(), toggleCommentLike(), recordView()             │
│  • Optimistic update -> Supabase API call                       │
└─────────────────────────────────────────────────────────────────┘
```

### Key Modifications
1. **`WatchViewModel.kt` & `WatchUiState`**:
   - Add `comments: List<SeriesComment> = emptyList()`.
   - Add `showCommentsSheet: Boolean = false`.
   - Implement `loadComments()`, `postComment()`, `toggleCommentLike()`.
2. **`WatchScreen.kt`**:
   - Add Comments button (`💬`) in right action column.
   - Render `CommentsBottomSheet` when `showCommentsSheet` is true.
   - Display animated success toast when a report is submitted.
3. **`ReportSourceDialog.kt`**:
   - Redesign with `FilterChip` options for clean touch selection.
4. **`CommentsBottomSheet.kt`**:
   - Smooth animated insertion for new comments.
   - Instant like count updates on comment cards.
