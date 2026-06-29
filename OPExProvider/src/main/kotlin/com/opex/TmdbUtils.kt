// TmdbUtils.kt — SHARED FILE
// Dùng chung cho OPExProvider và KKPExProvider
// Khi sửa file này, copy sang cả 2 plugin
// OPExProvider: package com.opex
// KKPExProvider: package com.example
// → Chỉ cần đổi dòng package, còn lại giữ nguyên

package com.opex // ← đổi thành com.example khi copy sang KKPExProvider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.async

// ─────────────────────────────────────────────────────────────────────────────
// TMDB API
// ─────────────────────────────────────────────────────────────────────────────

object TmdbUtils {

    private const val BASE     = "https://api.themoviedb.org/3"
    private const val API_KEY  = "YOUR_API_KEY_HERE"
    const val IMG_185  = "https://image.tmdb.org/t/p/w185"
    const val IMG_500  = "https://image.tmdb.org/t/p/w500"
    const val IMG_1280 = "https://image.tmdb.org/t/p/w1280"

    // 2 calls song song: vi-VN cho text (overview, credits) + không language cho images
    // Không gộp được vì language filter ảnh hưởng cả images nếu dùng append_to_response
    suspend fun fetchDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? =
        try {
            kotlinx.coroutines.coroutineScope {
                val textDeferred = async {
                    parseJson<TmdbDetailResponse>(
                        app.get("$BASE/$tmdbType/$tmdbId?api_key=$API_KEY&language=vi-VN&append_to_response=credits").text
                    )
                }
                val imagesDeferred = async {
                    parseJson<TmdbImagesResponse>(
                        app.get("$BASE/$tmdbType/$tmdbId/images?api_key=$API_KEY").text
                    )
                }
                val text = textDeferred.await()
                val images = imagesDeferred.await()
                text?.copy(images = images)
            }
        } catch (e: Exception) { null }

    // Season — không gộp được vào append_to_response
    suspend fun fetchSeason(tmdbId: String, seasonNumber: Int): TmdbSeasonResponse? =
        try {
            parseJson<TmdbSeasonResponse>(
                app.get("$BASE/tv/$tmdbId/season/$seasonNumber?api_key=$API_KEY").text
            )
        } catch (e: Exception) { null }

    // Parse cast từ credits đã có trong details — không cần call riêng
    fun parseCast(credits: TmdbCreditsResponse?, limit: Int = 15): List<ActorData>? =
        credits?.cast?.take(limit)?.map { cast ->
            ActorData(
                Actor(cast.name ?: "", cast.profile_path?.let { "$IMG_185$it" }),
                roleString = cast.character
            )
        }

    // Backdrop random từ images đã có trong details
    fun pickBackdrop(details: TmdbDetailResponse?, fallback: String): String =
        details?.images?.backdrops
            ?.mapNotNull { it.filePath?.let { p -> "$IMG_1280$p" } }
            ?.randomOrNull()
            ?: details?.backdrop_path?.let { "$IMG_1280$it" }
            ?: fallback

    // Tìm TMDB ID — encode tên + tolerance ±1 năm
    suspend fun findId(name: String?, originName: String?, year: Int?, isSeries: Boolean): String? {
        val query = (if (!originName.isNullOrEmpty()) originName else name) ?: return null
        if (year == null) return null
        val tmdbType = if (isSeries) "tv" else "movie"
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            parseJson<TmdbSearchResponse>(
                app.get("$BASE/search/$tmdbType?api_key=$API_KEY&query=$encoded").text
            ).results?.firstOrNull { result ->
                val rawDate = if (isSeries) result.firstAirDate else result.releaseDate
                val tmdbYear = rawDate?.take(4)?.toIntOrNull()
                tmdbYear != null && kotlin.math.abs(tmdbYear - year) <= 1
            }?.id?.toString()
        } catch (e: Exception) { null }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TMDB DATA CLASSES — dùng chung, không phụ thuộc data class của từng plugin
// ─────────────────────────────────────────────────────────────────────────────

data class TmdbDetailResponse(
    @param:JsonProperty("vote_average")  val vote_average: Double?,
    @param:JsonProperty("poster_path")   val poster_path: String?,
    @param:JsonProperty("backdrop_path") val backdrop_path: String?,
    @param:JsonProperty("overview")      val overview: String?,
    @param:JsonProperty("original_name") val original_name: String?,
    @param:JsonProperty("original_title")val original_title: String?,
    @param:JsonProperty("images")        val images: TmdbImagesResponse? = null,
    @param:JsonProperty("credits")       val credits: TmdbCreditsResponse? = null
)

data class TmdbCreditsResponse(
    @param:JsonProperty("cast") val cast: List<TmdbCast>? = null
)

data class TmdbCast(
    @param:JsonProperty("name")         val name: String?,
    @param:JsonProperty("character")    val character: String?,
    @param:JsonProperty("profile_path") val profile_path: String?
)

data class TmdbSeasonResponse(
    @param:JsonProperty("episodes") val episodes: List<TmdbEpisode>?
)

data class TmdbEpisode(
    @param:JsonProperty("episode_number") val episode_number: Int?,
    @param:JsonProperty("runtime")        val runtime: Int?,
    @param:JsonProperty("name")           val name: String?,
    @param:JsonProperty("overview")       val overview: String?,
    @param:JsonProperty("still_path")     val still_path: String?,
    @param:JsonProperty("air_date")       val air_date: String?,
    @param:JsonProperty("vote_average")   val vote_average: Double?
)

data class TmdbImagesResponse(
    @param:JsonProperty("backdrops") val backdrops: List<TmdbImage>? = null,
    @param:JsonProperty("posters")   val posters: List<TmdbImage>? = null
)

data class TmdbImage(
    @param:JsonProperty("file_path")    val filePath: String? = null,
    @param:JsonProperty("width")        val width: Int? = null,
    @param:JsonProperty("height")       val height: Int? = null,
    @param:JsonProperty("vote_average") val voteAverage: Double? = null
)

data class TmdbSearchResponse(
    @param:JsonProperty("results") val results: List<TmdbSearchResult>? = null
)

data class TmdbSearchResult(
    @param:JsonProperty("id")             val id: Int?,
    @param:JsonProperty("name")           val name: String?,
    @param:JsonProperty("first_air_date") val firstAirDate: String?,
    @param:JsonProperty("title")          val title: String?,
    @param:JsonProperty("release_date")   val releaseDate: String?
)
