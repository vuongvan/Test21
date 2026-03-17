package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.text.SimpleDateFormat
import java.util.Locale

object OPExUtils {
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

    private suspend fun fetchTmdbSeason(tmdbId: String, seasonNumber: Int = 1): TmdbSeasonResponse? {
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$TMDB_API_KEY&language=vi-VN"
        return try { parseJson<TmdbSeasonResponse>(app.get(url).text) } catch (e: Exception) { null }
    }

    suspend fun getMergedEpisodes(
        api: MainAPI,
        tmdbId: String?, 
        ophimServers: List<OPServer>?,
        isSeries: Boolean
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

        val tmdbSeason = if (isSeries) fetchTmdbSeason(tmdbId, 1) else null
        val tmdbEpsMap = tmdbSeason?.episodes?.associateBy { it.episode_number }

        return ophimEpsMap.map { (num, data) ->
            val tmdbEp = tmdbEpsMap?.get(num)
            api.newEpisode(data.second) {
                // Tên tập phim lấy từ TMDB nếu có, không thì dùng tên của OPhim
                this.name = tmdbEp?.name ?: (if (data.first.contains("Tập", true)) data.first else "Tập ${data.first}")
                this.episode = num
                this.posterUrl = tmdbEp?.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.description = tmdbEp?.overview
                
                // Hiển thị đánh giá và ngày chiếu (giống ảnh 3)
                val rating = tmdbEp?.vote_average
                //val date = formatDate(tmdbEp?.air_date)
            
                if (rating != null && rating > 0) {
                    this.score = Score.from10(rating) // Cloudstream dùng thang điểm 1000 cho rating nội bộ hoặc hiển thị text
                }
                
                // Gán ngày chiếu để hiện dưới tên tập
                this.addDate(tmdbEp?.air_date)
                
            }
        }.sortedBy { it.episode }
    }
}

// --- Data Classes cập nhật thêm các field TMDB ---
data class TmdbSeasonResponse(val episodes: List<TmdbEpisode>?)
data class TmdbEpisode(
    val episode_number: Int?, 
    val name: String?, 
    val overview: String?, 
    val still_path: String?,
    val air_date: String?,
    val vote_average: Double?
)

// Các data class khác giữ nguyên như bản trước...
data class OPListResponse(@param:JsonProperty("data") val data: OPListData? = null, @param:JsonProperty("items") val items: List<OPItem>? = null, @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null)
data class OPListData(@param:JsonProperty("items") val items: List<OPItem>? = null, @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null)
data class OPItem(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("slug") val slug: String? = null, @param:JsonProperty("poster_url") val poster_url: String? = null, @param:JsonProperty("thumb_url") val thumb_url: String? = null, @param:JsonProperty("quality") val quality: String? = null, @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null, @param:JsonProperty("imdb") val imdb: OPImdbListItem? = null, @param:JsonProperty("episode_current") val episode_current: String? = null)
data class OPImdbListItem(@param:JsonProperty("vote_average") val vote_average: Double? = null)
data class OPRootResponse(@param:JsonProperty("data") val data: OPDataContent? = null)
data class OPDataContent(@param:JsonProperty("item") val item: OPItemDetail? = null, @param:JsonProperty("seoOnPage") val seoOnPage: OPSeoOnPage? = null, @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null)
data class OPSeoOnPage(@param:JsonProperty("seoSchema") val seoSchema: OPSeoSchema? = null)
data class OPSeoSchema(@param:JsonProperty("image") val image: String? = null)
data class OPItemDetail(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("content") val content: String? = null, @param:JsonProperty("status") val status: String? = null, @param:JsonProperty("year") val year: Int? = null, @param:JsonProperty("episode_current") val episode_current: String? = null, @param:JsonProperty("episode_total") val episode_total: String? = null, @param:JsonProperty("lang") val lang: String? = null, @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null, @param:JsonProperty("category") val category: List<OPCat>? = null, @param:JsonProperty("poster_url") val poster_url: String? = null, @param:JsonProperty("thumb_url") val thumb_url: String? = null, @param:JsonProperty("episodes") val episodes: List<OPServer>? = null)
data class OPTmdb(@param:JsonProperty("vote_average") val vote_average: Double? = null, @param:JsonProperty("id") val id: Any? = null)
data class OPCat(@param:JsonProperty("name") val name: String? = null)
data class OPServer(@param:JsonProperty("server_name") val server_name: String? = null, @param:JsonProperty("server_data") val server_data: List<OPEpisode>? = null)
data class OPEpisode(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("link_m3u8") val link_m3u8: String? = null)
data class TmdbCreditsResponse(val cast: List<TmdbCast>?)
data class TmdbCast(val name: String?, val profile_path: String?, val character: String?)
data class TmdbDetailResponse(val vote_average: Double?, val overview: String?)
