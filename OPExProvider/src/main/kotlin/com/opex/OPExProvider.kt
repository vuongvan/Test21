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

    private val imgDomain = "https://img.ophim.live/uploads/movies/"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = getCustomCategories(page)
        return newHomePageResponse(items.map { HomePageList(it.second, getListFromUrl(it.first)) }, hasNext = true)
    }

    private fun getCustomCategories(page: Int): List<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val categories = mutableListOf<Pair<String, String>>()
        
        // Default category
        categories.add(Pair("$mainUrl/v1/api/home", "Mới Cập Nhật"))
        
        // Parallel lists for category configuration
        val pathKeys = listOf(PREF_CATEGORY_1, PREF_CATEGORY_2, PREF_CATEGORY_3, PREF_CATEGORY_4, PREF_CATEGORY_5, PREF_CATEGORY_6)
        val nameKeys = listOf(PREF_CATEGORY_1_NAME, PREF_CATEGORY_2_NAME, PREF_CATEGORY_3_NAME, PREF_CATEGORY_4_NAME, PREF_CATEGORY_5_NAME, PREF_CATEGORY_6_NAME)
        val defaultPaths = listOf("v1/api/danh-sach/phim-le", "v1/api/quoc-gia/trung-quoc", "v1/api/quoc-gia/han-quoc", "v1/api/danh-sach/hoat-hinh", "", "")
        val defaultNames = listOf("Phim Lẻ Mới", "Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")
        
        for (i in 0 until 6) {
            val categoryPath = prefs.getString(pathKeys[i], defaultPaths[i]).orEmpty()
            if (categoryPath.isNotEmpty()) {
                val categoryName = prefs.getString(nameKeys[i], defaultNames[i]) ?: defaultNames[i]
                val categoryUrl = if (categoryPath.startsWith("http")) {
                    categoryPath
                } else {
                    "$mainUrl/$categoryPath?page=$page"
                }
                categories.add(Pair(categoryUrl, categoryName))
            }
        }
        
        return categories
    }

    private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        return try {
            val response = app.get(url, timeout = 15).text
            val data = parseJson<OPListResponse>(response)
            val items = data.data?.items ?: data.items 
            items?.map {
                newMovieSearchResponse(it.name ?: "", "$mainUrl/phim/${it.slug}", TvType.Movie) {
                    this.posterUrl = if (it.poster_url?.startsWith("http") == true) it.poster_url else "$imgDomain${it.poster_url ?: it.thumb_url}"
                }
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

     
    override suspend fun load(url: String): LoadResponse? {
        val slug = url.split("/").last()
        val response = app.get("$mainUrl/v1/api/phim/$slug").text
        
        // --- SỬ DỤNG PARSE JSON ĐỂ LẤY DỮ LIỆU CHUẨN ---
        val detailData = parseJson<OPDetailResponse>(response)
        val movie = detailData.movie ?: return null
        
        // Tên phim giờ đây chắc chắn chỉ là giá trị của trường "name"
        val movieName = movie.name ?: "OPhim"
        val movieYear = movie.year
        val movieContent = movie.content ?: ""
        val moviePoster = movie.poster_url ?: ""
        
        val metaTags = mutableListOf<String>()
        val isSingleEpisode = movie.episode_total == "1"
        val rawStatus = movie.status ?: ""

        // --- XỬ LÝ TIẾN TRÌNH (CHỈ PHIM BỘ) ---
        if (!isSingleEpisode) {
            val epCurrent = movie.episode_current ?: ""
            val epTotal = movie.episode_total ?: ""
            
            val curr = epCurrent.replace("Tập", "", true).trim()
            val total = epTotal.replace("Tập", "", true).trim()

            val displayProgress = if (rawStatus.equals("ongoing", true)) {
                if (curr.isNotEmpty() && total.isNotEmpty()) "$curr/$total Tập" else epCurrent
            } else {
                epCurrent 
            }
            if (displayProgress.isNotEmpty()) metaTags.add(displayProgress)
        }

        // --- TAGS: LOẠI BỎ VIETSUB ---
        movie.lang?.let { lang ->
            lang.split("+").forEach {
                val tag = it.trim()
                if (!tag.contains("Vietsub", true)) {
                    metaTags.add(tag)
                }
            }
        }

        // --- THỂ LOẠI ---
        movie.category?.forEach { cat ->
            cat.name?.let { metaTags.add(it) }
        }

        // --- XỬ LÝ TẬP PHIM (GIỮ NGUYÊN LOGIC SERVER) ---
        val epMap = mutableMapOf<String, MutableList<String>>()
        detailData.episodes?.forEach { server ->
            val serverName = server.server_name ?: "Server"
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: ""
                val link = ep.link_m3u8 ?: ""
                if (epName.isNotEmpty() && link.isNotEmpty()) {
                    epMap.getOrPut(epName) { mutableListOf() }.add("$link|$serverName")
                }
            }
        }

        val episodeList = epMap.map { (epName, links) ->
            newEpisode(links.joinToString(",")) {
                this.name = "Tập $epName"
                val firstNum = """(\d+)""".toRegex().find(epName)?.groupValues?.get(1)
                this.episode = firstNum?.toIntOrNull()
            }
        }.sortedBy { it.episode }

        val tvType = if (isSingleEpisode) TvType.Movie else TvType.TvSeries
        val poster = if (moviePoster.startsWith("http")) moviePoster else "$imgDomain$moviePoster"
        val plotClean = movieContent.replace(Regex("<.*?>"), "").replace("\\n", "\n")

        return if (tvType == TvType.Movie) {
            newMovieLoadResponse(movieName, url, TvType.Movie, episodeList.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                this.tags = metaTags
                movie.vote_average?.let { if (it > 0) this.score = Score.from10(it) }
            }
        } else {
            newTvSeriesLoadResponse(movieName, url, tvType, episodeList) {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                this.tags = metaTags
                movie.vote_average?.let { if (it > 0) this.score = Score.from10(it) }
                this.showStatus = if (rawStatus.contains("complete", true) || rawStatus.contains("hoàn thành", true)) {
                    ShowStatus.Completed
                } else {
                    ShowStatus.Ongoing
                }
            }
        }
    }

// --- CẦN THÊM CÁC DATA CLASS NÀY Ở CUỐI FILE ĐỂ PARSE JSON ---

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

data class OPListResponse(
    @field:JsonProperty("items") val items: List<OPItem>? = null, 
    @field:JsonProperty("data") val data: OPListData? = null
)

data class OPListData(
    @field:JsonProperty("items") val items: List<OPItem>? = null
)

data class OPItem(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("slug") val slug: String? = null,
    @field:JsonProperty("poster_url") val poster_url: String? = null,
    @field:JsonProperty("thumb_url") val thumb_url: String? = null
)
// --- CẤU TRÚC DATA CLASS VỚI @field:JsonProperty ---
data class OPDetailResponse(
    @field:JsonProperty("movie") val movie: OPMovieDetail? = null,
    @field:JsonProperty("episodes") val episodes: List<OPServer>? = null
)

data class OPMovieDetail(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("content") val content: String? = null,
    @field:JsonProperty("status") val status: String? = null,
    @field:JsonProperty("poster_url") val poster_url: String? = null,
    @field:JsonProperty("year") val year: Int? = null,
    @field:JsonProperty("episode_current") val episode_current: String? = null,
    @field:JsonProperty("episode_total") val episode_total: String? = null,
    @field:JsonProperty("lang") val lang: String? = null,
    @field:JsonProperty("vote_average") val vote_average: Double? = null,
    @field:JsonProperty("category") val category: List<OPCat>? = null
)

data class OPCat(@field:JsonProperty("name") val name: String? = null)

data class OPServer(
    @field:JsonProperty("server_name") val server_name: String? = null,
    @field:JsonProperty("server_data") val server_data: List<OPEpisode>? = null
)

data class OPEpisode(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("link_m3u8") val link_m3u8: String? = null
)
