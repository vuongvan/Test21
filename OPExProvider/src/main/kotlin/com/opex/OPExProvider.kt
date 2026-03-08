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
                newMovieSearchResponse(it.name ?: "", "$mainUrl/v1/api/phim/${it.slug}", TvType.Movie) {
                    this.posterUrl = if (it.poster_url?.startsWith("http") == true) it.poster_url else "$imgDomain${it.poster_url ?: it.thumb_url}"
                }
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.split("/").last()
        val response = app.get("$mainUrl/v1/api/phim/$slug").text
        
        // --- PARSE JSON CHUẨN XÁC ---
        val rootData = parseJson<OPRootResponse>(response)
        val movie = rootData.data?.item ?: return null // Lấy dữ liệu phim từ data.item
        
        // --- TRÍCH XUẤT THÔNG TIN CƠ BẢN ---
        val movieName = movie.name ?: "OPhim"
        val movieYear = movie.year
        val movieContent = movie.content ?: ""
        
        // Lấy domain ảnh từ JSON nếu có, không thì dùng mặc định
        val currentImgDomain = rootData.data?.cdnImage ?: imgDomain
        val posterPath = movie.poster_url ?: movie.thumb_url ?: ""
        val poster = if (posterPath.startsWith("http")) posterPath else "$currentImgDomain/$posterPath"

        val metaTags = mutableListOf<String>()
        val rawStatus = movie.status ?: ""
        
        // Xử lý xác định phim lẻ hay phim bộ (JSON trả về dạng "13 Tập" nên cần xóa chữ Tập đi để kiểm tra)
        val epTotalNumber = movie.episode_total?.replace("Tập", "", true)?.trim() ?: ""
        val isSingleEpisode = epTotalNumber == "1"

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

        // --- CHỈ GIỮ LẠI LỒNG TIẾNG/THUYẾT MINH ---
        movie.lang?.let { lang ->
            lang.split("+").forEach {
                val tag = it.trim()
                if (!tag.contains("Vietsub", true)) {
                    metaTags.add(tag)
                }
            }
        }

        // --- THÊM THỂ LOẠI ---
        movie.category?.forEach { cat ->
            cat.name?.let { metaTags.add(it) }
        }

        // --- GOM NHÓM TẬP PHIM TỪ DANH SÁCH SERVER ---
        val epMap = mutableMapOf<String, MutableList<String>>()
        movie.episodes?.forEach { server ->
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
        val plotClean = movieContent.replace(Regex("<.*?>"), "").replace("\\n", "\n")
        val ratingValue = movie.tmdb?.vote_average ?: 0.0

        // --- TRẢ VỀ RESPONSE CHUẨN ---
        return if (tvType == TvType.Movie) {
            newMovieLoadResponse(movieName, url, TvType.Movie, episodeList.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                this.tags = metaTags
                if (ratingValue > 0) this.score = Score.from10(ratingValue)
            }
        } else {
            newTvSeriesLoadResponse(movieName, url, tvType, episodeList) {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                this.tags = metaTags
                if (ratingValue > 0) this.score = Score.from10(ratingValue)
                this.showStatus = if (rawStatus.contains("complete", true) || rawStatus.contains("hoàn thành", true)) {
                    ShowStatus.Completed
                } else {
                    ShowStatus.Ongoing
                }
            }
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
// Root JSON chứa "status", "data"
data class OPRootResponse(
    @field:JsonProperty("status") val status: String? = null,
    @field:JsonProperty("data") val data: OPDataContent? = null
)

// Bên trong "data" chứa "item" và "APP_DOMAIN_CDN_IMAGE"
data class OPDataContent(
    @field:JsonProperty("item") val item: OPItemDetail? = null,
    @field:JsonProperty("APP_DOMAIN_CDN_IMAGE") val cdnImage: String? = null
)

// Thông tin chi tiết của bộ phim nằm trong "item"
data class OPItemDetail(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("content") val content: String? = null,
    @field:JsonProperty("status") val status: String? = null,
    @field:JsonProperty("poster_url") val poster_url: String? = null,
    @field:JsonProperty("thumb_url") val thumb_url: String? = null,
    @field:JsonProperty("year") val year: Int? = null,
    @field:JsonProperty("episode_current") val episode_current: String? = null,
    @field:JsonProperty("episode_total") val episode_total: String? = null,
    @field:JsonProperty("lang") val lang: String? = null,
    @field:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @field:JsonProperty("category") val category: List<OPCat>? = null,
    @field:JsonProperty("episodes") val episodes: List<OPServer>? = null
)

// TMDB chứa Rating
data class OPTmdb(
    @field:JsonProperty("vote_average") val vote_average: Double? = null
)

// Thể loại
data class OPCat(
    @field:JsonProperty("name") val name: String? = null
)

// Danh sách Server (Vietsub #1, Thuyết Minh #1...)
data class OPServer(
    @field:JsonProperty("server_name") val server_name: String? = null,
    @field:JsonProperty("server_data") val server_data: List<OPEpisode>? = null
)

// Thông tin từng tập phim
data class OPEpisode(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("link_m3u8") val link_m3u8: String? = null
)
