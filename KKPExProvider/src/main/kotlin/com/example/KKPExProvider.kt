package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import android.content.Context

class KKPExProvider : MainAPI() {
    companion object {
        lateinit var ctx: Context
        const val PREFS_NAME = "kkpex_provider_prefs"
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
    }
    override var mainUrl = "https://phimapi.com"
    override var name = "KK Phim"
    override val hasMainPage = true
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private fun fixPosterUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return if (url.startsWith("http")) url else "https://phimimg.com/$url"
    }

    private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        return try {
            val response = app.get(url).text
            val data = parseJson<KKListResponse>(response)
            val items = data.data?.items ?: data.items 
            items?.map {
                newMovieSearchResponse(it.name ?: "", "$mainUrl/phim/${it.slug}", TvType.Movie) {
                    this.posterUrl = fixPosterUrl(it.poster_url ?: it.thumb_url)
                }
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun getCustomCategories(page: Int): List<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val categories = mutableListOf<Pair<String, String>>()
        
        // Default category
        categories.add(Pair("$mainUrl/danh-sach/phim-moi-cap-nhat?page=$page", "Phim Mới Cập Nhật"))
        
        // Parallel lists for category configuration
        val pathKeys = listOf(PREF_CATEGORY_1, PREF_CATEGORY_2, PREF_CATEGORY_3, PREF_CATEGORY_4, PREF_CATEGORY_5, PREF_CATEGORY_6)
        val nameKeys = listOf(PREF_CATEGORY_1_NAME, PREF_CATEGORY_2_NAME, PREF_CATEGORY_3_NAME, PREF_CATEGORY_4_NAME, PREF_CATEGORY_5_NAME, PREF_CATEGORY_6_NAME)
        val defaultPaths = listOf("quoc-gia/trung-quoc", "quoc-gia/han-quoc", "danh-sach/hoat-hinh", "", "", "")
        val defaultNames = listOf("Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 4", "Danh Sách 5", "Danh Sách 6")
        
        for (i in 0 until 6) {
            val categoryPath = prefs.getString(pathKeys[i], defaultPaths[i]).orEmpty()
            if (categoryPath.isNotEmpty()) {
                val categoryName = prefs.getString(nameKeys[i], defaultNames[i]) ?: defaultNames[i]
                val categoryUrl = if (categoryPath.startsWith("http")) {
                    "$categoryPath?page=$page"
                } else {
                    "${mainUrl}/v1/api/$categoryPath?page=$page"
                }
                categories.add(Pair(categoryUrl, categoryName))
            }
        }
        
        return categories
    }
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = getCustomCategories(page)
        val homePageLists = items.map { (url, title) -> HomePageList(title, getListFromUrl(url)) }
        return newHomePageResponse(homePageLists, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/v1/api/tim-kiem?keyword=$query&limit=20"
        val response = app.get(url).text
        val data = parseJson<KKSearchResponse>(response)
        return data.data?.items?.map {
            newMovieSearchResponse(it.name ?: "", "$mainUrl/phim/${it.slug}", TvType.Movie) {
                this.posterUrl = fixPosterUrl(it.poster_url ?: it.thumb_url)
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val res = parseJson<KKDetailResponse>(response)
        val movie = res.movie ?: return null
        
        val episodeMap = mutableMapOf<String, MutableList<String>>()
        res.episodes?.forEach { server ->
            val serverName = server.server_name ?: "HLS"
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: "1"
                val existingLinks = episodeMap.getOrPut(epName) { mutableListOf() }
                existingLinks.add("${ep.link_m3u8}::${serverName}")
            }
        }

        // FIX LỖI GOM NHÓM TẬP: Chỉ lấy số đầu tiên tìm thấy trong tên tập
        val episodesList = episodeMap.map { (epName, links) ->
            newEpisode(links.joinToString("|||")) {
                this.name = "Tập $epName"
                val s = Regex("""(\d+)""").find(epName)?.value
                this.episode = s?.toIntOrNull()
            }
        }.sortedBy { it.episode }

        val finalPoster = fixPosterUrl(movie.poster_url ?: movie.thumb_url)
        val isCompleted = movie.status == "completed"
        
        // Build tag list from categories (from API)
        val movieTags = movie.category?.toMutableList() ?: mutableListOf()
        
        // Add quality if available
        movie.quality?.let { if (it.isNotEmpty()) movieTags.add(it) }

        val fullPlot = """
            Diễn viên: ${movie.actor?.joinToString(", ") ?: "Đang cập nhật"}
            
            ${movie.content ?: "Không có nội dung mô tả."}
        """.trimIndent()

        val isSeries = movie.type == "series" || movie.type == "hoathinh" || episodesList.size > 1

        return if (isSeries) {
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                // Set status to metadata
                this.showStatus = if (isCompleted) ShowStatus.Completed else ShowStatus.Ongoing
                // Add rating to metadata
                movie.tmdb?.vote_average?.let { score ->
                    if (score > 0) {
                        this.score = Score.from10(score)
                    }
                }
            }
        } else {
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, episodesList.firstOrNull()?.data ?: "") {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                // Add rating to metadata
                movie.tmdb?.vote_average?.let { score ->
                    if (score > 0) {
                        this.score = Score.from10(score)
                    }
                }
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        data.split("|||").forEach { item ->
            val parts = item.split("::")
            val link = parts.getOrNull(0) ?: ""
            val serverName = parts.getOrNull(1) ?: "HLS"
            if (link.isNotEmpty()) {
                callback.invoke(newExtractorLink(serverName, serverName, link, type = ExtractorLinkType.M3U8))
            }
        }
        return true
    }
}

// --- AUTHENTICATION MODELS ---
data class LoginRequest(
    @param:JsonProperty("username") val username: String,
    @param:JsonProperty("password") val password: String
)

data class LoginResponse(
    @param:JsonProperty("success") val success: Boolean,
    @param:JsonProperty("token") val token: String? = null,
    @param:JsonProperty("message") val message: String? = null
)

// --- DATA MODELS ---
data class KKListResponse(
    @field:JsonProperty("items") val items: List<KKItem>? = null,
    @field:JsonProperty("data") val data: KKListData? = null
)

data class KKItem(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("slug") val slug: String? = null,
    @field:JsonProperty("poster_url") val poster_url: String? = null,
    @field:JsonProperty("thumb_url") val thumb_url: String? = null
)

data class KKSearchResponse(
    @field:JsonProperty("data") val data: KKListData? = null
)

data class KKListData(
    @field:JsonProperty("items") val items: List<KKItem>? = null
)

data class KKDetailResponse(
    @field:JsonProperty("movie") val movie: KKMovie? = null,
    @field:JsonProperty("episodes") val episodes: List<KKServer>? = null
)

data class KKMovie(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("type") val type: String? = null,
    @field:JsonProperty("status") val status: String? = null,
    @field:JsonProperty("poster_url") val poster_url: String? = null,
    @field:JsonProperty("thumb_url") val thumb_url: String? = null,
    @field:JsonProperty("content") val content: String? = null,
    @field:JsonProperty("year") val year: Int? = null,
    @field:JsonProperty("episode_current") val episode_current: String? = null,
    @field:JsonProperty("episode_total") val episode_total: String? = null,
    @field:JsonProperty("quality") val quality: String? = null,
    @field:JsonProperty("actor") val actor: List<String>? = null,
    @field:JsonProperty("tmdb") val tmdb: KKTMDB? = null,
    @field:JsonProperty("category") val category: List<String>? = null
)

data class KKTMDB(
    @field:JsonProperty("vote_average") val vote_average: Double? = null
)

data class KKServer(
    @field:JsonProperty("server_name") val server_name: String? = null,
    @field:JsonProperty("server_data") val server_data: List<KKEpisode>? = null
)

data class KKEpisode(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("link_m3u8") val link_m3u8: String? = null
)
