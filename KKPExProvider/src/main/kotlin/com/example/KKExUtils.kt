package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import android.content.Context

object KKExUtils {

    // [FIX 4] Bỏ TMDB_API_KEY thừa — chỉ cần 1 biến
    private val tmdbApiKey = "YOUR_API_KEY_HERE" // Giữ nguyên chữ này để lệnh sed tìm thấy

    fun fixPosterUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return if (url.startsWith("http")) url else "https://phimimg.com/$url"
    }

    suspend fun fetchTmdbCast(tmdbType: String, tmdbId: String, castCount: Int = 15): List<ActorData>? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/credits?api_key=$tmdbApiKey&language=vi-VN"
        return try {
            val res = app.get(url).parsedSafe<TmdbCreditsResponse>()
            res?.cast?.take(castCount)?.map { cast ->
                val actorImg = cast.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" }
                ActorData(Actor(cast.name ?: "", actorImg), roleString = cast.character)
            }
        } catch (e: Exception) { null }
    }

    suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$tmdbApiKey&language=vi-VN"
        return try {
            app.get(url).parsedSafe<TmdbDetailResponse>()
        } catch (e: Exception) { null }
    }

    suspend fun fetchTmdbSeason(tmdbId: String, seasonNumber: Int): TmdbSeasonResponse? {
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbApiKey&language=vi-VN"
        return try { parseJson<TmdbSeasonResponse>(app.get(url).text) } catch (e: Exception) { null }
    }

    suspend fun findTmdbId(name: String?, originName: String?, year: Int?, isSeries: Boolean): String? {
        val tmdbType = if (isSeries) "tv" else "movie"
        val queryName = if (!originName.isNullOrEmpty()) originName else name
        if (queryName.isNullOrEmpty() || year == null) return null

        val searchUrl = "https://api.themoviedb.org/3/search/$tmdbType?api_key=$tmdbApiKey&query=$queryName&language=vi-VN"
        return try {
            val response = app.get(searchUrl).parsedSafe<TmdbSearchResponse>()
            response?.results?.firstOrNull { result ->
                val rawDate = if (isSeries) result.firstAirDate else result.releaseDate
                val tmdbYear = rawDate?.take(4)?.toIntOrNull()
                tmdbYear != null && tmdbYear == year
            }?.id?.toString()
        } catch (e: Exception) { null }
    }

    suspend fun fetchTmdbBackdrops(tmdbType: String, tmdbId: String): List<String> {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/images?api_key=$tmdbApiKey"
        return try {
            val response = parseJson<TmdbImagesResponse>(app.get(url).text)
            response.backdrops
                ?.mapNotNull { it.filePath }
                ?.map { "https://image.tmdb.org/t/p/w1280$it" }
                ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // =========================================================================
    // Bundle cho PHIM LẺ: cast + details + backdrops chạy song song (3 calls)
    // =========================================================================
    data class TmdbBundle(
        val cast: List<ActorData>?,
        val details: TmdbDetailResponse?,
        val backdrops: List<String>
    )

    suspend fun fetchTmdbBundle(tmdbType: String, tmdbId: String, castCount: Int = 15): TmdbBundle = coroutineScope {
        // Launch tất cả song song, await riêng từng cái — type-safe hơn awaitAll với mixed types
        val castDeferred     = async { fetchTmdbCast(tmdbType, tmdbId, castCount) }
        val detailsDeferred  = async { fetchTmdbDetails(tmdbType, tmdbId) }
        val backdropDeferred = async { fetchTmdbBackdrops(tmdbType, tmdbId) }
        TmdbBundle(
            cast      = castDeferred.await(),
            details   = detailsDeferred.await(),
            backdrops = backdropDeferred.await()
        )
    }

    // =========================================================================
    // Bundle cho PHIM BỘ: cast + details + backdrops + season chạy song song (4 calls)
    // =========================================================================
    data class TmdbSeriesBundle(
        val cast: List<ActorData>?,
        val details: TmdbDetailResponse?,
        val backdrops: List<String>,
        val season: TmdbSeasonResponse?
    )

    suspend fun fetchTmdbSeriesBundle(tmdbId: String, seasonNumber: Int, castCount: Int = 15): TmdbSeriesBundle = coroutineScope {
        // Launch tất cả song song, await riêng từng cái — type-safe hơn awaitAll với mixed types
        val castDeferred     = async { fetchTmdbCast("tv", tmdbId, castCount) }
        val detailsDeferred  = async { fetchTmdbDetails("tv", tmdbId) }
        val backdropDeferred = async { fetchTmdbBackdrops("tv", tmdbId) }
        val seasonDeferred   = async { fetchTmdbSeason(tmdbId, seasonNumber) }
        TmdbSeriesBundle(
            cast      = castDeferred.await(),
            details   = detailsDeferred.await(),
            backdrops = backdropDeferred.await(),
            season    = seasonDeferred.await()
        )
    }
}

// --- DATA MODELS ---
data class KKListResponse(
    @param:JsonProperty("items") val items: List<KKItem>? = null,
    @param:JsonProperty("data") val data: KKListData? = null
)

data class KKSearchResponse(
    @param:JsonProperty("data") val data: KKSearchData? = null
)

data class KKSearchData(
    @param:JsonProperty("items") val items: List<KKItem>? = null
)

data class KKListData(
    @param:JsonProperty("items") val items: List<KKItem>? = null
)

data class KKItem(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("tmdb") val tmdb: KKTMDB? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("quality") val quality: String? = null
)

data class KKDetailResponse(
    @param:JsonProperty("movie") val movie: KKMovie? = null,
    @param:JsonProperty("episodes") val episodes: List<KKServer>? = null
)

data class KKMovie(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("origin_name") val origin_name: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("actor") val actor: List<String>? = null,
    @param:JsonProperty("tmdb") val tmdb: KKTMDB? = null,
    @param:JsonProperty("category") val category: List<KKCategory>? = null,
    @param:JsonProperty("country") val country: List<KKCountry>? = null,
    @param:JsonProperty("lang") val lang: String? = null
)

data class KKCountry(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null
)

data class KKCategory(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null
)

data class KKServer(
    @param:JsonProperty("server_name") val server_name: String? = null,
    @param:JsonProperty("server_data") val server_data: List<KKEpisode>? = null
)

data class KKEpisode(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("link_m3u8") val link_m3u8: String? = null,
    @param:JsonProperty("link_sub") val link_sub: String? = null
)

data class KKTMDB(
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("season") val season: Int? = null,
    @param:JsonProperty("vote_average") val vote_average: Double? = null
)

data class TmdbCast(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("character") val character: String? = null,
    @param:JsonProperty("profile_path") val profile_path: String? = null
)

data class TmdbCreditsResponse(
    val cast: List<TmdbCast>? = null
)

data class TmdbDetailResponse(
    @param:JsonProperty("vote_average") val vote_average: Double? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("backdrop_path") val backdrop_path: String? = null,
    @param:JsonProperty("poster_path") val poster_path: String? = null
)

data class TmdbSeasonResponse(
    @param:JsonProperty("episodes") val episodes: List<TmdbEpisodeDetail>? = null
)

data class TmdbEpisodeDetail(
    @param:JsonProperty("episode_number") val episodeNumber: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("still_path") val stillPath: String? = null,
    @param:JsonProperty("air_date") val airDate: String? = null,
    @param:JsonProperty("runtime") val runTime: Int? = null,
    @param:JsonProperty("vote_average") val voteAverage: Double? = null
)

data class TmdbImagesResponse(
    @param:JsonProperty("backdrops") val backdrops: List<TmdbImage>? = null,
    @param:JsonProperty("posters") val posters: List<TmdbImage>? = null
)

data class TmdbImage(
    @param:JsonProperty("file_path") val filePath: String? = null,
    @param:JsonProperty("width") val width: Int? = null,
    @param:JsonProperty("height") val height: Int? = null
)

data class TmdbSearchResponse(
    @param:JsonProperty("results") val results: List<TmdbSearchResult>? = null
)

data class TmdbSearchResult(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("first_air_date") val firstAirDate: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("release_date") val releaseDate: String? = null
)
