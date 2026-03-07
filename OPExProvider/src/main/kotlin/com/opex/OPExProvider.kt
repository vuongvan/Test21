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
            
            items?.map { item ->
                // Lấy điểm số từ API và format lại (ví dụ 6.7)
                val ratingDouble = item.tmdb?.vote_average ?: 0.0
                val formattedRating = if (ratingDouble > 0.0) "%.1f".format(ratingDouble) else ""

                newMovieSearchResponse(item.name ?: "", "$mainUrl/phim/${item.slug}", TvType.Movie) {
                    this.posterUrl = if (item.poster_url?.startsWith("http") == true) item.poster_url else "$imgDomain${item.poster_url ?: item.thumb_url}"
                    
                    // --- HIỂN THỊ ĐIỂM SỐ TRÊN ẢNH BÌA ---
                    // Tùy thuộc vào phiên bản Cloudstream bạn đang dùng:
                    
                    // CÁCH 1: Nếu bản Cloudstream của bạn hỗ trợ gắn custom badge qua posterHeaders (như ảnh bạn gửi)
                    if (formattedRating.isNotEmpty()) {
                        this.posterHeaders = mapOf("rating" to "⭐ $formattedRating") 
                    }

                    // CÁCH 2: Gắn trực tiếp vào tên phim nếu Cách 1 không hiện (VD: "Tên Phim [⭐ 6.7]")
                    // this.name = if (formattedRating.isNotEmpty()) "${item.name} [⭐ $formattedRating]" else (item.name ?: "")
                }
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
    override suspend fun load(url: String): LoadResponse? {
        val slug = url.split("/").last()
        val response = app.get("$mainUrl/v1/api/phim/$slug").text
        
        // 1. Metadata cơ bản
        val movieName = """"name":"(.*?)"""".toRegex().find(response)?.groupValues?.get(1) ?: "OPhim"
        val movieYear = """"year":(\d+)""".toRegex().find(response)?.groupValues?.get(1)?.toIntOrNull()
        val movieContent = """"content":"(.*?)","type"""".toRegex().find(response)?.groupValues?.get(1) ?: ""
        val moviePoster = """"poster_url":"(.*?)"""".toRegex().find(response)?.groupValues?.get(1) ?: ""
        
        // Nhận diện Phim Lẻ/Phim Bộ
        val isMovie = response.contains(""""type":"single"""") || response.contains(""""@type":"Movie"""")
        val tvType = if (isMovie) TvType.Movie else TvType.TvSeries

        // 2. Status & Tags (Thể loại, Quốc gia, Rating)
        val metaTags = mutableListOf<String>()
        val categories = """"category":\[(.*?)]""".toRegex().find(response)?.groupValues?.get(1)
        """"name":"([^"]+)"""".toRegex().findAll(categories ?: "").forEach { metaTags.add(it.groupValues[1]) }
        val countries = """"country":\[(.*?)]""".toRegex().find(response)?.groupValues?.get(1)
        """"name":"([^"]+)"""".toRegex().findAll(countries ?: "").forEach { metaTags.add(it.groupValues[1]) }

        val rawRating = """"vote_average":([\d.]+)""".toRegex().find(response)?.groupValues?.get(1)
        val ratingDouble = rawRating?.toDoubleOrNull() ?: 0.0
        if (ratingDouble > 0.0) metaTags.add("⭐ ${"%.1f".format(ratingDouble)}")

        val startAnchor = response.indexOf("\"origin_name\"")
        val endAnchor = response.indexOf("\"thumb_url\"")
        if (startAnchor != -1 && endAnchor != -1) {
            val safeZone = response.substring(startAnchor, endAnchor)
            """"status":"(.*?)"""".toRegex().find(safeZone)?.groupValues?.get(1)?.let {
                if (it.isNotEmpty()) metaTags.add(it.replaceFirstChar { char -> char.uppercase() })
            }
        }

        // 3. XỬ LÝ TẬP PHIM: TẠO TAB SUB/DUB BẰNG SEASON
        val episodeList = mutableListOf<Episode>()
        val serverBlocks = response.split(""""server_name":""").drop(1)
        
        var hasSub = false
        var hasDub = false

        serverBlocks.forEach { block ->
            val serverName = block.substringBefore("""","""").replace("\"", "")
            
            // Nếu chứa Thuyết minh/Lồng tiếng -> Season 2 (Dub), còn lại -> Season 1 (Sub)
            val isDub = serverName.contains("Thuyết Minh", ignoreCase = true) || serverName.contains("Lồng Tiếng", ignoreCase = true)
            val currentSeason = if (isDub) 2 else 1
            
            if (isDub) hasDub = true else hasSub = true

            val epDataRegex = """"name":"([^"]+)","slug":"[^"]*","filename"[^}]+?"link_m3u8":"([^"]+)"""".toRegex()
            
            epDataRegex.findAll(block).forEach { epMatch ->
                val epName = epMatch.groupValues[1] 
                val link = epMatch.groupValues[2].replace("\\/", "/")
                
                if (epName.isNotEmpty() && link.isNotEmpty()) {
                    val firstNum = """(\d+)""".toRegex().find(epName)?.groupValues?.get(1)
                    
                    episodeList.add(newEpisode(link) {
                        this.name = if (epName.all { it.isDigit() }) "Tập $epName" else epName
                        this.episode = firstNum?.toIntOrNull()
                        
                        // Gán season để Cloudstream tự chia Menu (1 hoặc 2)
                        this.season = currentSeason
                    })
                }
            }
        }

        val poster = if (moviePoster.startsWith("http")) moviePoster else "$imgDomain$moviePoster"
        val plotClean = movieContent.replace(Regex("<.*?>"), "").replace("\\n", "\n")

        // 4. Trả về Response
        if (isMovie && episodeList.size <= 2 && (hasSub != hasDub || episodeList.size == 1)) {
            // Nếu là Phim Lẻ nguyên khối (chỉ có 1 link) -> Hiện giao diện nút Play khổng lồ
            val movieLinks = episodeList.joinToString(",") { it.data }
            return newMovieLoadResponse(movieName, url, TvType.Movie, movieLinks) {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                this.tags = metaTags
            }
        } else {
            // Nếu là Phim Bộ hoặc Phim Lẻ nhiều nguồn -> Hiện danh sách tập có chia Tab Sub/Dub
            return newTvSeriesLoadResponse(movieName, url, tvType, episodeList.sortedBy { it.episode }) {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                this.tags = metaTags 
                
                // Đây là lệnh đổi tên Tab Season thành "Phụ Đề" và "Lồng Tiếng"
                val sNames = mutableListOf<SeasonData>()
                if (hasSub) sNames.add(SeasonData(1, "Phụ Đề"))
                if (hasDub) sNames.add(SeasonData(2, "Lồng Tiếng"))
                this.seasonNames = sNames
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 'data' giờ là đường link m3u8 trực tiếp, không chứa tên server thừa nữa
        data.split(",").forEach { link ->
            if (link.isNotEmpty()) {
                callback.invoke(
                    newExtractorLink("OPhim", "OPhim", link, ExtractorLinkType.M3U8)
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

data class OPListData(val items: List<OPItem>?)

// Thêm class này để lấy điểm đánh giá
data class OPTmdb(
    @field:JsonProperty("vote_average") val vote_average: Double? = null
)

// Bổ sung thêm thuộc tính tmdb vào OPItem
data class OPItem(
    val name: String?, 
    val slug: String?, 
    val poster_url: String?, 
    val thumb_url: String?,
    @field:JsonProperty("tmdb") val tmdb: OPTmdb? = null
)
