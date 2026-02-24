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
        
        // --- LOGIC LỌC STATUS TRONG VÙNG AN TOÀN ---
        val startAnchor = response.indexOf("\"origin_name\"")
        val endAnchor = response.indexOf("\"thumb_url\"")
        
        val rawStatus = if (startAnchor != -1 && endAnchor != -1 && startAnchor < endAnchor) {
            val safeZone = response.substring(startAnchor, endAnchor) 
            """"status":"(.*?)"""".toRegex().find(safeZone)?.groupValues?.get(1) ?: ""
        } else ""

        val statusFromApi = rawStatus.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        
        val epCurrent = """"episode_current":"(.*?)"""".toRegex().find(response)?.groupValues?.get(1) ?: ""
        val epTotal = """"episode_total":"(.*?)"""".toRegex().find(response)?.groupValues?.get(1) ?: ""
        
        val displayProgress = if (rawStatus.equals("ongoing", ignoreCase = true)) {
            "$epCurrent / $epTotal" 
        } else {
            epCurrent 
        }

        val rawRating = """"vote_average":([\d.]+)""".toRegex().find(response)?.groupValues?.get(1)
        val tmdbRating = rawRating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: "0.0"

        val epMap = mutableMapOf<String, MutableList<String>>() 
        val serverBlocks = response.split(""""server_name":""").drop(1)

        serverBlocks.forEach { block ->
            val serverName = block.substringBefore("""",""").replace("\"", "")
            val epDataRegex = """"slug":"([^"]+)","filename".*?"link_m3u8":"([^"]+)"""".toRegex()
            
            epDataRegex.findAll(block).forEach { epMatch ->
                val epNum = epMatch.groupValues[1]
                val link = epMatch.groupValues[2].replace("\\/", "/")
                if (epNum.isNotEmpty() && link.isNotEmpty()) {
                    epMap.getOrPut(epNum) { mutableListOf() }.add("$link|$serverName")
                }
            }
        }

        val episodeList = epMap.map { (epNum, links) ->
            newEpisode(links.joinToString(",")) {
                this.name = if (epNum.all { it.isDigit() }) "Tập $epNum" else epNum
                this.episode = epNum.toIntOrNull()
            }
        }.sortedBy { it.episode }

        val metaTags = mutableListOf<String>()
        metaTags.add("⭐ $tmdbRating")

        val poster = if (moviePoster.startsWith("http")) moviePoster else "$imgDomain$moviePoster"
        val plotClean = movieContent.replace(Regex("<.*?>"), "").replace("\\n", "\n")

        return newTvSeriesLoadResponse(movieName, url, TvType.TvSeries, episodeList) {
            this.posterUrl = poster
            this.plot = plotClean
            this.year = movieYear
            this.tags = metaTags
            // Set status to metadata
            this.showStatus = if (rawStatus.equals("completed", ignoreCase = true) || rawStatus.equals("hoàn thành", ignoreCase = true)) ShowStatus.Completed else ShowStatus.Ongoing
            // Add rating to metadata
            tmdbRating.toDoubleOrNull()?.let { score ->
                if (score > 0) {
                    this.score = Score.from10(score)
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
