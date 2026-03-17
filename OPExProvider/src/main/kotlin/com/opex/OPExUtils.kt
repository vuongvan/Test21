package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parsedSafe
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Locale
import android.content.Context

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

    suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$TMDB_API_KEY&language=vi-VN"
        return try { app.get(url).parsedSafe<TmdbDetailResponse>() } catch (e: Exception) { null }
    }

    suspend fun fetchTmdbCast(tmdbType: String, tmdbId: String): List<ActorData>? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/credits?api_key=$TMDB_API_KEY&language=vi-VN"
        return try {
            val res = app.get(url).parsedSafe<TmdbCreditsResponse>()
            res?.cast?.take(15)?.map { 
                ActorData(Actor(it.name ?: "", it.profile_path?.let { p -> "https://image.tmdb.org/t/p/w185$p" }), roleString = it.character)
            }
        } catch (e: Exception) { null }
    }

    private suspend fun fetchTmdbSeason(tmdbId: String, seasonNumber: Int = 1): TmdbSeasonResponse? {
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$TMDB_API_KEY&language=vi-VN"
        return try { app.get(url).parsedSafe<TmdbSeasonResponse>() } catch (e: Exception) { null }
    }

    // Logic: Chỉ lấy những tập OPhim đã có, làm đẹp bằng TMDB
    suspend fun getMergedEpisodes(
        tmdbId: String?, 
        ophimServers: List<OPServer>?,
        isTvSeries: Boolean
    ): List<Episode> {
        val ophimEpsMap = mutableMapOf<Int, Pair<String, String>>() // Số tập -> <Tên, Links>
        
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

        if (!isTvSeries || tmdbId == null) {
            return ophimEpsMap.map { (num, data) ->
                newEpisode(data.second) {
                    this.name = if (data.first.contains("Tập", true)) data.first else "Tập ${data.first}"
                    this.episode = num
                }
            }.sortedBy { it.episode }
        }

        val tmdbSeason = fetchTmdbSeason(tmdbId, 1)
        val tmdbEpsMap = tmdbSeason?.episodes?.associateBy { it.episode_number }

        // CHỈ lặp qua danh sách tập của OPhim (Bỏ qua tập Coming Soon của TMDB)
        return ophimEpsMap.map { (num, data) ->
            val tmdbEp = tmdbEpsMap?.get(num)
            newEpisode(data.second) {
                this.name = if (data.first.contains("Tập", true)) data.first else "Tập ${data.first}"
                this.episode = num
                this.posterUrl = tmdbEp?.still_path?.let { "https://image.tmdb.org/t/p/w300$it" }
                this.description = formatAirDate(tmdbEp?.air_date) ?: tmdbEp?.overview
            }
        }.sortedBy { it.episode }
    }

    private fun formatAirDate(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val parts = dateStr.split("-")
            if (parts.size == 3) "${parts[2].toInt()} tháng ${parts[1].toInt()}, ${parts[0]}" else dateStr
        } catch (e: Exception) { dateStr }
    }
}

// --- TOÀN BỘ DATA CLASS (KHÔNG LƯỢC BỎ) ---
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
data class OPServer(@param:JsonProperty("server_name") val server_name: String? = null, @param:JsonProperty("server_data") val server_data: List<OPEpisode>?)
data class OPEpisode(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("link_m3u8") val link_m3u8: String? = null)
data class TmdbCreditsResponse(@param:JsonProperty("cast") val cast: List<TmdbCast>?)
data class TmdbCast(@param:JsonProperty("name") val name: String?, @param:JsonProperty("profile_path") val profile_path: String?, @param:JsonProperty("character") val character: String?)
data class TmdbDetailResponse(@param:JsonProperty("vote_average") val vote_average: Double?, @param:JsonProperty("overview") val overview: String?)
data class TmdbSeasonResponse(@param:JsonProperty("episodes") val episodes: List<TmdbEpisode>?)
data class TmdbEpisode(@param:JsonProperty("episode_number") val episode_number: Int?, @param:JsonProperty("name") val name: String?, @param:JsonProperty("overview") val overview: String?, @param:JsonProperty("still_path") val still_path: String?, @param:JsonProperty("air_date") val air_date: String?)
