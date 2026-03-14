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
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = getCustomCategories(page)
        return newHomePageResponse(items.map { HomePageList(it.second, getListFromUrl(it.first)) }, hasNext = true)
    }

private fun getCustomCategories(page: Int): List<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val categories = mutableListOf<Pair<String, String>>()
        
        val defaultPaths = listOf("v1/api/danh-sach/phim-thuyet-minh?sort_field=year&sort_type=desc", "v1/api/danh-sach/phim-long-tieng?sort_field=year&sort_type=desc", "v1/api/danh-sach/phim-le?sort_field=year&sort_type=desc", "v1/api/danh-sach/hoat-hinh?sort_field=year&sort_type=desc", "", "")
        val defaultNames = listOf("Phim Thuyết Minh", "Phim Lồng Tiếng", "Phim Lẻ", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")

        // Mục cố định
        categories.add(Pair("$mainUrl/v1/api/danh-sach/phim-moi-cap-nhat?page=$page", "Mới Cập Nhật"))
        
        for (i in 0..5) {
            val path = prefs.getString(getPreferenceKey(i + 1), defaultPaths[i]).orEmpty()
            if (path.isNotEmpty()) {
                val name = prefs.getString(getPreferenceNameKey(i + 1), defaultNames[i]) ?: defaultNames[i]
                val sep = if (path.contains("?")) "&" else "?"
                val finalUrl = if (path.startsWith("http")) "$path${sep}page=$page" else "$mainUrl/$path${sep}page=$page"
                categories.add(Pair(finalUrl, name))
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
                    this.posterUrl = OPExUtils.fixImgUrl(it.thumb_url ?: it.poster_url, cdn)
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
        val isSingleEpisode = movie.episode_total?.trim() == "1" || movie.category?.any { it.name?.contains("Phim lẻ", true) == true } ?: false
        val tmdbType = if (isSingleEpisode) "movie" else "tv"

        val actorsList = tmdbId?.let { OPExUtils.fetchTmdbCast(tmdbType, it) }
        val tmdbExtra = tmdbId?.let { OPExUtils.fetchTmdbDetails(tmdbType, it) }

        val movieName = movie.name?.split("-", "[")?.first()?.trim() ?: "OPhim"
        val poster = OPExUtils.fixImgUrl(movie.poster_url ?: movie.thumb_url, cdn) ?: data.seoOnPage?.seoSchema?.image ?: ""
        
        val metaTags = mutableListOf<String>()
        val rawStatus = movie.status ?: ""
        
        if (!isSingleEpisode) {
            val epCurrent = movie.episode_current ?: ""
            val epTotal = movie.episode_total?.replace("Tập", "", true)?.trim() ?: ""
            if (epCurrent.isNotEmpty()) {
                val cleanCurrent = epCurrent.replace("Hoàn tất", "").replace("(", "").replace(")", "").trim()
                metaTags.add(if (epTotal.isNotEmpty() && !cleanCurrent.contains("/")) "$cleanCurrent/$epTotal Tập" else epCurrent)
            }
        }
        movie.lang?.let { l -> l.split("+").forEach { if (!it.contains("Vietsub", true)) metaTags.add(it.trim()) } }
        movie.category?.forEach { it.name?.let { n -> metaTags.add(n) } }

        // --- FIX CHỌN SERVER: Gộp Vietsub/Thuyết minh ---
        val episodesMap = mutableMapOf<String, MutableList<String>>()
        movie.episodes?.forEach { server ->
            val sName = server.server_name ?: "Server"
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: "Full"
                val list = episodesMap.getOrPut(epName) { mutableListOf() }
                list.add("${ep.link_m3u8}|$sName")
            }
        }

        val episodeList = episodesMap.map { (name, links) ->
            newEpisode(links.joinToString(",")) {
                this.name = if (name.contains("Tập", true)) name else "Tập $name"
                // CHỈ lấy số đầu tiên (Ví dụ "01-03" -> lấy 1) để tránh lặp số
                this.episode = Regex("""(\d+)""").find(name)?.value?.toIntOrNull()
            }
        }.sortedBy { it.episode }
        
        val finalRating = tmdbExtra?.vote_average ?: movie.tmdb?.vote_average ?: 0.0
        val plotClean = (movie.content ?: tmdbExtra?.overview ?: "").replace(Regex("<.*?>"), "").replace("\\n", "\n")

        return if (isSingleEpisode) {
            newMovieLoadResponse(movieName, url, TvType.Movie, episodeList.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movie.year
                this.tags = metaTags
                this.actors = actorsList
                this.score = finalRating.let { if (it > 0) Score.from10(it) else null }
            }
        } else {
            newTvSeriesLoadResponse(movieName, url, TvType.TvSeries, episodeList) {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movie.year
                this.tags = metaTags
                this.actors = actorsList
                this.score = finalRating.let { if (it > 0) Score.from10(it) else null }
                this.showStatus = if (rawStatus.contains("complete", true) || rawStatus.contains("hoàn thành", true)) 
                    ShowStatus.Completed else ShowStatus.Ongoing
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
        
    override suspend fun search(query: String): List<SearchResponse> = getListFromUrl("$mainUrl/v1/api/tim-kiem?keyword=$query&limit=30")
}

