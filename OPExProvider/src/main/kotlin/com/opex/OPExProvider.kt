package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Locale
import android.content.Context

class OPExProvider : MainAPI() {
    companion object {
        lateinit var ctx: Context
        const val PREFS_NAME = "opex_provider_prefs"
        const val PREF_DOMAIN = "domain"
        const val PREF_CATEGORY_1 = "category_1"
        const val PREF_CATEGORY_2 = "category_2"
        const val PREF_CATEGORY_3 = "category_3"
        const val PREF_CATEGORY_4 = "category_4"
        const val PREF_CATEGORY_5 = "category_5"
        const val PREF_CATEGORY_6 = "category_6"
        const val PREF_CATEGORY_1_NAME = "category_1_name"
        const val PREF_CATEGORY_2_NAME = "category_2_name"
        const val PREF_CATEGORY_3_NAME = "category_3_name"
        const val PREF_CATEGORY_4_NAME = "category_4_name"
        const val PREF_CATEGORY_5_NAME = "category_5_name"
        const val PREF_CATEGORY_6_NAME = "category_6_name"

        fun getPreferenceKey(i: Int): String = when (i) {
            1 -> PREF_CATEGORY_1
            2 -> PREF_CATEGORY_2
            3 -> PREF_CATEGORY_3
            4 -> PREF_CATEGORY_4
            5 -> PREF_CATEGORY_5
            6 -> PREF_CATEGORY_6
            else -> PREF_CATEGORY_1
        }

        fun getPreferenceNameKey(i: Int): String = when (i) {
            1 -> PREF_CATEGORY_1_NAME
            2 -> PREF_CATEGORY_2_NAME
            3 -> PREF_CATEGORY_3_NAME
            4 -> PREF_CATEGORY_4_NAME
            5 -> PREF_CATEGORY_5_NAME
            6 -> PREF_CATEGORY_6_NAME
            else -> PREF_CATEGORY_1_NAME
        }
    }

    override var mainUrl = "https://ophim1.com"
    override var name = "OPhim"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    
    private val tmdbApiKey = "YOUR_API_KEY_HERE"

    private fun fixImgUrl(url: String?, domain: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http")) return url
        if (domain.isNullOrBlank()) return null
        
        val cleanDomain = domain.removeSuffix("/")
        val cleanPath = url.removePrefix("/")
        
        return if (cleanPath.startsWith("uploads/")) {
            "$cleanDomain/$cleanPath"
        } else {
            "$cleanDomain/uploads/movies/$cleanPath"
        }
    }

    private suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$tmdbApiKey&language=vi-VN"
        return try { app.get(url).parsedSafe<TmdbDetailResponse>() } catch (e: Exception) { null }
    }

    // --- ĐÃ FIX: Trả về List<ActorData>? ---
    private suspend fun fetchTmdbCast(tmdbType: String, tmdbId: String): List<ActorData>? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/credits?api_key=$tmdbApiKey&language=vi-VN"
        return try {
            val res = app.get(url).parsedSafe<TmdbCreditsResponse>()
            res?.cast?.take(15)?.map { 
                ActorData(
                    actor = Actor(it.name ?: "", it.profile_path?.let { p -> "https://image.tmdb.org/t/p/w185$p" }),
                    roleString = it.character // Thêm vai diễn vào đây
                )
            }
        } catch (e: Exception) { null }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = getCustomCategories(page)
        return newHomePageResponse(items.map { HomePageList(it.second, getListFromUrl(it.first)) }, hasNext = true)
    }

    private fun getCustomCategories(page: Int): List<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val categories = mutableListOf<Pair<String, String>>()
        categories.add(Pair("$mainUrl/v1/api/home?sort_field=year&sort_type=desc&page=$page", "Mới Cập Nhật"))
        
        for (i in 1..6) {
            val path = prefs.getString(getPreferenceKey(i), "").orEmpty()
            if (path.isNotEmpty()) {
                val name = prefs.getString(getPreferenceNameKey(i), "Danh mục $i") ?: "Danh mục $i"
                val sep = if (path.contains("?")) "&" else "?"
                categories.add(Pair(if (path.startsWith("http")) "$path${sep}page=$page" else "$mainUrl/$path${sep}page=$page", name))
            }
        }
        return categories
    }
    
    private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        return try {
            val response = app.get(url, timeout = 15).text
            val data = parseJson<OPListResponse>(response)
            val cdn = data.data?.APP_DOMAIN_CDN_IMAGE ?: data.APP_DOMAIN_CDN_IMAGE
            val items = data.data?.items ?: data.items 
            
            items?.filter { it.episode_current?.contains("trailer", true) != true }?.map { it ->
                val scoreVal = it.tmdb?.vote_average ?: it.imdb?.vote_average ?: 0.0
                newMovieSearchResponse(it.name ?: "", "$mainUrl/v1/api/phim/${it.slug}", TvType.Movie) {
                    this.posterUrl = fixImgUrl(it.poster_url ?: it.thumb_url, cdn)
                    if (scoreVal > 0) this.score = Score.from10(scoreVal)
                    this.quality = if (it.quality?.uppercase() == "CAM") SearchQuality.Cam else SearchQuality.HD
                }
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.split("/").last()
        val movieResponse = app.get("$mainUrl/v1/api/phim/$slug").text
        val movieRoot = parseJson<OPRootResponse>(movieResponse)
        val data = movieRoot.data ?: return null
        val movie = data.item ?: return null
        val cdn = data.APP_DOMAIN_CDN_IMAGE 

        val tmdbId = movie.tmdb?.id?.toString()
        val isSingleEpisode = movie.episode_total == "1" || movie.category?.any { it.name?.contains("Phim lẻ", true) == true } ?: false
        val tmdbType = if (isSingleEpisode) "movie" else "tv"

        val actorsList = tmdbId?.let { fetchTmdbCast(tmdbType, it) }
        val tmdbExtra = tmdbId?.let { fetchTmdbDetails(tmdbType, it) }

        val movieName = movie.name?.split("-", "[")?.first()?.trim() ?: "OPhim"
        val poster = fixImgUrl(movie.poster_url ?: movie.thumb_url, cdn) ?: data.seoOnPage?.seoSchema?.image ?: ""
        
        val metaTags = mutableListOf<String>()
        movie.category?.forEach { it.name?.let { n -> metaTags.add(n) } }
        movie.lang?.let { metaTags.add(it) }

        val episodeList = movie.episodes?.flatMap { server ->
            server.server_data?.map { ep ->
                newEpisode("${ep.link_m3u8}|${server.server_name}") {
                    this.name = if (isSingleEpisode) "Full" else "Tập ${ep.name}"
                    this.episode = ep.name?.filter { it.isDigit() }?.toIntOrNull()
                }
            } ?: emptyList()
        }?.sortedBy { it.episode } ?: emptyList()

        val finalRating = tmdbExtra?.vote_average ?: movie.tmdb?.vote_average ?: 0.0

        return if (isSingleEpisode) {
            newMovieLoadResponse(movieName, url, TvType.Movie, episodeList.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = movie.content?.replace(Regex("<.*?>"), "")
                this.year = movie.year
                this.tags = metaTags
                this.actors = actorsList // Đã tương thích với ActorData
                if (finalRating > 0) this.score = Score.from10(finalRating)
            }
        } else {
            newTvSeriesLoadResponse(movieName, url, TvType.TvSeries, episodeList) {
                this.posterUrl = poster
                this.plot = movie.content?.replace(Regex("<.*?>"), "")
                this.year = movie.year
                this.tags = metaTags
                this.actors = actorsList // Đã tương thích với ActorData
                if (finalRating > 0) this.score = Score.from10(finalRating)
            }
        }
    }
        
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        data.split(",").forEach { info ->
            val parts = info.split("|")
            val link = parts.getOrNull(0) ?: ""
            val name = parts.getOrNull(1) ?: "OPhim"
            if (link.isNotEmpty()) callback.invoke(newExtractorLink(name, name, link, ExtractorLinkType.M3U8))
        }
        return true
    }
        
    override suspend fun search(query: String): List<SearchResponse> = getListFromUrl("$mainUrl/v1/api/tim-kiem?keyword=$query&limit=20")
}

// --- DATA CLASSES ---
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
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("imdb") val imdb: OPImdbListItem? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null
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
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("category") val category: List<OPCat>? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("episodes") val episodes: List<OPServer>? = null
)
data class OPTmdb(@param:JsonProperty("vote_average") val vote_average: Double? = null, @param:JsonProperty("id") val id: Any? = null)
data class OPCat(@param:JsonProperty("name") val name: String? = null)
data class OPServer(@param:JsonProperty("server_name") val server_name: String? = null, @param:JsonProperty("server_data") val server_data: List<OPEpisode>? = null)
data class OPEpisode(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("link_m3u8") val link_m3u8: String? = null)

// --- TMDB DATA CLASSES ---
data class TmdbCreditsResponse(@param:JsonProperty("cast") val cast: List<TmdbCast>? = null)
data class TmdbCast(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("profile_path") val profile_path: String? = null,
    @param:JsonProperty("character") val character: String? = null // Thêm trường character vào đây
)
data class TmdbDetailResponse(@param:JsonProperty("vote_average") val vote_average: Double? = null, @param:JsonProperty("overview") val overview: String? = null)
