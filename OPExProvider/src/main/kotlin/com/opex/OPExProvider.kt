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
        
        val movieName = """"name":"(.*?)"""".toRegex().find(response)?.groupValues?.get(1) ?: "OPhim"
        val movieYear = """"year":(\d+)""".toRegex().find(response)?.groupValues?.get(1)?.toIntOrNull()
        val movieContent = """"content":"(.*?)","type"""".toRegex().find(response)?.groupValues?.get(1) ?: ""
        val moviePoster = """"poster_url":"(.*?)"""".toRegex().find(response)?.groupValues?.get(1) ?: ""
        
        // Xác định loại phim qua episode_total từ JSON
        val epTotalRaw = """"episode_total":"(\d+)"""".toRegex().find(response)?.groupValues?.get(1)
        val isSingleEpisode = epTotalRaw == "1"

        // --- GOM NHÓM NGUỒN PHIM ---
        val epMap = mutableMapOf<String, MutableList<String>>() 
        val serverBlocks = response.split(""""server_name":""").drop(1)

        serverBlocks.forEach { block ->
            // Lấy tên server (VD: Vietsub #1, Thuyết Minh #1)
            val serverName = block.substringBefore("""","""").replace("\"", "")
            
            // Regex bóc tách link m3u8 và tên tập từ JSON
            val epDataRegex = """"name":"([^"]+)","slug":"[^"]*","filename"[^}]+?"link_m3u8":"([^"]+)"""".toRegex()
            
            epDataRegex.findAll(block).forEach { epMatch ->
                val epName = epMatch.groupValues[1] 
                val link = epMatch.groupValues[2].replace("\\/", "/")
                
                if (epName.isNotEmpty() && link.isNotEmpty()) {
                    // Lưu link kèm tên server để loadLinks xử lý
                    epMap.getOrPut(epName) { mutableListOf() }.add("$link|$serverName")
                }
            }
        }

        // Tạo danh sách tập phim đã làm sạch tên
        val episodeList = epMap.map { (epName, links) ->
            newEpisode(links.joinToString(",")) {
                // CHỈ GIỮ LẠI TÊN TẬP (VD: Tập 1), không còn chữ Vietsub ở đây
                this.name = if (epName.all { it.isDigit() }) "Tập $epName" else epName
                val firstNum = """(\d+)""".toRegex().find(epName)?.groupValues?.get(1)
                this.episode = firstNum?.toIntOrNull()
            }
        }.sortedBy { it.episode }

        val tvType = if (isSingleEpisode) TvType.Movie else TvType.TvSeries
        val poster = if (moviePoster.startsWith("http")) moviePoster else "$imgDomain$moviePoster"
        val plotClean = movieContent.replace(Regex("<.*?>"), "").replace("\\n", "\n")

        val metaTags = mutableListOf<String>()
        val rawRating = """"vote_average":([\d.]+)""".toRegex().find(response)?.groupValues?.get(1)
        val ratingValue = rawRating?.toDoubleOrNull() ?: 0.0

        return if (tvType == TvType.Movie) {
            newMovieLoadResponse(movieName, url, TvType.Movie, episodeList.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                if (ratingValue > 0) this.score = Score.from10(ratingValue)
            }
        } else {
            newTvSeriesLoadResponse(movieName, url, tvType, episodeList) {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                if (ratingValue > 0) this.score = Score.from10(ratingValue)
            }
        }
        }
        override suspend fun loadLinks(
        data: String, 
        isCasting: Boolean, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Tách các server đã gộp bằng dấu phẩy
        data.split(",").forEach { info ->
            val parts = info.split("|")
            val link = parts.getOrNull(0) ?: ""
            val serverName = parts.getOrNull(1) ?: "OPhim"
            
            if (link.isNotEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        serverName, // Tên này sẽ hiện trong mục "Nguồn Phim"
                        serverName, 
                        link, 
                        "", 
                        Qualities.Unknown.value, 
                        isM3u8 = true
                    )
                )
            }
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
