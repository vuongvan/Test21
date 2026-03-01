package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Locale

class OPExProvider : MainAPI() {
    override var mainUrl = "https://ophim1.com"
    override var name = "OPhim"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val imgDomain = "https://img.ophim.live/uploads/movies/"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = listOf(
            Pair("$mainUrl/v1/api/home", "Mới Cập Nhật"),
            Pair("$mainUrl/v1/api/danh-sach/phim-le?page=$page", "Phim Lẻ Mới"),
            Pair("$mainUrl/v1/api/quoc-gia/trung-quoc?page=$page", "Phim Trung Quốc"),
            Pair("$mainUrl/v1/api/quoc-gia/han-quoc?page=$page", "Phim Hàn Quốc"),
            Pair("$mainUrl/v1/api/danh-sach/hoat-hinh?page=$page", "Phim Hoạt Hình")
        )
        return newHomePageResponse(items.map { HomePageList(it.second, getListFromUrl(it.first)) }, hasNext = true)
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
        
        // --- GIỮ NGUYÊN LOGIC STATUS IN HOA TRONG VÙNG AN TOÀN ---
        val startAnchor = response.indexOf("\"origin_name\"")
        val endAnchor = response.indexOf("\"thumb_url\"")
        val rawStatus = if (startAnchor != -1 && endAnchor != -1 && startAnchor < endAnchor) {
            val safeZone = response.substring(startAnchor, endAnchor) 
            """"status":"(.*?)"""".toRegex().find(safeZone)?.groupValues?.get(1) ?: ""
        } else ""
        val statusFromApi = rawStatus.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        
        val epCurrent = """"episode_current":"(.*?)"""".toRegex().find(response)?.groupValues?.get(1) ?: ""
        val epTotal = """"episode_total":"(.*?)"""".toRegex().find(response)?.groupValues?.get(1) ?: ""
        val displayProgress = if (rawStatus.equals("ongoing", ignoreCase = true)) "$epCurrent / $epTotal" else epCurrent

        val rawRating = """"vote_average":([\d.]+)""".toRegex().find(response)?.groupValues?.get(1)
        val tmdbRating = rawRating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: "0.0"

        // --- CẬP NHẬT LOGIC LẤY TẬP PHIM (FIX PHIM BÁCH LUYỆN THÀNH THẦN) ---
        val epMap = mutableMapOf<String, MutableList<String>>() 
        // Tách chuỗi theo từng server để không bỏ lỡ tập nào
        val serverBlocks = response.split(""""server_name":""").drop(1)

        serverBlocks.forEach { block ->
            val serverName = block.substringBefore("""",""").replace("\"", "")
            // Regex này sẽ quét qua toàn bộ server_data của từng server
            val epDataRegex = """"name":"([^"]+)","slug":"([^"]+)","filename".*?"link_m3u8":"([^"]+)"""".toRegex()
            
            epDataRegex.findAll(block).forEach { epMatch ->
                val epName = epMatch.groupValues[1] // Số tập (1, 2, 3...)
                val link = epMatch.groupValues[3].replace("\\/", "/")
                if (epName.isNotEmpty() && link.isNotEmpty()) {
                    // Gom link vào cùng 1 tập dựa trên tên tập (epName)
                    epMap.getOrPut(epName) { mutableListOf() }.add("$link|$serverName")
                }
            }
        }

        val episodeList = epMap.map { (epName, links) ->
            newEpisode(links.joinToString(",")) {
                this.name = if (epName.all { it.isDigit() }) "Tập $epName" else epName
                this.episode = epName.toIntOrNull()
            }
        }.sortedBy { it.episode }

        // --- GIỮ NGUYÊN TAGS VÀ METADATA ---
        val metaTags = mutableListOf<String>()
        if (statusFromApi.isNotEmpty()) metaTags.add(statusFromApi) 
        metaTags.add("⭐ $tmdbRating")
        if (displayProgress.isNotEmpty()) metaTags.add(displayProgress)

        val poster = if (moviePoster.startsWith("http")) moviePoster else "$imgDomain$moviePoster"
        val plotClean = movieContent.replace(Regex("<.*?>"), "").replace("\\n", "\n")

        return newTvSeriesLoadResponse(movieName, url, TvType.TvSeries, episodeList) {
            this.posterUrl = poster
            this.plot = plotClean
            this.year = movieYear
            this.tags = metaTags 
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
data class OPListData(val items: List<OPItem>?)
data class OPItem(val name: String?, val slug: String?, val poster_url: String?, val thumb_url: String?)
