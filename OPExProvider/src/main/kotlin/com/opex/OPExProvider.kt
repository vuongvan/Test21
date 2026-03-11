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
    private val tmdbApiKey = "YOUR_API_KEY_HERE"

private suspend fun fetchTmdbDetails(tmdbType: String, tmdbId: String): TmdbDetailResponse? {
    val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$tmdbApiKey&language=vi-VN"
    return try { app.get(url).parsedSafe<TmdbDetailResponse>() } catch (e: Exception) { null }
}

private suspend fun fetchTmdbCast(tmdbType: String, tmdbId: String): List<ActorData>? {
    val url = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/credits?api_key=$tmdbApiKey&language=vi-VN"
    return try {
        val res = app.get(url).parsedSafe<TmdbCreditsResponse>()
        res?.cast?.take(15)?.map { cast ->
            val actorImg = cast.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" }
            ActorData(Actor(cast.name ?: "", actorImg), roleString = cast.character)
        }
    } catch (e: Exception) { null }
}


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
            
            items?.map { it ->
                // Lấy điểm số an toàn (Double)
                val tmdbScore = it.tmdb?.vote_average ?: 0.0
                val imdbScore = it.imdb?.vote_average ?: 0.0
                val finalRating = if (tmdbScore > 0) tmdbScore else imdbScore

                // Hiển thị tên phim kèm ngôn ngữ để người dùng dễ chọn
                val displayName = if (!it.lang.isNullOrBlank()) "${it.name}" else it.name ?: ""

                newMovieSearchResponse(displayName, "$mainUrl/v1/api/phim/${it.slug}", TvType.Movie) {
                    this.posterUrl = if (it.poster_url?.startsWith("http") == true) {
                        it.poster_url 
                    } else {
                        "$imgDomain${it.poster_url ?: it.thumb_url}"
                    }
                    
                    // Gán điểm Rating (Cloudstream tự hiểu hệ số 10)
                    if (finalRating > 0) {
                        this.score = Score.from10(finalRating)
                    }

                    // Gán chất lượng: Ưu tiên lấy từ API, nếu lỗi thì mặc định HD
                    this.quality = when (it.quality?.uppercase()) {
                        "CAM" -> SearchQuality.Cam
                        "SD" -> SearchQuality.SD
                        else -> SearchQuality.HD
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) { 
            emptyList() 
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.split("/").last()
        val movieResponse = app.get("$mainUrl/v1/api/phim/$slug").text
        val movieRoot = parseJson<OPRootResponse>(movieResponse)
        val data = movieRoot.data ?: return null
        val movie = data.item ?: return null

        // Xác định loại phim và ID TMDB
        // Lưu ý: data class OPTmdb cần có biến id: String? hoặc Int?
        val tmdbId = movie.tmdb?.id?.toString() // Bạn nhớ thêm 'val id: Any?' vào class OPTmdb nhé
        val epTotalNumber = movie.episode_total?.replace("Tập", "", true)?.trim() ?: ""
        val isSingleEpisode = epTotalNumber == "1" || movie.category?.any { it.name?.contains("Phim lẻ", true) == true } ?: false
        val tmdbType = if (isSingleEpisode) "movie" else "tv"

        // Lấy dữ liệu TMDB song song để tối ưu tốc độ
        val actorsList = tmdbId?.let { fetchTmdbCast(tmdbType, it) }
        val tmdbExtra = tmdbId?.let { fetchTmdbDetails(tmdbType, it) }

        // --- THÔNG TIN CƠ BẢN ---
        val movieName = movie.name?.split("-", "[")?.first()?.trim() ?: "OPhim"
        val poster = data.seoOnPage?.seoSchema?.image ?: ""
        val movieYear = movie.year
        // Ưu tiên nội dung từ TMDB vì nó thường đầy đủ hơn
        val movieContent = tmdbExtra?.overview ?: movie.content ?: ""
        
        val metaTags = mutableListOf<String>()
        val rawStatus = movie.status ?: ""
        
        if (!isSingleEpisode) {
            val epCurrent = movie.episode_current ?: ""
            val displayProgress = if (rawStatus.contains("ongoing", true)) {
                val curr = epCurrent.replace("Tập", "", true).trim()
                val total = movie.episode_total?.replace("Tập", "", true)?.trim() ?: ""
                if (curr.isNotEmpty() && total.isNotEmpty()) "$curr/$total Tập" else epCurrent
            } else epCurrent
            if (displayProgress.isNotEmpty()) metaTags.add(displayProgress)
        }
        
        movie.lang?.let { l -> l.split("+").forEach { if (!it.contains("Vietsub", true)) metaTags.add(it.trim()) } }
        movie.category?.forEach { it.name?.let { n -> metaTags.add(n) } }

        // Xử lý Tập phim
        val epMap = mutableMapOf<String, MutableList<String>>()
        movie.episodes?.forEach { server ->
            server.server_data?.forEach { ep ->
                val n = ep.name ?: ""
                val l = ep.link_m3u8 ?: ""
                if (n.isNotEmpty() && l.isNotEmpty()) {
                    epMap.getOrPut(n) { mutableListOf() }.add("$l|${server.server_name}")
                }
            }
        }
        
        val episodeList = epMap.map { (epName, links) ->
            newEpisode(links.joinToString(",")) {
                this.name = if (isSingleEpisode) "Full" else "Tập $epName"
                this.episode = """(\d+)""".toRegex().find(epName)?.groupValues?.get(1)?.toIntOrNull()
            }
        }.sortedBy { it.episode }

        val tvType = if (isSingleEpisode) TvType.Movie else TvType.TvSeries
        val plotClean = movieContent.replace(Regex("<.*?>"), "").replace("\\n", "\n")
        
        // Ưu tiên điểm từ TMDB API, nếu không có thì lấy từ OPhim
        val finalRating = tmdbExtra?.vote_average ?: movie.tmdb?.vote_average ?: 0.0

        return if (tvType == TvType.Movie) {
            newMovieLoadResponse(movieName, url, TvType.Movie, episodeList.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                this.tags = metaTags
                this.actors = actorsList
                if (finalRating > 0) this.score = Score.from10(finalRating)
            }
        } else {
            newTvSeriesLoadResponse(movieName, url, tvType, episodeList) {
                this.posterUrl = poster
                this.plot = plotClean
                this.year = movieYear
                this.tags = metaTags
                this.actors = actorsList
                if (finalRating > 0) this.score = Score.from10(finalRating)
                this.showStatus = if (rawStatus.contains("complete", true) || rawStatus.contains("hoàn thành", true)) ShowStatus.Completed else ShowStatus.Ongoing
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
    @param:JsonProperty("items") val items: List<OPItem>? = null, 
    @param:JsonProperty("data") val data: OPListData? = null
)

data class OPListData(
    @param:JsonProperty("items") val items: List<OPItem>? = null
)

data class OPItem(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null,
    @param:JsonProperty("origin_name") val origin_name: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("_id") val _id: String? = null,
    @param:JsonProperty("modified") val modified: OPModified? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("imdb") val imdb: OPImdbListItem? = null
)

data class OPModified(
    @param:JsonProperty("time") val time: String? = null
)

data class OPImdbListItem(
    @param:JsonProperty("vote_average") val vote_average: Double? = null
)

data class OPRootResponse(
    @param:JsonProperty("data") val data: OPDataContent? = null
)

data class OPDataContent(
    @param:JsonProperty("seoOnPage") val seoOnPage: OPSeoOnPage? = null,
    @param:JsonProperty("item") val item: OPItemDetail? = null
)

data class OPSeoOnPage(
    @param:JsonProperty("seoSchema") val seoSchema: OPSeoSchema? = null
)

data class OPSeoSchema(
    @param:JsonProperty("image") val image: String? = null
)

data class OPItemDetail(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("category") val category: List<OPCat>? = null,
    @param:JsonProperty("episodes") val episodes: List<OPServer>? = null
)

data class OPTmdb(
    @param:JsonProperty("vote_average") val vote_average: Double? = null,
    @param:JsonProperty("id") val id: Any? = null // Thêm dòng này để lấy ID gọi qua TMDB API
)

data class OPCat(
    @param:JsonProperty("name") val name: String? = null
)

data class OPServer(
    @param:JsonProperty("server_name") val server_name: String? = null, 
    @param:JsonProperty("server_data") val server_data: List<OPEpisode>? = null
)

data class OPEpisode(
    @param:JsonProperty("name") val name: String? = null, 
    @param:JsonProperty("link_m3u8") val link_m3u8: String? = null
)

data class OPPeopleResponse(
    @param:JsonProperty("data") val data: OPPeopleData? = null
)

data class OPPeopleData(
    @param:JsonProperty("peoples") val peoples: List<OPPerson>? = null,
    @param:JsonProperty("profile_sizes") val profileSizes: OPProfileSizes? = null
)

data class OPProfileSizes(
    @param:JsonProperty("h632") val h632: String? = null
)

data class OPPerson(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("character") val character: String? = null,
    @param:JsonProperty("profile_path") val profilePath: String? = null,
    @param:JsonProperty("known_for_department") val department: String? = null
)

data class OPListItem(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("imdb") val imdb: OPImdb? = null
)

data class OPImdb(
    @param:JsonProperty("vote_average") val vote_average: Double? = null
)
// --- TMDB DATA CLASSES ---
data class TmdbCreditsResponse(
    @param:JsonProperty("cast") val cast: List<TmdbCast>? = null
)

data class TmdbCast(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("profile_path") val profile_path: String? = null,
    @param:JsonProperty("character") val character: String? = null
)

data class TmdbDetailResponse(
    @param:JsonProperty("vote_average") val vote_average: Double? = null,
    @param:JsonProperty("overview") val overview: String? = null
)

