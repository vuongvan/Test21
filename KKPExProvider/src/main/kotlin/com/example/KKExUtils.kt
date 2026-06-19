package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import java.util.Locale
import android.content.Context

object KKExUtils {

    private val tmdbApiKey = "YOUR_API_KEY_HERE" // Giữ nguyên chữ này để lệnh sed tìm thấy

    fun fixPosterUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return if (url.startsWith("http")) url else "https://phimimg.com/$url"
    }

    suspend fun fetchTmdbCast(tmdbType: String, tmdbId: String): List<ActorData>? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/credits?api_key=$tmdbApiKey&language=vi-VN"
        return try {
            val res = app.get(url).parsedSafe<TmdbCreditsResponse>()
            res?.cast?.take(15)?.map { cast ->
                val actorImg = cast.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" }
                ActorData(Actor(cast.name ?: "", actorImg), roleString = cast.character)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$tmdbApiKey&language=vi-VN"
        return try {
            app.get(url).parsedSafe<TmdbDetailResponse>()
        } catch (e: Exception) { null }
    }

    /**
     * [TỐI ƯU] Gọi song song 2 API TMDB (cast + details) thay vì tuần tự.
     * Tiết kiệm ~50% thời gian chờ mạng trong hàm load().
     */
    suspend fun fetchTmdbCastAndDetails(
        tmdbType: String,
        tmdbId: String
    ): Pair<List<ActorData>?, TmdbDetailResponse?> = coroutineScope {
        val castDeferred    = async { fetchTmdbCast(tmdbType, tmdbId) }
        val detailsDeferred = async { fetchTmdbDetails(tmdbType, tmdbId) }
        Pair(castDeferred.await(), detailsDeferred.await())
    }
}

// --- DATA MODELS ---
data class KKListResponse(
    @param:JsonProperty("items") val items: List<KKItem>? = null,
    @param:JsonProperty("data") val data: KKListData? = null
)

data class KKSearchResponse(
    @param:JsonProperty("data") val data: KKListData? = null
)

data class KKListData(
    @param:JsonProperty("items") val items: List<KKItem>? = null
)

data class KKItem(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("tmdb") val tmdb: KKTMDB? = null
)

data class KKDetailResponse(
    @param:JsonProperty("movie") val movie: KKMovie? = null,
    @param:JsonProperty("episodes") val episodes: List<KKServer>? = null
)

data class KKMovie(
    @param:JsonProperty("name") val name: String? = null,
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

data class KKCategory(@param:JsonProperty("name") val name: String? = null)
data class KKCountry(@param:JsonProperty("name") val name: String? = null)

data class KKServer(
    @param:JsonProperty("server_name") val server_name: String? = null,
    @param:JsonProperty("server_data") val server_data: List<KKEpisode>? = null
)

data class KKEpisode(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("link_m3u8") val link_m3u8: String? = null
)

data class KKTMDB(
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("vote_average") val vote_average: Double? = null
)

data class TmdbResponse(
    @param:JsonProperty("credits") val credits: TmdbCredits? = null
)

data class TmdbCredits(
    @param:JsonProperty("cast") val cast: List<TmdbCast>? = null
)

data class TmdbCast(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("character") val character: String? = null,
    @param:JsonProperty("profile_path") val profile_path: String? = null
)

data class TmdbCreditsResponse(
    val cast: List<TmdbCast>? = null
)

data class TmdbTvInfo(
    @param:JsonProperty("vote_average") val voteAverage: Double? = null
)

data class TmdbMovieInfo(
    @param:JsonProperty("vote_average") val voteAverage: Double? = null
)

data class TmdbDetailResponse(
    @param:JsonProperty("vote_average") val vote_average: Double? = null,
    @param:JsonProperty("overview") val overview: String? = null
)
