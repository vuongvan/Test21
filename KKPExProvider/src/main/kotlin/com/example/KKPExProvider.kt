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
    private val tmdbApiKey = "YOUR_API_KEY_HERE" // Giữ nguyên chữ này để lệnh sed tìm thấy
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
        
        val tmdbId = movie.tmdb?.id
        val isSeries = movie.episodeTotal != "1"
        
        // --- A. LẤY METADATA TẬP PHIM TỪ TMDB (ẢNH, TÊN, NGÀY) ---
        val tmdbEpisodesMap = mutableMapOf<Int, TmdbEpisodeDetail>()
        if (isSeries && !tmdbId.isNullOrEmpty()) {
            try {
                val tmdbSeasonUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/1?api_key=$tmdbApiKey&language=vi-VN"
                val sRes = app.get(tmdbSeasonUrl).parsedSafe<TmdbSeasonResponse>()
                sRes?.episodes?.forEach { it.episodeNumber?.let { num -> tmdbEpisodesMap[num] = it } }
            } catch (e: Exception) {}
        }

        // --- B. XỬ LÝ DANH SÁCH TẬP (GIỮ LOGIC GOM SERVER CỦA BẠN) ---
        val episodeMap = mutableMapOf<String, MutableList<String>>()
        res.episodes?.forEach { server ->
            server.serverData?.forEach { ep ->
                val epName = ep.name ?: "1"
                episodeMap.getOrPut(epName) { mutableListOf() }.add("${ep.linkM3u8}::${server.serverName ?: "HLS"}")
            }
        }

        val episodesList = episodeMap.map { (epName, links) ->
            val epNum = Regex("""(\d+)""").find(epName)?.value?.toIntOrNull()
            val tmdbEp = tmdbEpisodesMap[epNum]

            newEpisode(links.joinToString("|||")) {
                this.name = if (tmdbEp?.name != null) "Tập $epNum: ${tmdbEp.name}" else "Tập $epName"
                this.episode = epNum
                this.posterUrl = tmdbEp?.stillPath?.let { "https://image.tmdb.org/t/p/w300$it" }
                this.description = tmdbEp?.overview
                this.date = tmdbEp?.airDate
            }
        }.sortedBy { it.episode }

        // --- C. KHÔI PHỤC TAGS & CATEGORY ---
        val movieTags = mutableListOf<String>()
        if (isSeries) {
            val tagEp = if (movie.status == "completed") movie.episodeCurrent else "${movie.episodeCurrent}/${movie.episodeTotal}"
            tagEp?.let { movieTags.add(it) }
        }
        movie.lang?.let { if (it.contains("Thuyết Minh", true)) movieTags.add("Thuyết Minh") }
        movie.category?.forEach { it.name?.let { cat -> movieTags.add(cat) } }

        // --- D. LẤY DIỄN VIÊN ---
        val finalActors = if (!tmdbId.isNullOrEmpty()) {
            fetchTmdbCast(if (isSeries) "tv" else "movie", tmdbId) 
        } else {
            movie.actor?.map { ActorData(Actor(it, null), roleString = "Diễn viên") }
        } ?: emptyList()

        val finalPoster = fixPosterUrl(movie.posterUrl ?: movie.thumbUrl)

        // --- E. TRẢ VỀ RESPONSE (CÓ SCORE VÀ STATUS) ---
        return if (isSeries) {
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = movie.content?.replace(Regex("<.*?>"), "")
                this.tags = movieTags
                this.actors = finalActors
                this.showStatus = if (movie.status?.contains("completed", true) == true) ShowStatus.Completed else ShowStatus.Ongoing
                movie.tmdb?.voteAverage?.let { if (it > 0) this.score = Score.from10(it) }
            }
        } else {
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, episodesList.firstOrNull()?.data ?: "") {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = movie.content?.replace(Regex("<.*?>"), "")
                this.tags = movieTags
                this.actors = finalActors
                movie.tmdb?.voteAverage?.let { if (it > 0) this.score = Score.from10(it) }
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

data class KKDetailResponse(
    @param:JsonProperty("movie") val movie: KKMovie? = null,
    @param:JsonProperty("episodes") val episodes: List<KKServer>? = null
)

data class KKMovie(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("thumb_url") val thumbUrl: String? = null,
    @param:JsonProperty("poster_url") val posterUrl: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("episode_total") val episodeTotal: String? = null,
    @param:JsonProperty("episode_current") val episodeCurrent: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("actor") val actor: List<String>? = null,
    @param:JsonProperty("category") val category: List<KKCategory>? = null,
    @param:JsonProperty("tmdb") val tmdb: TmdbIdInfo? = null
)

data class TmdbIdInfo(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("vote_average") val voteAverage: Double? = null
)

data class KKCategory(@param:JsonProperty("name") val name: String? = null)

data class KKServer(
    @param:JsonProperty("server_name") val serverName: String? = null,
    @param:JsonProperty("server_data") val serverData: List<KKEpisode>? = null
)

data class KKEpisode(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("link_m3u8") val linkM3u8: String? = null
)

// TMDB API Classes
data class TmdbSeasonResponse(@param:JsonProperty("episodes") val episodes: List<TmdbEpisodeDetail>? = null)
data class TmdbEpisodeDetail(
    @param:JsonProperty("episode_number") val episodeNumber: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("still_path") val stillPath: String? = null,
    @param:JsonProperty("air_date") val airDate: String? = null
)


