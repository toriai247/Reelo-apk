package com.example.data.remote

import android.util.Log
import com.example.data.local.PreferencesManager
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SupabaseApiClient(private val prefs: PreferencesManager) {

    private val tag = "SupabaseApiClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private suspend fun createRequest(
        endpoint: String,
        method: String = "GET",
        jsonBody: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): Request {
        val baseUrl = prefs.getSupabaseUrl().trimEnd('/')
        val anonKey = prefs.getSupabaseAnonKey()
        val userToken = prefs.authTokenFlow.first()
        val authBearer = if (!userToken.isNullOrBlank()) userToken else anonKey

        val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"

        val builder = Request.Builder()
            .url(fullUrl)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $authBearer")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        val body = jsonBody?.toRequestBody(jsonMediaType)
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "".toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete(body)
            "PATCH" -> builder.patch(body ?: "".toRequestBody(jsonMediaType))
        }

        return builder.build()
    }

    private suspend fun executeSafe(request: Request): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                Log.w(tag, "Supabase request ${request.url} failed: ${response.code} - $responseBody")
                return@withContext null
            }
            return@withContext responseBody
        } catch (e: Exception) {
            Log.e(tag, "Network error on ${request.url}: ${e.message}")
            return@withContext null
        }
    }

    // --- Content Categories ---
    suspend fun getCategories(): List<ContentCategory> = withContext(Dispatchers.IO) {
        val request = createRequest("/rest/v1/content_categories?select=*&is_active=eq.true&order=sort_order.asc")
        val json = executeSafe(request)
        if (json.isNullOrBlank()) return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, ContentCategory::class.java)
            val adapter = moshi.adapter<List<ContentCategory>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing categories: ${e.message}")
            emptyList()
        }
    }

    // --- Home Shelves RPC ---
    suspend fun getSeriesHome(category: String? = null): List<HomeShelfItem> = withContext(Dispatchers.IO) {
        val bodyJson = if (category.isNullOrBlank() || category.lowercase() == "all") {
            "{\"p_category\": null}"
        } else {
            "{\"p_category\": \"${category.lowercase()}\"}"
        }

        val request = createRequest("/rest/v1/rpc/get_series_home", method = "POST", jsonBody = bodyJson)
        val json = executeSafe(request)
        if (json.isNullOrBlank() || json.trim() == "[]") {
            return@withContext emptyList()
        }

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, HomeShelfItem::class.java)
            val adapter = moshi.adapter<List<HomeShelfItem>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing get_series_home: ${e.message}")
            emptyList()
        }
    }

    // --- Continue Watching RPC ---
    suspend fun getContinueWatching(limit: Int = 12): List<ContinueWatchingItem> = withContext(Dispatchers.IO) {
        val userToken = prefs.authTokenFlow.first()
        if (userToken.isNullOrBlank()) return@withContext emptyList()

        val request = createRequest("/rest/v1/rpc/get_continue_watching", method = "POST", jsonBody = "{\"p_limit\": $limit}")
        val json = executeSafe(request) ?: return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, ContinueWatchingItem::class.java)
            val adapter = moshi.adapter<List<ContinueWatchingItem>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing continue watching: ${e.message}")
            emptyList()
        }
    }

    // --- Search Series RPC ---
    suspend fun searchSeries(query: String, category: String? = null, limit: Int = 20, offset: Int = 0): List<SeriesCard> = withContext(Dispatchers.IO) {
        val catParam = if (category.isNullOrBlank() || category.lowercase() == "all") "null" else "\"${category.lowercase()}\""
        val bodyJson = "{\"q\": \"$query\", \"p_category\": $catParam, \"lim\": $limit, \"off\": $offset}"

        val request = createRequest("/rest/v1/rpc/search_series", method = "POST", jsonBody = bodyJson)
        val json = executeSafe(request)
        if (json.isNullOrBlank()) return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, SeriesCard::class.java)
            val adapter = moshi.adapter<List<SeriesCard>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing search results: ${e.message}")
            emptyList()
        }
    }

    // --- Series Details by Slug ---
    suspend fun getSeriesBySlug(slug: String): Series? = withContext(Dispatchers.IO) {
        val request = createRequest("/rest/v1/series?slug=eq.$slug&select=*")
        val json = executeSafe(request)
        if (json.isNullOrBlank()) return@withContext null

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, Series::class.java)
            val adapter = moshi.adapter<List<Series>>(type)
            val list = adapter.fromJson(json)
            list?.firstOrNull()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing series by slug: ${e.message}")
            null
        }
    }

    // --- Seasons for Series ---
    suspend fun getSeasons(seriesId: String): List<SeriesSeason> = withContext(Dispatchers.IO) {
        val request = createRequest("/rest/v1/series_seasons?series_id=eq.$seriesId&order=season_number.asc")
        val json = executeSafe(request)
        if (json.isNullOrBlank()) return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, SeriesSeason::class.java)
            val adapter = moshi.adapter<List<SeriesSeason>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing seasons: ${e.message}")
            emptyList()
        }
    }

    // --- Episodes for Series ---
    suspend fun getEpisodes(seriesId: String): List<Episode> = withContext(Dispatchers.IO) {
        val request = createRequest("/rest/v1/episodes?series_id=eq.$seriesId&is_published=eq.true&order=season_number.asc,episode_number.asc")
        val json = executeSafe(request)
        if (json.isNullOrBlank() || json.trim() == "[]") return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, Episode::class.java)
            val adapter = moshi.adapter<List<Episode>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing episodes: ${e.message}")
            emptyList()
        }
    }

    // --- Sources for Episode ---
    suspend fun getEpisodeSources(episodeId: String): List<EpisodeSource> = withContext(Dispatchers.IO) {
        val request = createRequest("/rest/v1/episode_sources?episode_id=eq.$episodeId&is_active=eq.true&order=priority.asc")
        val json = executeSafe(request)
        if (json.isNullOrBlank() || json.trim() == "[]") return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, EpisodeSource::class.java)
            val adapter = moshi.adapter<List<EpisodeSource>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing episode sources: ${e.message}")
            emptyList()
        }
    }

    // --- Related Series RPC ---
    suspend fun getRelatedSeries(seriesId: String, limit: Int = 12): List<SeriesCard> = withContext(Dispatchers.IO) {
        val bodyJson = "{\"p_series_id\": \"$seriesId\", \"p_limit\": $limit}"
        val request = createRequest("/rest/v1/rpc/get_related_series", method = "POST", jsonBody = bodyJson)
        val json = executeSafe(request)
        if (json.isNullOrBlank()) return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, SeriesCard::class.java)
            val adapter = moshi.adapter<List<SeriesCard>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing related series: ${e.message}")
            emptyList()
        }
    }

    // --- Record View RPC ---
    suspend fun recordView(seriesId: String, episodeId: String? = null) = withContext(Dispatchers.IO) {
        val viewerKey = prefs.getOrCreateViewerKey()
        val epParam = if (episodeId != null) "\"$episodeId\"" else "null"
        val bodyJson = "{\"p_series_id\": \"$seriesId\", \"p_episode_id\": $epParam, \"p_viewer_key\": \"$viewerKey\"}"

        val request = createRequest("/rest/v1/rpc/record_view", method = "POST", jsonBody = bodyJson)
        executeSafe(request)
    }

    // --- Watch Progress Upsert ---
    suspend fun upsertWatchProgress(progress: WatchProgress) = withContext(Dispatchers.IO) {
        val userToken = prefs.authTokenFlow.first()
        if (userToken.isNullOrBlank()) return@withContext

        val adapter = moshi.adapter(WatchProgress::class.java)
        val jsonBody = adapter.toJson(progress)
        val request = createRequest(
            "/rest/v1/watch_progress",
            method = "POST",
            jsonBody = jsonBody,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
        )
        executeSafe(request)
    }

    // --- Watchlist ---
    suspend fun getWatchlist(userId: String): List<WatchlistItem> = withContext(Dispatchers.IO) {
        val request = createRequest("/rest/v1/series_watchlist?user_id=eq.$userId&select=user_id,series_id,created_at,series(*)")
        val json = executeSafe(request) ?: return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, WatchlistItem::class.java)
            val adapter = moshi.adapter<List<WatchlistItem>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing watchlist: ${e.message}")
            emptyList()
        }
    }

    suspend fun addToWatchlist(userId: String, seriesId: String): Boolean = withContext(Dispatchers.IO) {
        val bodyJson = "{\"user_id\": \"$userId\", \"series_id\": \"$seriesId\"}"
        val request = createRequest("/rest/v1/series_watchlist", method = "POST", jsonBody = bodyJson)
        executeSafe(request) != null
    }

    suspend fun removeFromWatchlist(userId: String, seriesId: String): Boolean = withContext(Dispatchers.IO) {
        val request = createRequest("/rest/v1/series_watchlist?user_id=eq.$userId&series_id=eq.$seriesId", method = "DELETE")
        executeSafe(request) != null
    }

    // --- Comments ---
    suspend fun getComments(seriesId: String, episodeId: String? = null): List<SeriesComment> = withContext(Dispatchers.IO) {
        val epFilter = if (episodeId != null) "&episode_id=eq.$episodeId" else ""
        val request = createRequest("/rest/v1/series_comments?series_id=eq.$seriesId$epFilter&select=*,profiles(username,display_name,avatar_url)&order=created_at.desc")
        val json = executeSafe(request)
        if (json.isNullOrBlank()) return@withContext emptyList()

        return@withContext try {
            val type = Types.newParameterizedType(List::class.java, SeriesComment::class.java)
            val adapter = moshi.adapter<List<SeriesComment>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error parsing comments: ${e.message}")
            emptyList()
        }
    }

    suspend fun postComment(seriesId: String, episodeId: String?, userId: String, parentId: String?, content: String): Boolean = withContext(Dispatchers.IO) {
        val epParam = if (episodeId != null) "\"$episodeId\"" else "null"
        val parentParam = if (parentId != null) "\"$parentId\"" else "null"
        val bodyJson = "{\"series_id\": \"$seriesId\", \"episode_id\": $epParam, \"user_id\": \"$userId\", \"parent_id\": $parentParam, \"content\": \"$content\"}"

        val request = createRequest("/rest/v1/series_comments", method = "POST", jsonBody = bodyJson)
        executeSafe(request) != null
    }

    suspend fun likeComment(commentId: String, userId: String, isLiked: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (isLiked) {
            val bodyJson = "{\"comment_id\": \"$commentId\", \"user_id\": \"$userId\"}"
            val request = createRequest("/rest/v1/series_comment_likes", method = "POST", jsonBody = bodyJson)
            executeSafe(request) != null
        } else {
            val request = createRequest("/rest/v1/series_comment_likes?comment_id=eq.$commentId&user_id=eq.$userId", method = "DELETE")
            executeSafe(request) != null
        }
    }

    // --- Report Broken Source ---
    suspend fun reportSource(sourceId: String, userId: String, reason: String, details: String?): Boolean = withContext(Dispatchers.IO) {
        val detailsParam = if (details != null) "\"$details\"" else "null"
        val bodyJson = "{\"source_id\": \"$sourceId\", \"user_id\": \"$userId\", \"reason\": \"$reason\", \"details\": $detailsParam}"
        val request = createRequest("/rest/v1/source_reports", method = "POST", jsonBody = bodyJson)
        executeSafe(request) != null
    }

    // --- Rate Series ---
    suspend fun rateSeries(seriesId: String, userId: String, rating: Int): Boolean = withContext(Dispatchers.IO) {
        val bodyJson = "{\"series_id\": \"$seriesId\", \"user_id\": \"$userId\", \"rating\": $rating}"
        val request = createRequest(
            "/rest/v1/series_ratings",
            method = "POST",
            jsonBody = bodyJson,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates")
        )
        executeSafe(request) != null
    }

    // --- Like Series ---
    suspend fun toggleSeriesLike(seriesId: String, userId: String, isLiked: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (isLiked) {
            val bodyJson = "{\"series_id\": \"$seriesId\", \"user_id\": \"$userId\"}"
            val request = createRequest("/rest/v1/series_likes", method = "POST", jsonBody = bodyJson)
            executeSafe(request) != null
        } else {
            val request = createRequest("/rest/v1/series_likes?series_id=eq.$seriesId&user_id=eq.$userId", method = "DELETE")
            executeSafe(request) != null
        }
    }

    // --- Auth (Email/Password) ---
    suspend fun signIn(email: String, password: String): Result<Profile> = withContext(Dispatchers.IO) {
        val bodyJson = "{\"email\": \"$email\", \"password\": \"$password\"}"
        val request = createRequest("/auth/v1/token?grant_type=password", method = "POST", jsonBody = bodyJson)
        val json = executeSafe(request) ?: return@withContext Result.failure(Exception("Login failed. Check your network or credentials."))

        try {
            val mapAdapter = moshi.adapter(Map::class.java)
            val parsed = mapAdapter.fromJson(json) as? Map<*, *>
            val accessToken = parsed?.get("access_token") as? String
            val userMap = parsed?.get("user") as? Map<*, *>
            val userId = userMap?.get("id") as? String ?: ""
            val userMeta = userMap?.get("user_metadata") as? Map<*, *>
            val username = userMeta?.get("username") as? String ?: "user_${userId.take(8)}"
            val displayName = userMeta?.get("display_name") as? String ?: username
            val avatarUrl = userMeta?.get("avatar_url") as? String

            if (!accessToken.isNullOrBlank() && userId.isNotBlank()) {
                prefs.saveAuthSession(accessToken, userId, username, displayName, avatarUrl)
                val profile = Profile(id = userId, username = username, displayName = displayName, avatarUrl = avatarUrl)
                return@withContext Result.success(profile)
            } else {
                return@withContext Result.failure(Exception("Invalid auth response from server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, password: String, username: String, displayName: String): Result<Profile> = withContext(Dispatchers.IO) {
        val bodyJson = """
            {
                "email": "$email",
                "password": "$password",
                "data": {
                    "username": "$username",
                    "display_name": "$displayName"
                }
            }
        """.trimIndent()
        val request = createRequest("/auth/v1/signup", method = "POST", jsonBody = bodyJson)
        val json = executeSafe(request) ?: return@withContext Result.failure(Exception("Sign up failed. Please check your connection."))

        try {
            val mapAdapter = moshi.adapter(Map::class.java)
            val parsed = mapAdapter.fromJson(json) as? Map<*, *>
            val accessToken = parsed?.get("access_token") as? String
            val userMap = parsed?.get("user") as? Map<*, *>
            val userId = userMap?.get("id") as? String ?: ""
            val profile = Profile(id = userId, username = username, displayName = displayName)

            if (!accessToken.isNullOrBlank() && userId.isNotBlank()) {
                prefs.saveAuthSession(accessToken, userId, username, displayName, null)
            }
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        prefs.clearAuthSession()
    }
}
