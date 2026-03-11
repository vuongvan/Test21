package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.fasterxml.jackson.annotation.JsonProperty
import android.content.Context

class OPExProvider : MainAPI() {
    companion object {
        lateinit var ctx: Context
        const val PREFS_NAME = "opex_provider_prefs"
        fun getPreferenceKey(i: Int): String = "category_$i"
        fun getPreferenceNameKey(i: Int): String = "category_${i}_name"
    }

    override var mainUrl = "https://ophim1.com"
    override var name = "OPhim"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    
    // API KEY TMDB 
    private val tmdbApiKey = "YOUR_API_KEY_HERE"

    // --- HÀM XỬ LÝ ẢNH ĐỘNG ---
    private fun fixImgUrl(url: String?, dynamicDomain: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http")) return url
        if (dynamicDomain.isNullOrBlank()) return null 
        
        val base = dynamicDomain.removeSuffix("/")
        val path = url.removePrefix("/")
        return "$base/$path"
    }

    // --- TMDB HELPERS ---
    private suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$tmdbApiKey&language=vi-VN"
        return try { app.get(url).parsedSafe<TmdbDetailResponse>() } catch (e: Exception) { null }
    }

    private suspend fun fetchTmdbCast(tmdbType: String, tmdbId: String): List<ActorData>? {
        val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/credits?api_key=$tmdbApiKey&language=vi-VN"
        return try {
            val res = app.get(url).parsedSafe<TmdbCreditsResponse>()
            res?.cast?.take(15)?.map { cast ->
                val actorImg = cast.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" }
                ActorData(Actor(cast.name ?: "", actorImg), roleString = cast.character)
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
                val separator = if (path.contains("?")) "&" else "?"
                val finalUrl = if (path.startsWith("http")) "$path${if(path.contains("?")) "&" else "?"}page=$page" 
                               else "$mainUrl/$path${separator}page=$page"
                categories.add(Pair(finalUrl, name))
            }
        }
        return categories
    }
    
    private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        return try {
            val response = app.get(url, timeout = 15).text
            val data = parseJson<OPListResponse>(response)
            val domain = data.data?.pathImage 
            val items = data.data?.items ?: data.items 
            
            items?.filter { it.episode_current?.contains("trailer", true) != true }?.map { it ->
                val scoreVal = it.tmdb?.vote_average ?: it.imdb?.vote_average ?: 0.0

                newMovieSearchResponse(it.name ?: "", "$mainUrl/v1/api/phim/${it.slug}", TvType.Movie) {
                    this.posterUrl = fixImgUrl(it.poster_url ?: it.thumb_url, domain)
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
        val domain = data.pathImage 

        val tmdbId = movie.tmdb?.id?.toString()
        val isSingleEpisode = movie.episode_total == "1" || movie.category?.any { it.name?.contains("Phim lẻ", true) == true } ?: false
        val tmdbType = if (isSingleEpisode) "movie" else "tv"

        val actorsList = tmdbId?.let { fetchTmdbCast(tmdbType, it) }
        val tmdbExtra = tmdbId?.let { fetchTmdbDetails(tmdbType, it) }

        val movieName = movie.name?.split("-", "[")?.first()?.trim() ?: "OPhim"
        val poster = fixImgUrl(movie.poster_url ?: movie.thumb_url, domain) ?: ""
        val plotClean = (movie.content ?: tmdbExtra?.overview ?: "").replace(Regex("<.*?>"), "").replace("\\n", "\n")
        
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
                this.plot = plotClean
                this.year = movie.year
                this.tags = metaTags
                this.addActors(actorsList)
                if (finalRating > 0) this.score = Score.from10(finalRating)
            }
        } else {
            newTvSeriesLoadResponse(movieName, url, TvType.TvSeries, episodeList) {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movie.year
                this.tags = metaTags
                this.addActors(actorsList)
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
    @param:JsonProperty("items") val items: List<OPItem>? = null
)

data class OPListData(
    @param:JsonProperty("items") val items: List<OPItem>? = null,
    @param:JsonProperty("pathImage") val pathImage: String? = null
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
    @param:JsonProperty("pathImage") val pathImage: String? = null
)

data class OPItemDetail(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("category") val category: List<OPCat>? = null,
    @param:JsonProperty("episodes") val episodes: List<OPServer>? = null
)

data class OPTmdb(@param:JsonProperty("vote_average") val vote_average: Double? = null, @param:JsonProperty("id") val id: Any? = null)
data class OPCat(@param:JsonProperty("name") val name: String? = null)
data class OPServer(@param:JsonProperty("server_name") val server_name: String? = null, @param:JsonProperty("server_data") val server_data: List<OPEpisode>? = null)
data class OPEpisode(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("link_m3u8") val link_m3u8: String? = null)
data class TmdbCreditsResponse(@param:JsonProperty("cast") val cast: List<TmdbCast>? = null)
data class TmdbCast(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("profile_path") val profile_path: String? = null, @param:JsonProperty("character") val character: String? = null)
data class TmdbDetailResponse(@param:JsonProperty("vote_average") val vote_average: Double? = null, @param:JsonProperty("overview") val overview: String? = null)
