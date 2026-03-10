package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

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
    private val tmdbApiKey ="661c6c1d38ed79fb876dc2eba6ffbfa0"
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

    private suspend fun fetchTmdbCast(tmdbType: String, tmdbId: String): List<ActorData>? {
    val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/credits?api_key=$tmdbApiKey&language=vi-VN"
    return try {
        val res = app.get(url).parsedSafe<TmdbCreditsResponse>()
        res?.cast?.take(15)?.map { cast ->
            val actorImg = cast.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" }
            ActorData(Actor(cast.name ?: "", actorImg), roleString = cast.character)
        }
    } catch (e: Exception) {
        null
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
        
        return data.data?.items?.mapNotNull { item ->
            val title = item.name ?: return@mapNotNull null
            val href = "$mainUrl/phim/${item.slug}"
            
            // Dùng Movie hay TvSeries ở đây đều được, quan trọng là phần bên trong { }
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = fixPosterUrl(item.poster_url ?: item.thumb_url)
                
                // --- THÊM ĐOẠN NÀY ĐỂ HIỆN ĐIỂM KHI TÌM KIẾM ---
                val rating = item.tmdb?.vote_average ?: 0.0
                if (rating > 0) {
                    this.score = Score.from10(rating)
                }
                // ----------------------------------------------
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
        movie.category?.forEach { cat: KKCategory -> // Chỉ định rõ kiểu KKCategory
            cat.name?.let { movieTags.add(it) }
        }

        
        val fullPlot = """
            ${movie.content ?: "Không có nội dung mô tả."}
        """.trimIndent()

        // ==========================================
        // THÊM LẠI LOGIC LẤY THÔNG TIN DIỄN VIÊN TỪ TMDB
        // ==========================================
         // --- PHẦN LẤY DIỄN VIÊN ---
        val tmdbId = movie.tmdb?.id
        val tmdbType = if (isSeries) "tv" else "movie"
        
        // Gọi hàm đã viết ở trên
        val actorsList = if (!tmdbId.isNullOrEmpty()) {
            fetchTmdbCast(tmdbType, tmdbId) 
        } else null

        // Nếu TMDB không có, lấy danh sách tên từ API gốc của bạn làm phương án dự phòng
        val finalActors = actorsList ?: movie.actor?.map { 
            ActorData(Actor(it, null), roleString = "Diễn viên") 
        }
        

        // Bước B: Nếu TMDB không có dữ liệu, dùng danh sách tên từ API gốc (Trường "actor" trong JSON)
        if (actorsList.isEmpty()) {
            movie.actor?.forEach { name ->
                if (name.isNotBlank()) {
                    // Hiển thị tên với ảnh mặc định của CloudStream
                    actorsList.add(ActorData(Actor(name, null), roleString = "Diễn viên"))
                }
            }
        }
        
        // ==========================================

                return if (isSeries) {  
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.showStatus = if (rawStatus.contains("completed", true) || rawStatus.contains("hoàn thành", true)) ShowStatus.Completed else ShowStatus.Ongoing
                this.score = movie.tmdb?.vote_average?.let { Score.from10(it) }
                this.actors = actorsList.takeIf { it.isNotEmpty() }
            }
        } else {
            // Lấy dữ liệu link từ tập đầu tiên cho phim lẻ
            val movieData = episodesList.firstOrNull()?.data ?: ""
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, movieData) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.score = movie.tmdb?.vote_average?.let { Score.from10(it) }
                this.actors = actorsList.takeIf { it.isNotEmpty() }
            }
       }
                
    }
    
    override suspend fun loadLinks(
        data: String, 
        isCasting: Boolean, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false

        // Tách các server (ngăn cách bởi |||)
        data.split("|||").forEach { serverData ->
            // Tách link và tên (ngăn cách bởi ::)
            val parts = serverData.split("::")
            val url = parts.getOrNull(0) ?: return@forEach
            val serverName = parts.getOrNull(1) ?: "HLS"

            // Dùng đúng cú pháp newExtractorLink cũ của bạn, thay data bằng url
            callback.invoke(
                newExtractorLink(
                    serverName, 
                    serverName, 
                    url, 
                    type = ExtractorLinkType.M3U8
                )
            )
        }
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
// Cần thêm các Data Class này ở cuối file để parse JSON tự động
data class TmdbCreditsResponse(
    val cast: List<TmdbCast>? = null
)

data class TmdbCast(
    val name: String? = null,
    val character: String? = null,
    val profile_path: String? = null
)
