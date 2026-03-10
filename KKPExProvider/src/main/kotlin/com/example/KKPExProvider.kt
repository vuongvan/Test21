package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Score
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
        private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        val response = app.get(url).text
        
        // Cố gắng parse JSON thành danh sách các item
        // PhimApi thường trả về items nằm ở res.data?.items (danh sách) hoặc res.items (tìm kiếm/trang chủ)
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

        // Map các item thành giao diện SearchResponse của CloudStream
        return items.mapNotNull { item ->
            val title = item.name ?: return@mapNotNull null
            val slug = item.slug ?: return@mapNotNull null
            
            // Xây dựng URL chi tiết để truyền vào hàm load()
            val href = "$mainUrl/phim/$slug" 
            val poster = fixPosterUrl(item.poster_url ?: item.thumb_url)

            // Khởi tạo hiển thị cho từng item trên màn hình
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                
                // ==========================================
                // LOGIC HIỂN THỊ ĐÁNH GIÁ (RATING) TRÊN ẢNH BÌA
                // ==========================================
                item.tmdb?.vote_average?.let { score ->
                    if (score > 0) {
                        // CloudStream sử dụng hệ số Int chia cho 1000.
                        // Ví dụ: score là 8.2 -> nhân 1000 = 8200.
                        // CloudStream sẽ tự động định dạng hiển thị thành 8.2 ★
                        this.rating = (score * 1000).toInt()
                    }
                }
            }
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
        
        // ... (Giữ nguyên các logic phân tích danh sách tập episodesList, Tags, v.v...) ...

        // ==========================================
        // THÊM ĐOẠN LOGIC LẤY THÔNG TIN DIỄN VIÊN TỪ TMDB
        // ==========================================
        val actorsList = mutableListOf<ActorData>()
        val tmdbType = movie.tmdb?.type
        val tmdbId = movie.tmdb?.id
        
        // Nếu API gốc có type (tv/movie) và id của TMDB
        if (!tmdbType.isNullOrEmpty() && !tmdbId.isNullOrEmpty()) {
            try {
                // Gọi sang API của TMDB được proxy qua phimapi
                val tmdbUrl = "https://phimapi.com/tmdb/$tmdbType/$tmdbId"
                val tmdbRes = app.get(tmdbUrl).parsedSafe<TmdbResponse>()
                
                // Bóc tách danh sách cast, giới hạn lấy 15 người đầu tiên cho nhẹ App
                tmdbRes?.credits?.cast?.take(15)?.forEach { cast ->
                    val actorName = cast.name ?: return@forEach
                    // Thêm prefix tên miền ảnh của TMDB
                    val actorImage = cast.profile_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                    val role = cast.character
                    
                    // Thêm vào danh sách Actors của CloudStream
                    actorsList.add(ActorData(Actor(actorName, actorImage), roleString = role))
                }
            } catch (e: Exception) {
                // Lỗi API thứ 3 thì bỏ qua để không làm sập trang load phim
            }
        }
        // ==========================================

        return if (isSeries) {
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.showStatus = if (movie.status == "completed") ShowStatus.Completed else ShowStatus.Ongoing
                
                // THÊM DÒNG NÀY ĐỂ HIỂN THỊ DIỄN VIÊN
                this.actors = actorsList.takeIf { it.isNotEmpty() }
            }
        } else {
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, episodesList.firstOrNull()?.data ?: "") {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                
                // THÊM DÒNG NÀY ĐỂ HIỂN THỊ DIỄN VIÊN
                this.actors = actorsList.takeIf { it.isNotEmpty() }
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
// --- DATA MODELS (ĐÃ FIX LỖI REDECLARATION) ---

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
    @param:JsonProperty("category") val category: List<KKCategory>? = null,
    @param:JsonProperty("country") val country: List<KKCountry>? = null,
    @param:JsonProperty("lang") val lang: String? = null // Trường này nhận giá trị "Lồng Tiếng"
)

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
// --- CẬP NHẬT LẠI KKTMDB ---
data class KKTMDB(
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("vote_average") val vote_average: Double? = null
)

// --- CẬP NHẬT LẠI KKItem (Để lấy điểm ở màn hình danh sách) ---
data class KKItem(
    @param:JsonProperty("name") val name: String? = null, 
    @param:JsonProperty("slug") val slug: String? = null, 
    @param:JsonProperty("poster_url") val poster_url: String? = null, 
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("tmdb") val tmdb: KKTMDB? = null // <-- Thêm dòng này
)

// --- THÊM DATA CLASS CHO TMDB ACTORS ---
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

