package com.opex

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import android.util.Log
import android.util.LruCache

object OPExUtils {
    private const val TAG = "OPExUtils"
    // Nên được nạp từ GitHub Secret qua YAML khi build
    private const val TMDB_API_KEY = "YOUR_API_KEY_HERE"
    private const val TMDB_BASE = "https://api.themoviedb.org/3"
    private const val TMDB_IMG_185 = "https://image.tmdb.org/t/p/w185"
    private const val TMDB_IMG_500 = "https://image.tmdb.org/t/p/w500"
    private const val TMDB_IMG_1280 = "https://image.tmdb.org/t/p/w1280"
    private const val NETWORK_TIMEOUT = 15L

    // Cache nhẹ trong bộ nhớ: data TMDB gần như tĩnh, tránh gọi lại API mỗi lần
    // người dùng mở lại cùng 1 phim trong phiên sử dụng app.
    private val tmdbIdCache = LruCache<String, String>(200)
    private val tmdbDetailsCache = LruCache<String, TmdbDetailResponse>(100)

    // 2 calls song song:
    // vi-VN + credits → overview/cast tiếng Việt
    // /images không language → backdrops đầy đủ không bị filter
    suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? {
        val cacheKey = "$tmdbType:$tmdbId"
        tmdbDetailsCache.get(cacheKey)?.let { return it }
        return try {
            coroutineScope {
                val textDeferred = async {
                    parseJson<TmdbDetailResponse>(
                        app.get(
                            "$TMDB_BASE/$tmdbType/$tmdbId?api_key=$TMDB_API_KEY&language=vi-VN&append_to_response=credits",
                            timeout = NETWORK_TIMEOUT
                        ).text
                    )
                }
                val imagesDeferred = async {
                    parseJson<TmdbImagesResponse>(
                        app.get("$TMDB_BASE/$tmdbType/$tmdbId/images?api_key=$TMDB_API_KEY", timeout = NETWORK_TIMEOUT).text
                    )
                }
                val result = textDeferred.await().copy(images = imagesDeferred.await())
                tmdbDetailsCache.put(cacheKey, result)
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchTmdbDetails failed for $cacheKey", e)
            null
        }
    }

    // Parse cast từ credits đã có trong TmdbDetailResponse — không cần call riêng
    fun parseCast(credits: TmdbCreditsResponse?, castCount: Int = 15): List<ActorData>? {
        return credits?.cast?.take(castCount)?.map { cast ->
            ActorData(
                Actor(cast.name ?: "", cast.profile_path?.let { "$TMDB_IMG_185$it" }),
                roleString = cast.character
            )
        }
    }

    // internal: chỉ dùng trong package com.opex (OPExProvider)
    internal suspend fun fetchTmdbSeason(tmdbId: String, seasonNumber: Int): TmdbSeasonResponse? {
        return try {
            parseJson<TmdbSeasonResponse>(
                app.get(
                    "$TMDB_BASE/tv/$tmdbId/season/$seasonNumber?api_key=$TMDB_API_KEY&language=vi-VN",
                    timeout = NETWORK_TIMEOUT
                ).text
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchTmdbSeason failed for $tmdbId season $seasonNumber", e)
            null
        }
    }


    suspend fun findTmdbId(name: String?, originName: String?, year: Int?, isSeries: Boolean): String? {
        val queryName = if (!originName.isNullOrEmpty()) originName else name
        if (queryName.isNullOrEmpty() || year == null) return null

        val tmdbType = if (isSeries) "tv" else "movie"
        val cacheKey = "$tmdbType:$queryName:$year"
        tmdbIdCache.get(cacheKey)?.let { return it }

        // encode để tránh URL sai với tên có dấu cách / tiếng Việt
        val encoded = java.net.URLEncoder.encode(queryName, "UTF-8")
        return try {
            val response = app.get(
                "$TMDB_BASE/search/$tmdbType?api_key=$TMDB_API_KEY&query=$encoded&language=vi-VN",
                timeout = NETWORK_TIMEOUT
            ).parsedSafe<TmdbSearchResponse>()

            val id = response?.results?.firstOrNull { result ->
                val rawDate = if (isSeries) result.firstAirDate else result.releaseDate
                val tmdbYear = rawDate?.take(4)?.toIntOrNull()
                // cho phép lệch 1 năm (ophim đôi khi ghi năm sản xuất, TMDB ghi năm phát sóng)
                tmdbYear != null && kotlin.math.abs(tmdbYear - year) <= 1
            }?.id?.toString()
            if (id != null) tmdbIdCache.put(cacheKey, id)
            id
        } catch (e: Exception) {
            Log.e(TAG, "findTmdbId failed for $queryName ($year)", e)
            null
        }
    }
}

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
    @param:JsonProperty("imdb") val imdb: OPImdbListItem? = null,
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

data class TmdbCreditsResponse(
    @param:JsonProperty("cast") val cast: List<TmdbCast>? = null
)
data class TmdbCast(
    @param:JsonProperty("name")         val name: String?,
    @param:JsonProperty("profile_path") val profile_path: String?,
    @param:JsonProperty("character")    val character: String?
)

data class TmdbDetailResponse(
    @param:JsonProperty("vote_average") val vote_average: Double?,
    @param:JsonProperty("poster_path") val poster_path: String?,
    @param:JsonProperty("backdrop_path") val backdrop_path: String?,
    @param:JsonProperty("overview") val overview: String?,
    @param:JsonProperty("original_name") val original_name: String?,
    @param:JsonProperty("original_title") val original_title: String?,
    @param:JsonProperty("images") val images: TmdbImagesResponse? = null,
    @param:JsonProperty("credits") val credits: TmdbCreditsResponse? = null
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
    @param:JsonProperty("posters") val posters: List<TmdbImage>? = null
)

data class TmdbImage(
    @param:JsonProperty("file_path") val filePath: String? = null,
    @param:JsonProperty("width") val width: Int? = null,
    @param:JsonProperty("height") val height: Int? = null,
    @param:JsonProperty("vote_average") val voteAverage: Double? = null
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
