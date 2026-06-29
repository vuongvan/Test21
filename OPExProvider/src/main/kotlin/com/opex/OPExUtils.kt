package com.opex

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty

object OPExUtils {
    private const val TMDB_API_KEY  = "YOUR_API_KEY_HERE"
    private const val TMDB_BASE     = "https://api.themoviedb.org/3"
    private const val TMDB_IMG_185  = "https://image.tmdb.org/t/p/w185"
    const val         TMDB_IMG_500  = "https://image.tmdb.org/t/p/w500"
    const val         TMDB_IMG_1280 = "https://image.tmdb.org/t/p/w1280"

    // 2 calls song song:
    // - vi-VN + credits → overview/cast tiếng Việt
    // - /images (không language) → backdrops đầy đủ
    suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? =
        try {
            coroutineScope {
                val textDeferred = async {
                    parseJson<TmdbDetailResponse>(
                        app.get("$TMDB_BASE/$tmdbType/$tmdbId?api_key=$TMDB_API_KEY&language=vi-VN&append_to_response=credits").text
                    )
                }
                val imagesDeferred = async {
                    parseJson<TmdbImagesResponse>(
                        app.get("$TMDB_BASE/$tmdbType/$tmdbId/images?api_key=$TMDB_API_KEY").text
                    )
                }
                textDeferred.await()?.copy(images = imagesDeferred.await())
            }
        } catch (e: Exception) { null }

    // Parse cast từ credits — không cần call riêng
    fun parseCast(credits: TmdbCreditsResponse?, castCount: Int = 15): List<ActorData>? =
        credits?.cast?.take(castCount)?.map { cast ->
            ActorData(
                Actor(cast.name ?: "", cast.profile_path?.let { "$TMDB_IMG_185$it" }),
                roleString = cast.character
            )
        }

    internal suspend fun fetchTmdbSeason(tmdbId: String, seasonNumber: Int): TmdbSeasonResponse? =
        try {
            parseJson<TmdbSeasonResponse>(
                app.get("$TMDB_BASE/tv/$tmdbId/season/$seasonNumber?api_key=$TMDB_API_KEY&language=vi-VN").text
            )
        } catch (e: Exception) { null }

    suspend fun findTmdbId(name: String?, originName: String?, year: Int?, isSeries: Boolean): String? {
        val queryName = if (!originName.isNullOrEmpty()) originName else name
        if (queryName.isNullOrEmpty() || year == null) return null
        val tmdbType = if (isSeries) "tv" else "movie"
        val encoded = java.net.URLEncoder.encode(queryName, "UTF-8")
        return try {
            app.get("$TMDB_BASE/search/$tmdbType?api_key=$TMDB_API_KEY&query=$encoded&language=vi-VN")
                .parsedSafe<TmdbSearchResponse>()
                ?.results?.firstOrNull { result ->
                    val rawDate = if (isSeries) result.firstAirDate else result.releaseDate
                    val tmdbYear = rawDate?.take(4)?.toIntOrNull()
                    tmdbYear != null && kotlin.math.abs(tmdbYear - year) <= 1
                }?.id?.toString()
        } catch (e: Exception) { null }
    }
    // Nên được nạp từ GitHub Secret qua YAML khi build





    // Trả MAL ID để CS3 tracker tự fetch nhân vật anime với ảnh artwork
// --- Data Classes ---

data class OPListResponse(
    @param:JsonProperty("data") val data: OPListData? = null,
    @param:JsonProperty("items") val items: List<OPItem>? = null,
    @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null
)

data class OPListData(
    @param:JsonProperty("items") val items: List<OPItem>? = null,
    @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null
)

data class OPItem(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("imdb") val imdb: OPImdbListItem? = null
)

data class OPTmdb(
    @param:JsonProperty("vote_average") val vote_average: Double? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("season") val season: Int? = null
)

data class OPImdbListItem(@param:JsonProperty("vote_average") val vote_average: Double? = null)

data class OPRootResponse(@param:JsonProperty("data") val data: OPDataContent? = null)

data class OPDataContent(
    @param:JsonProperty("item") val item: OPItemDetail? = null,
    @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null
)


data class OPItemDetail(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("origin_name") val origin_name: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("country") val country: List<OPCountry>? = null,
    @param:JsonProperty("category") val category: List<OPCat>? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("episodes") val episodes: List<OPServer>? = null
)

data class OPCountry(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null
)

data class OPCat(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null
)

data class OPServer(
    @param:JsonProperty("server_name") val server_name: String? = null,
    @param:JsonProperty("server_data") val server_data: List<OPEpisode>? = null
)

data class OPEpisode(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("link_m3u8") val link_m3u8: String? = null
)

// ── TMDB Data Classes ─────────────────────────────────────────────────────────

data class TmdbCreditsResponse(
    @param:JsonProperty("cast") val cast: List<TmdbCast>? = null
)

data class TmdbCast(
    @param:JsonProperty("name")         val name: String?,
    @param:JsonProperty("character")    val character: String?,
    @param:JsonProperty("profile_path") val profile_path: String?
)

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
