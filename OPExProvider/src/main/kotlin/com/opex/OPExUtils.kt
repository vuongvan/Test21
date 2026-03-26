package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.text.SimpleDateFormat
import java.util.Locale

object OPExUtils {
    // Lưu ý: Key này nên được nạp từ GitHub Secret thông qua file YAML khi build
    private const val TMDB_API_KEY = "YOUR_API_KEY_HERE"

    fun fixImgUrl(url: String?, domain: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http")) return url
        val cleanDomain = domain?.removeSuffix("/") ?: "https://img.ophim1.com"
        val cleanPath = url.removePrefix("/")
        return if (cleanPath.startsWith("uploads/")) "$cleanDomain/$cleanPath" 
               else "$cleanDomain/uploads/movies/$cleanPath"
    }

    private fun formatDate(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("d 'tháng' M, yyyy", Locale("vi"))
            val date = inputFormat.parse(dateStr)
            date?.let { outputFormat.format(it) }
        } catch (e: Exception) {
            dateStr
        }
    }

    suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$TMDB_API_KEY&language=vi-VN"
        return try { parseJson<TmdbDetailResponse>(app.get(url).text) } catch (e: Exception) { null }
    }

    suspend fun fetchTmdbCast(tmdbType: String, tmdbId: String): List<ActorData>? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/credits?api_key=$TMDB_API_KEY&language=vi-VN"
        return try {
            val res = parseJson<TmdbCreditsResponse>(app.get(url).text)
            res.cast?.take(15)?.map { 
                ActorData(Actor(it.name ?: "", it.profile_path?.let { p -> "https://image.tmdb.org/t/p/w185$p" }), roleString = it.character)
            }
        } catch (e: Exception) { null }
    }

    private suspend fun fetchTmdbSeason(tmdbId: String, seasonNumber: Int): TmdbSeasonResponse? {
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$TMDB_API_KEY&language=vi-VN"
        return try { parseJson<TmdbSeasonResponse>(app.get(url).text) } catch (e: Exception) { null }
    }

    suspend fun fetchTmdbBackdrops(tmdbType: String, tmdbId: String): List<String> {
        // Endpoint: /movie/{id}/images hoặc /tv/{id}/images
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/images?api_key=$TMDB_API_KEY"
        return try {
            val response = parseJson<TmdbImagesResponse>(app.get(url).text)
            // Lấy file_path của tất cả backdrop và xây dựng link full
            response.backdrops?.mapNotNull { it.filePath }
                ?.map { "https://image.tmdb.org/t/p/w1280$it" } // Dùng độ phân giải cao cho backdrop
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

        suspend fun findTmdbId(name: String?, originName: String?, year: Int?, isSeries: Boolean): String? {
        val tmdbType = if (isSeries) "tv" else "movie"
        
        // Ưu tiên tìm bằng tên gốc (Origin Name) trước, nếu không có thì dùng tên tiếng Việt
        val queryName = if (!originName.isNullOrEmpty()) originName else name
        if (queryName.isNullOrEmpty() || year == null) return null

        val searchUrl = "https://api.themoviedb.org/3/search/$tmdbType?api_key=$TMDB_API_KEY&query=$queryName&language=vi-VN"
        
        try {
            val response = app.get(searchUrl).parsedSafe<TmdbSearchResponse>()
            val results = response?.results ?: return null

            for (result in results) {
                // Lấy năm phát hành từ TMDB (cắt 4 ký tự đầu của chuỗi ngày tháng)
                val rawDate = if (isSeries) result.firstAirDate else result.releaseDate
                val tmdbYear = rawDate?.take(4)?.toIntOrNull()

                // Nếu năm khớp (hoặc chênh lệch tối đa 1 năm để trừ hao)
                if (tmdbYear != null && Math.abs(tmdbYear - year) == 0) {
                    return result.id?.toString()
                }
            }
        } catch (e: Exception) {
            return null
        }
        
        return null
        }
        

    suspend fun getMergedEpisodes(
        api: MainAPI,
        tmdbId: String?, 
        ophimServers: List<OPServer>?,
        isSeries: Boolean,
        seasonNumber: Int = 1
    ): List<Episode> {
        val ophimEpsMap = mutableMapOf<Int, Pair<String, String>>()
        
        ophimServers?.forEach { server ->
            val sName = server.server_name ?: "Server"
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: ""
                val epNum = Regex("""(\d+)""").find(epName)?.value?.toIntOrNull() ?: 1
                val current = ophimEpsMap[epNum]
                val newLinks = if (current == null) "${ep.link_m3u8}|$sName" else "${current.second},${ep.link_m3u8}|$sName"
                ophimEpsMap[epNum] = Pair(epName, newLinks)
            }
        }

        if (tmdbId == null) {
            return ophimEpsMap.map { (num, data) ->
                api.newEpisode(data.second) {
                    this.name = if (data.first.contains("Tập", true)) data.first else "Tập ${data.first}"
                    this.episode = num
                }
            }.sortedBy { it.episode }
        }

        val tmdbSeason = if (isSeries) fetchTmdbSeason(tmdbId, seasonNumber) else null
        val tmdbEpsMap = tmdbSeason?.episodes?.associateBy { it.episode_number }

        return ophimEpsMap.map { (num, data) ->
            val tmdbEp = tmdbEpsMap?.get(num)
            api.newEpisode(data.second) {
                // Hiển thị tên tập từ TMDB nếu có (VD: 1. Khởi đầu...)
                this.name = tmdbEp?.name ?: (if (data.first.contains("Tập", true)) data.first else "Tập ${data.first}")
                this.episode = num
                this.posterUrl = tmdbEp?.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.description = tmdbEp?.overview
                this.runTime = tmdbEp?.runtime
                // Xử lý điểm đánh giá theo thang điểm 10 của Cloudstream
                val rating = tmdbEp?.vote_average
                if (rating != null && rating > 0) {
                    this.score = Score.from10(rating)
                }
                
                // Định dạng ngày chiếu sang tiếng Việt và gán vào UI
                this.addDate(tmdbEp?.air_date)
            }
        }.sortedBy { it.episode }
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
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null, 
    @param:JsonProperty("imdb") val imdb: OPImdbListItem? = null, 
    @param:JsonProperty("episode_current") val episode_current: String? = null
)

data class OPTmdb(
    @param:JsonProperty("vote_average") val vote_average: Double? = null, 
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("season") val season: Int? = null // Đã thêm để lấy thông tin mùa
)

data class OPImdbListItem(@param:JsonProperty("vote_average") val vote_average: Double? = null)

data class OPRootResponse(@param:JsonProperty("data") val data: OPDataContent? = null)

data class OPDataContent(
    @param:JsonProperty("item") val item: OPItemDetail? = null, 
    @param:JsonProperty("seoOnPage") val seoOnPage: OPSeoOnPage? = null, 
    @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null
)

data class OPSeoOnPage(@param:JsonProperty("seoSchema") val seoSchema: OPSeoSchema? = null)
data class OPSeoSchema(@param:JsonProperty("image") val image: String? = null)

data class OPItemDetail(
    @param:JsonProperty("name") val name: String? = null, 
    @param:JsonProperty("content") val content: String? = null, 
    @param:JsonProperty("origin_name") val origin_name: String? = null, 
    @param:JsonProperty("status") val status: String? = null, 
    @param:JsonProperty("year") val year: Int? = null, 
    @param:JsonProperty("episode_current") val episode_current: String? = null, 
    @param:JsonProperty("episode_total") val episode_total: String? = null, 
    @param:JsonProperty("lang") val lang: String? = null, 
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null, 
    @param:JsonProperty("country") val country: List<OPCountry>? = null, // Thêm dòng này
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
data class OPServer(@param:JsonProperty("server_name") val server_name: String? = null, @param:JsonProperty("server_data") val server_data: List<OPEpisode>? = null)
data class OPEpisode(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("link_m3u8") val link_m3u8: String? = null)

data class TmdbCreditsResponse(val cast: List<TmdbCast>?)
data class TmdbCast(val name: String?, val profile_path: String?, val character: String?)
data class TmdbDetailResponse(
    val vote_average: Double?, 
    val poster_path: String?,
    val backdrop_path: String?, // Ảnh ngang (Backdrop/Cover)
    val overview: String?
        )
data class TmdbSeasonResponse(val episodes: List<TmdbEpisode>?)
data class TmdbEpisode(
    val episode_number: Int?, 
    val runtime: Int?, 
    val name: String?, 
    val overview: String?, 
    val still_path: String?,
    val air_date: String?,
    val vote_average: Double?
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

    // --- DATA CLASSES CHO CHỨC NĂNG TÌM KIẾM TMDB ---
data class TmdbSearchResponse(
    @param:JsonProperty("results") val results: List<TmdbSearchResult>? = null
)

data class TmdbSearchResult(
    @param:JsonProperty("id") val id: Int? = null,
    // TMDB dùng 'name' và 'first_air_date' cho TV
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("first_air_date") val firstAirDate: String? = null,
    // TMDB dùng 'title' và 'release_date' cho Movie
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("release_date") val releaseDate: String? = null
)
