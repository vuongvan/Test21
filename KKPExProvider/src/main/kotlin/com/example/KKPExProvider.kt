package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.addDubStatus
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
    
    // 1. XỬ LÝ LINH HOẠT CẢ 2 CẤU TRÚC JSON
    val items = try {
        val jsonSearch = parseJson<KKSearchResponse>(response)
        if (jsonSearch.data?.items != null) {
            jsonSearch.data.items // Cấu trúc Search (có .data)
        } else {
            // Nếu không có .data, thử parse theo cấu trúc Phim Mới (items trực tiếp)
            parseJson<KKListResponse>(response).items ?: emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    return items.mapNotNull { item ->
        val title = item.name ?: return@mapNotNull null
        val slug = item.slug ?: return@mapNotNull null
        val href = "$mainUrl/phim/$slug"
        
        // Đảm bảo dùng KKExUtils để fix URL ảnh nếu cần
        val poster = KKExUtils.fixPosterUrl(item.poster_url ?: item.thumb_url)

        newAnimeSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
            
            // 2. SỬA LỖI 3232: Chỉ lấy số tập hiện tại trước dấu "/"
            val epText = item.episode_current ?: ""
            val currentEp = epText.substringBefore("/")
                                .filter { it.isDigit() }
                                .toIntOrNull()
            
            // 3. XỬ LÝ NGÔN NGỮ (Badge)
            val langStr = item.lang?.lowercase() ?: ""
            val isDub = langStr.contains("thuyết minh") || langStr.contains("lồng tiếng")
            val isSub = langStr.contains("vietsub") || langStr.contains("phụ đề")

            // Hiển thị đồng thời L.Tiếng và P.Đề nếu phim có cả hai (giống ảnh Trending)
            if (currentEp != null) {
                if (isDub) addDub(currentEp) 
                if (isSub) addSub(currentEp)
            } 

            // 4. XỬ LÝ CHẤT LƯỢNG
            this.quality = when (item.quality?.uppercase()) {
                "CAM", "HDCAM" -> SearchQuality.Cam
                else -> SearchQuality.HD
            }

            // 5. XỬ LÝ ĐIỂM SỐ (Lấy từ tmdb.vote_average)
            val rating = item.tmdb?.vote_average ?: 0.0
            if (rating > 0) {
                this.score = Score.from10(rating)
            }
        }
    }
    }
    
 



    private fun getCustomCategories(page: Int): List<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val categories = mutableListOf<Pair<String, String>>()
        
        // Fix cứng phim mới cập nhật
        categories.add(Pair("$mainUrl/v1/api/danh-sach/phim-bo?page=$page", "Phim bộ"))
        
        val pathKeys = listOf(PREF_CATEGORY_1, PREF_CATEGORY_2, PREF_CATEGORY_3, PREF_CATEGORY_4, PREF_CATEGORY_5, PREF_CATEGORY_6)
        val nameKeys = listOf(PREF_CATEGORY_1_NAME, PREF_CATEGORY_2_NAME, PREF_CATEGORY_3_NAME, PREF_CATEGORY_4_NAME, PREF_CATEGORY_5_NAME, PREF_CATEGORY_6_NAME)
        val defaultPaths = listOf("v1/api/quoc-gia/trung-quoc", "v1/api/quoc-gia/han-quoc", "v1/api/danh-sach/hoat-hinh", "", "", "")
        val defaultNames = listOf("Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 4", "Danh Sách 5", "Danh Sách 6")
        
        for (i in 0 until 6) {
            val categoryPath = prefs.getString(pathKeys[i], defaultPaths[i]).orEmpty()
            if (categoryPath.isNotEmpty()) {
                val categoryName = prefs.getString(nameKeys[i], defaultNames[i]) ?: defaultNames[i]
                
                // Xác định base URL
                val baseUrl = if (categoryPath.startsWith("http")) {
                    categoryPath
                } else {
                    "${mainUrl}/$categoryPath"
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
    // API search của bạn yêu cầu keyword và có thể thêm limit
    val url = "$mainUrl/v1/api/tim-kiem?keyword=$query&limit=50"
    
    // Gọi hàm dùng chung để xử lý toàn bộ nhãn và dữ liệu
    return getListFromUrl(url)
}

    
    
        
    override suspend fun load(url: String): LoadResponse? {
        var response = app.get(url).text
        val res = parseJson<KKDetailResponse>(response)
        val movie = res.movie ?: return null
        
        val rawStatus = movie.status ?: ""
        
        val totalEpisodes = movie.episode_total ?: ""
        val isSeries = totalEpisodes != "1"
        
        // ==========================================
        // 1. LẤY METADATA TỪNG TẬP TỪ TMDB (THUMBNAIL, ĐIỂM, NGÀY)
        // ==========================================
        var tmdbId = movie.tmdb?.id
        val tmdbSeasonNum = movie.tmdb?.season
        val tmdbEpisodesMap = mutableMapOf<Int, TmdbEpisodeDetail>()
        val tmdbType = if (isSeries) "tv" else "movie"

        if (tmdbId.isNullOrEmpty()) {
            tmdbId = KKExUtils.findTmdbId(movie.name, movie.origin_name, movie.year, isSeries)
        }

         
        // Chỗ này nhớ sửa lại: Nếu tìm được tmdbId nhưng không có tmdbSeasonNum (do web thiếu), mặc định cho season = 1
        val finalSeasonNum = tmdbSeasonNum ?: 1 
        
        
        if (isSeries && !tmdbId.isNullOrEmpty()) {
            val seasonData = KKExUtils.fetchTmdbSeason(tmdbId, finalSeasonNum)
            seasonData?.episodes?.forEach { ep ->
                ep.episodeNumber?.let { tmdbEpisodesMap[it] = ep }
            }
        }

        // ==========================================
        // 2. XỬ LÝ DANH SÁCH TẬP PHIM (MAP THÔNG TIN TỪ TMDB)
        // ==========================================
        val episodeMap = mutableMapOf<String, MutableList<String>>()
        res.episodes?.forEach { server ->
            val serverName = server.server_name ?: "HLS"
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: "1"
                val existingLinks = episodeMap.getOrPut(epName) { mutableListOf() }
                existingLinks.add("${ep.link_m3u8}::${serverName}")
            }
        }

        val episodesList = episodeMap.map { (epName, links) ->
            val s = Regex("""(\d+)""").find(epName)?.value
            val epNum = s?.toIntOrNull()
            val tmdbEp = tmdbEpisodesMap[epNum] // Tra cứu thông tin TMDB dựa theo số tập

            newEpisode(links.joinToString("|||")) {
                //this.name = "$epName"
                this.name = tmdbEp?.name ?: "$epName"
                this.episode = epNum
                
                // Gắn Thumbnail
                tmdbEp?.stillPath?.let {
                    this.posterUrl = "https://image.tmdb.org/t/p/w300$it"
                }
                
                // Gắn Ngày phát sóng
                this.description = tmdbEp?.overview
                
                // Xử lý điểm đánh giá theo thang điểm 10 của Cloudstream
                val rating = tmdbEp?.voteAverage
                if (rating != null && rating > 0) {
                    this.score = Score.from10(rating)
                }
                this.runTime = tmdbEp?.runTime
                // Định dạng ngày chiếu sang tiếng Việt và gán vào UI
                this.addDate(tmdbEp?.airDate)
            }
        }.sortedBy { it.episode }

        // ==========================================

        val finalPoster = KKExUtils.fixPosterUrl(movie.thumb_url ?: movie.poster_url)
        val movieTags = mutableListOf<String>()
        
        // 1. Tag Trạng thái: Ongoing / Completed
        //episode_total từ API
         // 2. Logic xác định phim bộ: 
        // Chỉ là phim bộ nếu type là series/hoathinh VÀ episode_total khác "1"
        
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
        
        //val tmdbType = if (isSeries) "tv" else "movie"
        
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
        //poster tmdb
        val tmdbDetails = tmdbExtra
    // 2. Ưu tiên Poster từ TMDB, fallback về OPhim
       val posterUrl = tmdbDetails?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" } 
                    ?: KKExUtils.fixPosterUrl(movie.thumb_url ?: movie.poster_url)
        // --- LOGIC MỚI: Lấy ngẫu nhiên backdrop ---
        val tmdbBackdrops = tmdbId?.let { KKExUtils.fetchTmdbBackdrops(tmdbType, it) }
        
        // Ưu tiên 1: Chọn ngẫu nhiên từ danh sách ảnh TMDB
        // Ưu tiên 2: Dùng backdrop mặc định từ tmdbDetails (nếu gọi api images lỗi)
        // Ưu tiên 3: Fallback về thumb của web phim
        val finalBackdropUrl = if (!tmdbBackdrops.isNullOrEmpty()) {
            tmdbBackdrops.random() // Hàm random() của Kotlin sẽ chọn ngẫu nhiên 1 phần tử
        } else {
            tmdbDetails?.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
                ?: KKExUtils.fixPosterUrl(movie.thumb_url ?: movie.poster_url)
        }
        // ------------------------------------------
      //---------
                return if (isSeries) {  
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
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
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
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
