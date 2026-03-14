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
    override var name = "KK Phim"
    override val hasMainPage = true
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    
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
            val poster = KKExUtils.fixPosterUrl(item.poster_url ?: item.thumb_url)

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
        
        // Fix cứng phim mới cập nhật
        categories.add(Pair("$mainUrl/danh-sach/phim-moi-cap-nhat?page=$page", "Phim Mới Cập Nhật"))
        
        val pathKeys = listOf(PREF_CATEGORY_1, PREF_CATEGORY_2, PREF_CATEGORY_3, PREF_CATEGORY_4, PREF_CATEGORY_5, PREF_CATEGORY_6)
        val nameKeys = listOf(PREF_CATEGORY_1_NAME, PREF_CATEGORY_2_NAME, PREF_CATEGORY_3_NAME, PREF_CATEGORY_4_NAME, PREF_CATEGORY_5_NAME, PREF_CATEGORY_6_NAME)
        val defaultPaths = listOf("quoc-gia/trung-quoc", "quoc-gia/han-quoc", "danh-sach/hoat-hinh", "", "", "")
        val defaultNames = listOf("Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 4", "Danh Sách 5", "Danh Sách 6")
        
        for (i in 0 until 6) {
            val categoryPath = prefs.getString(pathKeys[i], defaultPaths[i]).orEmpty()
            if (categoryPath.isNotEmpty()) {
                val categoryName = prefs.getString(nameKeys[i], defaultNames[i]) ?: defaultNames[i]
                
                // Xác định base URL
                val baseUrl = if (categoryPath.startsWith("http")) {
                    categoryPath
                } else {
                    "${mainUrl}/v1/api/$categoryPath"
                }

                // XỬ LÝ NỐI ? THEO CHUẨN URL
                val finalUrl = if (baseUrl.contains("?")) {
                    "$baseUrl&page=$page" // Nếu đã có ? thì dùng &
                } else {
                    "$baseUrl?page=$page" // Nếu chưa có ? thì dùng ?
                }
                
                categories.add(Pair(finalUrl, categoryName))
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
                this.posterUrl = KKExUtils.fixPosterUrl(item.poster_url ?: item.thumb_url)
                
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

        val finalPoster = KKExUtils.fixPosterUrl(movie.thumb_url ?: movie.poster_url)
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
                // --- PHẦN LẤY DIỄN VIÊN ---
        val tmdbId = movie.tmdb?.id
        val tmdbType = if (isSeries) "tv" else "movie"
        
        // Lấy danh sách từ TMDB (có thể null)
        val tmdbActors = if (!tmdbId.isNullOrEmpty()) {
            KKExUtils.fetchTmdbCast(tmdbType, tmdbId) 
        } else null

        // Chuyển đổi list dự phòng từ API gốc nếu TMDB không có
        val backupActors = movie.actor?.map { 
            ActorData(Actor(it, null), roleString = "Diễn viên") 
        }

        // Ưu tiên TMDB, nếu không có thì lấy backup, nếu không có nữa thì rỗng
        val finalActors = tmdbActors ?: backupActors ?: emptyList()
        
        // ==========================================
        
        //Score
        val tmdbExtra = tmdbId?.let { KKExUtils.fetchTmdbDetails(tmdbType, it) }
        val finalRating = tmdbExtra?.vote_average ?: movie.tmdb?.vote_average ?: 0.0
        
        //---------
                return if (isSeries) {  
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.showStatus = if (rawStatus.contains("completed", true) || rawStatus.contains("hoàn thành", true)) ShowStatus.Completed else ShowStatus.Ongoing
                this.score = finalRating.let { if (it > 0) Score.from10(it) else null }
                this.actors = finalActors
            }
        } else {
            // Lấy dữ liệu link từ tập đầu tiên cho phim lẻ
            val movieData = episodesList.firstOrNull()?.data ?: ""
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, movieData) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.score = finalRating.let { if (it > 0) Score.from10(it) else null }
                this.actors = finalActors
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
