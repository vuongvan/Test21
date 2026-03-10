package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Locale
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
        val response = app.get(url).text
        val items = try {
            val res = parseJson<KKListResponse>(response)
            res.data?.items ?: res.items ?: emptyList()
        } catch (e: Exception) {
            try {
                val searchRes = parseJson<KKSearchResponse>(response)
                searchRes.data?.items ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        return items.mapNotNull { item ->
            val title = item.name ?: return@mapNotNull null
            val slug = item.slug ?: return@mapNotNull null
            val href = "$mainUrl/phim/$slug" 
            val poster = fixPosterUrl(item.poster_url ?: item.thumb_url)

            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                val finalRating = item.tmdb?.vote_average ?: 0.0
                if (finalRating > 0) {
                    this.score = Score.from10(finalRating)
                }
            }
        }
    }
        
    private fun getCustomCategories(page: Int): List<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val categories = mutableListOf<Pair<String, String>>()
        categories.add(Pair("$mainUrl/danh-sach/phim-moi-cap-nhat?page=$page", "Phim Mới Cập Nhật"))
        
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
        
        val rawStatus = movie.status ?: ""
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
                this.name = "$epName"
                val s = Regex("""(\d+)""").find(epName)?.value
                this.episode = s?.toIntOrNull()
            }
        }.sortedBy { it.episode }

        val finalPoster = fixPosterUrl(movie.poster_url ?: movie.thumb_url)
        val movieTags = mutableListOf<String>()
        
        // 1. Tag Trạng thái: Ongoing / Completed
        //episode_total từ API
        val totalEpisodes = movie.episode_total ?: ""

        // 2. Logic xác định phim bộ: 
        // Chỉ là phim bộ nếu type là series/hoathinh VÀ episode_total khác "1"
        val isSeries = totalEpisodes != "1"

        if (isSeries) {
            val isCompleted = movie.status == "completed"
            val totalEpisodes = movie.episode_total ?: ""
            val currentFromApi = movie.episode_current ?: ""

            val tagEp = if (!isCompleted) {
                // Nếu chưa hoàn thành: (Số tập thực tế)/(Tổng tập dự kiến)
                "$currentFromApi/$totalEpisodes"
            } else {
                // Nếu đã hoàn thành: Lấy thẳng giá trị episode_current, không cắt gọt
                currentFromApi
            }
            
            movieTags.add("$tagEp")
        }
        
        // 2. KIỂM TRA NGÔN NGỮ (Lồng Tiếng / Thuyết Minh)
        movie.lang?.let { lang ->
            if (lang.contains("Thuyết Minh", ignoreCase = true)) {
                movieTags.add("Thuyết Minh")
            } else if (lang.contains("Lồng Tiếng", ignoreCase = true)) {
                movieTags.add("Lồng Tiếng")
            }
        }

        // 4. THÊM CATEGORY VÀO TAGS
        movie.category?.forEach { cat ->
            cat.name?.let { movieTags.add(it) }
        }
        
        val fullPlot = """
            ${movie.content ?: "Không có nội dung mô tả."}
        """.trimIndent()

        // ==========================================
        // THÊM LẠI LOGIC LẤY THÔNG TIN DIỄN VIÊN TỪ TMDB
        // ==========================================
        val actorsList = mutableListOf<ActorData>()
        val tmdbType = movie.tmdb?.type
        val tmdbId = movie.tmdb?.id
        
        if (!tmdbType.isNullOrEmpty() && !tmdbId.isNullOrEmpty()) {
            try {
                val tmdbUrl = "https://phimapi.com/tmdb/$tmdbType/$tmdbId"
                val tmdbRes = app.get(tmdbUrl).parsedSafe<TmdbResponse>()
                
                tmdbRes?.credits?.cast?.take(15)?.forEach { cast ->
                    val actorName = cast.name ?: return@forEach
                    val actorImage = cast.profile_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                    actorsList.add(ActorData(Actor(actorName, actorImage), roleString = cast.character))
                }
            } catch (e: Exception) {
                // Lỗi API bên thứ 3 thì bỏ qua để không sập app
            }
        }
        // ==========================================

        return if (isSeries) {  
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.showStatus = if (rawStatus.equals("completed", ignoreCase = true) || rawStatus.equals("hoàn thành", ignoreCase = true)) ShowStatus.Completed else ShowStatus.Ongoing
                
                // Add rating to metadata
                val scoreValue = movie.tmdb?.vote_average
                if (scoreValue != null && scoreValue > 0) {
                    this.score = Score.from10(scoreValue)
                }
                
                // Add actors to metadata
                this.actors = actorsList.takeIf { it.isNotEmpty() }
            }
        } else {
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, episodesList.firstOrNull()?.data ?: "") {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                
                // Add rating to metadata
                val scoreValue = movie.tmdb?.vote_average
                if (scoreValue != null && scoreValue > 0) {
                    this.score = Score.from10(scoreValue)
                }
                
                // Add actors to metadata
                this.actors = actorsList.takeIf { it.isNotEmpty() }
            }
        }
    }
    
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.isEmpty()) return false
        callback.invoke(newExtractorLink("HLS", "HLS", data, type = ExtractorLinkType.M3U8))
        return true
    }
}

// --- DATA MODELS ---
data class KKListResponse(
    @param:JsonProperty("items") val items: List<KKItem>? = null, 
    @param:JsonProperty("data") val data: KKListData? = null
)

data class KKSearchResponse(
    @param:JsonProperty("data") val data: KKListData? = null
)

data class KKListData(
    @param:JsonProperty("items") val items: List<KKItem>? = null
)

data class KKItem(
    @param:JsonProperty("name") val name: String? = null, 
    @param:JsonProperty("slug") val slug: String? = null, 
    @param:JsonProperty("poster_url") val poster_url: String? = null, 
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("tmdb") val tmdb: KKTMDB? = null
)

data class KKDetailResponse(
    @param:JsonProperty("movie") val movie: KKMovie? = null, 
    @param:JsonProperty("episodes") val episodes: List<KKServer>? = null
)

data class KKMovie(
    @param:JsonProperty("name") val name: String? = null, 
    @param:JsonProperty("type") val type: String? = null, 
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("actor") val actor: List<String>? = null,
    @param:JsonProperty("tmdb") val tmdb: KKTMDB? = null,
    @param:JsonProperty("category") val category: List<KKCategory>? = null, // Thêm lại dòng này
    @param:JsonProperty("country") val country: List<KKCountry>? = null,   // Thêm lại dòng này
    @param:JsonProperty("lang") val lang: String? = null
)

// Định nghĩa 2 class còn thiếu này:
data class KKCategory(@param:JsonProperty("name") val name: String? = null)
data class KKCountry(@param:JsonProperty("name") val name: String? = null)


data class KKServer(
    @param:JsonProperty("server_name") val server_name: String? = null, 
    @param:JsonProperty("server_data") val server_data: List<KKEpisode>? = null
)

data class KKEpisode(
    @param:JsonProperty("name") val name: String? = null, 
    @param:JsonProperty("link_m3u8") val link_m3u8: String? = null
)

data class KKTMDB(
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("vote_average") val vote_average: Double? = null
)

data class TmdbResponse(
    @param:JsonProperty("credits") val credits: TmdbCredits? = null
)

data class TmdbCredits(
    @param:JsonProperty("cast") val cast: List<TmdbCast>? = null
)

data class TmdbCast(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("character") val character: String? = null,
    @param:JsonProperty("profile_path") val profile_path: String? = null
)
