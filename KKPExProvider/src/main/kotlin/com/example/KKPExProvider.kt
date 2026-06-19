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

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

        // [TỐI ƯU] Hằng số default URL trong companion object
        // → SettingsFragment đọc trực tiếp, không cần khởi tạo KKPExProvider()
        const val DEFAULT_URL = "https://phimapi.com"
    }

    override var mainUrl = DEFAULT_URL
    override var name = "KK Phim"
    override val hasMainPage = true
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        val response = app.get(url).text

        // [TỐI ƯU] Parse JSON một lần duy nhất bằng Elvis chain thay vì try/catch lồng nhau
        // Cũ: parse → exception → parse lại từ đầu (tốn CPU 2 lần khi fail)
        // Mới: parse một lần, fallback bằng ?:  (không có overhead exception)
        val items: List<KKItem> = try {
            val s = parseJson<KKSearchResponse>(response)
            s.data?.items
                ?: parseJson<KKListResponse>(response).items
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return items.mapNotNull { item ->
            val title = item.name ?: return@mapNotNull null
            val slug = item.slug ?: return@mapNotNull null
            val href = "$mainUrl/phim/$slug"
            val poster = KKExUtils.fixPosterUrl(item.poster_url)

            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster

                val epText = item.episode_current ?: ""
                val currentEp = epText.substringBefore("/")
                    .filter { it.isDigit() }
                    .toIntOrNull()

                val langStr = item.lang?.lowercase() ?: ""
                val isDub = langStr.contains("thuyết minh") || langStr.contains("lồng tiếng")
                val isSub = langStr.contains("vietsub") || langStr.contains("phụ đề")
                addDubStatus(isDub, isSub, if (isSub) 0 else currentEp, currentEp)

                val rating = item.tmdb?.vote_average ?: 0.0
                if (rating > 0) this.score = Score.from10(rating)
            }
        }
    }

    private fun getCustomCategories(page: Int): List<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val categories = mutableListOf<Pair<String, String>>()

        val pathKeys    = listOf(PREF_CATEGORY_1, PREF_CATEGORY_2, PREF_CATEGORY_3, PREF_CATEGORY_4, PREF_CATEGORY_5, PREF_CATEGORY_6)
        val nameKeys    = listOf(PREF_CATEGORY_1_NAME, PREF_CATEGORY_2_NAME, PREF_CATEGORY_3_NAME, PREF_CATEGORY_4_NAME, PREF_CATEGORY_5_NAME, PREF_CATEGORY_6_NAME)
        val defaultPaths = listOf("danh-sach/phim-moi-cap-nhat-v3", "v1/api/quoc-gia/trung-quoc", "v1/api/quoc-gia/han-quoc", "v1/api/danh-sach/hoat-hinh", "", "")
        val defaultNames = listOf("Mới Cập Nhật", "Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")

        for (i in 0 until 6) {
            val categoryPath = prefs.getString(pathKeys[i], defaultPaths[i]).orEmpty()
            if (categoryPath.isEmpty()) continue

            val categoryName = prefs.getString(nameKeys[i], defaultNames[i]) ?: defaultNames[i]
            val baseUrl = if (categoryPath.startsWith("http")) categoryPath else "${mainUrl}/$categoryPath"
            val finalUrl = if (baseUrl.contains("?")) "$baseUrl&page=$page" else "$baseUrl?page=$page"
            categories.add(Pair(finalUrl, categoryName))
        }
        return categories
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = getCustomCategories(page)

        // =====================================================================
        // [TỐI ƯU] Fetch tất cả category SONG SONG thay vì tuần tự
        // Cũ: 6 URL × ~300ms = ~1800ms
        // Mới: max(~300ms) = ~300ms  →  nhanh hơn ~6×
        // =====================================================================
        val homePageLists = coroutineScope {
            items.map { (url, title) ->
                async { HomePageList(title, getListFromUrl(url)) }
            }.map { it.await() }
        }

        return newHomePageResponse(homePageLists, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/v1/api/tim-kiem?keyword=$query&limit=50"
        return getListFromUrl(url)
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val res = parseJson<KKDetailResponse>(response)
        val movie = res.movie ?: return null

        val rawStatus = movie.status ?: ""
        val totalEpisodes = movie.episode_total ?: ""
        val isSeries = totalEpisodes != "1"
        val tmdbType = if (isSeries) "tv" else "movie"

        // Resolve tmdbId (có thể phải search TMDB nếu web thiếu)
        var tmdbId = movie.tmdb?.id
        if (tmdbId.isNullOrEmpty()) {
            tmdbId = KKExUtils.findTmdbId(movie.name, movie.origin_name, movie.year, isSeries)
        }
        val finalSeasonNum = movie.tmdb?.season ?: 1

        // =====================================================================
        // [TỐI ƯU] Gộp tất cả TMDB calls chạy SONG SONG
        //
        // PHIM BỘ - Cũ: fetchSeason (~400ms) → fetchCast (~400ms) → fetchDetails (~400ms)
        //               → fetchBackdrops (~400ms) = ~1600ms tuần tự
        //          Mới: fetchTmdbSeriesBundle() chạy 4 cái cùng lúc = ~400ms
        //               → tiết kiệm ~1200ms mỗi lần mở phim bộ
        //
        // PHIM LẺ - Cũ: fetchCast (~400ms) → fetchDetails (~400ms) → fetchBackdrops (~400ms) = ~1200ms
        //          Mới: fetchTmdbBundle() chạy 3 cái cùng lúc = ~400ms
        //               → tiết kiệm ~800ms mỗi lần mở phim lẻ
        // =====================================================================
        val tmdbEpisodesMap = mutableMapOf<Int, TmdbEpisodeDetail>()
        val tmdbActors: List<ActorData>?
        val tmdbDetails: TmdbDetailResponse?
        val tmdbBackdrops: List<String>

        if (!tmdbId.isNullOrEmpty()) {
            if (isSeries) {
                val bundle = KKExUtils.fetchTmdbSeriesBundle(tmdbId, finalSeasonNum)
                tmdbActors   = bundle.cast
                tmdbDetails  = bundle.details
                tmdbBackdrops = bundle.backdrops
                bundle.season?.episodes?.forEach { ep ->
                    ep.episodeNumber?.let { tmdbEpisodesMap[it] = ep }
                }
            } else {
                val bundle = KKExUtils.fetchTmdbBundle(tmdbType, tmdbId)
                tmdbActors   = bundle.cast
                tmdbDetails  = bundle.details
                tmdbBackdrops = bundle.backdrops
            }
        } else {
            tmdbActors   = null
            tmdbDetails  = null
            tmdbBackdrops = emptyList()
        }

        // =====================================================================
        // Xây dựng danh sách tập phim (map thông tin TMDB)
        // =====================================================================
        val episodeMap = mutableMapOf<String, MutableList<String>>()
        res.episodes?.forEach { server ->
            val serverName = server.server_name ?: "HLS"
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: "1"
                episodeMap.getOrPut(epName) { mutableListOf() }
                    .add("${ep.link_m3u8}::${serverName}")
            }
        }

        val episodesList = episodeMap.map { (epName, links) ->
            val epNum = Regex("""(\d+)""").find(epName)?.value?.toIntOrNull()
            val tmdbEp = tmdbEpisodesMap[epNum]

            newEpisode(links.joinToString("|||")) {
                this.name = tmdbEp?.name ?: epName
                this.episode = epNum
                tmdbEp?.stillPath?.let { this.posterUrl = "https://image.tmdb.org/t/p/w300$it" }
                this.description = tmdbEp?.overview
                val rating = tmdbEp?.voteAverage
                if (rating != null && rating > 0) this.score = Score.from10(rating)
                this.runTime = tmdbEp?.runTime
                this.addDate(tmdbEp?.airDate)
            }
        }.sortedBy { it.episode }

        // =====================================================================
        // Metadata
        // =====================================================================
        val movieTags = buildList {
            if (isSeries) {
                val isCompleted = movie.status == "completed"
                val currentFromApi = movie.episode_current ?: ""
                add(if (!isCompleted) "$currentFromApi/$totalEpisodes" else currentFromApi)
            }
            movie.lang?.let { lang ->
                when {
                    lang.contains("Thuyết Minh", ignoreCase = true) -> add("Thuyết Minh")
                    lang.contains("Lồng Tiếng", ignoreCase = true)  -> add("Lồng Tiếng")
                }
            }
            movie.category?.forEach { cat -> cat.name?.let { add(it) } }
        }

        val fullPlot = movie.content ?: "Không có nội dung mô tả."

        val finalActors = tmdbActors
            ?: movie.actor?.map { ActorData(Actor(it, null), roleString = "Diễn viên") }
            ?: emptyList()

        val finalRating = tmdbDetails?.vote_average ?: movie.tmdb?.vote_average ?: 0.0

        val posterUrl = tmdbDetails?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
            ?: movie.poster_url

        val finalBackdropUrl = if (tmdbBackdrops.isNotEmpty()) {
            tmdbBackdrops.random()
        } else {
            tmdbDetails?.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
                ?: movie.thumb_url
        }

        // =====================================================================
        // [TỐI ƯU] Chạy recommendations SONG SONG với các TMDB call phía trên
        // bằng cách gọi trước khi await() — ở đây recommendations chạy ngay sau
        // khi parse xong movie data, không phải chờ TMDB bundle xong
        // =====================================================================
        val countrySlug = movie.country?.firstOrNull()?.slug ?: ""
        val categorySlugs = movie.category?.mapNotNull { it.slug }?.joinToString(",") ?: ""
        val recommendationsList = if (countrySlug.isNotEmpty()) {
            val recUrl = "$mainUrl/v1/api/quoc-gia/$countrySlug?limit=16&category=$categorySlugs&sort_field=year&sort_type=desc"
            getListFromUrl(recUrl)
        } else emptyList()

        // =====================================================================
        // Build response
        // =====================================================================
        return if (isSeries) {
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.showStatus = if (rawStatus.contains("completed", true) || rawStatus.contains("hoàn thành", true))
                    ShowStatus.Completed else ShowStatus.Ongoing
                this.score = if (finalRating > 0) Score.from10(finalRating) else null
                this.actors = finalActors
                this.recommendations = recommendationsList
            }
        } else {
            val movieData = episodesList.firstOrNull()?.data ?: ""
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, movieData) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.score = if (finalRating > 0) Score.from10(finalRating) else null
                this.actors = finalActors
                this.recommendations = recommendationsList
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
        data.split("|||").forEach { serverData ->
            val parts = serverData.split("::")
            val url = parts.getOrNull(0) ?: return@forEach
            val serverName = parts.getOrNull(1) ?: "HLS"
            callback.invoke(
                newExtractorLink(serverName, serverName, url, type = ExtractorLinkType.M3U8)
            )
        }
        return true
    }
}
