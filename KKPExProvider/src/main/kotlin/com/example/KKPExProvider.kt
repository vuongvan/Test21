package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import android.content.Context

class KKPExProvider : MainAPI() {
    companion object {
        // [FIX 3] ctx nullable để tránh crash UninitializedPropertyAccessException
        var ctx: Context? = null

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
        const val DEFAULT_URL = "https://phimapi.com"

        // [FIX 2] Compile Regex một lần duy nhất, tái sử dụng mọi lần gọi load()
        private val EP_NUM_REGEX = Regex("""(\d+)""")
    }

    override var mainUrl = DEFAULT_URL
    override var name = "KK Phim"
    override val hasMainPage = true
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        val response = app.get(url).text

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
        // [FIX 3] Dùng ctx nullable, trả về rỗng nếu chưa init thay vì crash
        val prefs = ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return emptyList()

        val pathKeys     = listOf(PREF_CATEGORY_1, PREF_CATEGORY_2, PREF_CATEGORY_3, PREF_CATEGORY_4, PREF_CATEGORY_5, PREF_CATEGORY_6)
        val nameKeys     = listOf(PREF_CATEGORY_1_NAME, PREF_CATEGORY_2_NAME, PREF_CATEGORY_3_NAME, PREF_CATEGORY_4_NAME, PREF_CATEGORY_5_NAME, PREF_CATEGORY_6_NAME)
        val defaultPaths = listOf("danh-sach/phim-moi-cap-nhat-v3", "v1/api/quoc-gia/trung-quoc", "v1/api/quoc-gia/han-quoc", "v1/api/danh-sach/hoat-hinh", "", "")
        val defaultNames = listOf("Mới Cập Nhật", "Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")

        return buildList {
            for (i in 0 until 6) {
                val categoryPath = prefs.getString(pathKeys[i], defaultPaths[i]).orEmpty()
                if (categoryPath.isEmpty()) continue
                val categoryName = prefs.getString(nameKeys[i], defaultNames[i]) ?: defaultNames[i]
                val baseUrl = if (categoryPath.startsWith("http")) categoryPath else "$mainUrl/$categoryPath"
                val finalUrl = if (baseUrl.contains("?")) "$baseUrl&page=$page" else "$baseUrl?page=$page"
                add(Pair(finalUrl, categoryName))
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? =
        coroutineScope {
            val categories = getCustomCategories(page)
            // Tất cả category fetch chạy song song — 6×700ms → ~700ms
            val homeItems = categories
                .map { (url, catName) -> async { HomePageList(catName, getListFromUrl(url)) } }
                .awaitAll()
                .filter { it.list.isNotEmpty() }
            newHomePageResponse(homeItems, hasNext = true)
        }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/v1/api/tim-kiem?keyword=$query&limit=50"
        return getListFromUrl(url)
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val res = parseJson<KKDetailResponse>(response)
        val movie = res.movie ?: return null

        val rawStatus     = movie.status ?: ""
        val totalEpisodes = movie.episode_total ?: ""

        // [FIX 5] Kết hợp type + episode_total để xác định phim bộ chính xác hơn
        // Tránh trường hợp episode_total = null khiến phim lẻ bị nhận nhầm là phim bộ
        val isSeries = (movie.type == "series" || movie.type == "hoathinh")
                && totalEpisodes != "1"
        val tmdbType = if (isSeries) "tv" else "movie"

        val finalSeasonNum = movie.tmdb?.season ?: 1
        val countrySlug   = movie.country?.firstOrNull()?.slug ?: ""
        val categorySlugs = movie.category?.mapNotNull { it.slug }?.joinToString(",") ?: ""

        val tmdbEpisodesMap = mutableMapOf<Int, TmdbEpisodeDetail>()
        val tmdbActors: List<ActorData>?
        val tmdbDetails: TmdbDetailResponse?
        val tmdbBackdrops: List<String>
        val recommendationsList: List<SearchResponse>

        // =====================================================================
        // [FIX BUG 1] findTmdbId launch NGAY trong coroutineScope cùng với rec
        // Trước: findTmdbId (~400ms) blocking → xong mới launch bundle + rec
        // Sau:   findTmdbId + rec chạy song song ngay từ đầu
        //
        // [FIX BUG 2] Không await() bundle giữa chừng trong scope
        // Trước: bundleDeferred.await() block scope → rec bị chặn dù đã launch
        // Sau:   await() tất cả cùng lúc ở cuối, không cái nào chặn cái nào
        // =====================================================================
        coroutineScope {
            // Resolve tmdbId: nếu web có sẵn thì wrap luôn vào async để không block
            val tmdbIdDeferred = async {
                movie.tmdb?.id?.takeIf { it.isNotEmpty() }
                    ?: KKExUtils.findTmdbId(movie.name, movie.origin_name, movie.year, isSeries)
            }

            // Rec launch song song ngay, không cần chờ tmdbId
            val recDeferred = async {
                if (countrySlug.isNotEmpty()) {
                    val recUrl = "$mainUrl/v1/api/quoc-gia/$countrySlug?limit=16&category=$categorySlugs&sort_field=year&sort_type=desc"
                    getListFromUrl(recUrl)
                } else emptyList()
            }

            // Chờ tmdbId xong rồi mới quyết định launch bundle
            // (bundle phụ thuộc tmdbId nên không thể tránh, nhưng rec đã chạy song song rồi)
            val resolvedTmdbId = tmdbIdDeferred.await()

            if (!resolvedTmdbId.isNullOrEmpty()) {
                // Launch bundle + await() — rec vẫn đang chạy song song trong nền
                if (isSeries) {
                    val bundle = KKExUtils.fetchTmdbSeriesBundle(resolvedTmdbId, finalSeasonNum)
                    tmdbActors    = bundle.cast
                    tmdbDetails   = bundle.details
                    tmdbBackdrops = bundle.backdrops
                    bundle.season?.episodes?.forEach { ep ->
                        ep.episodeNumber?.let { tmdbEpisodesMap[it] = ep }
                    }
                } else {
                    val bundle = KKExUtils.fetchTmdbBundle(tmdbType, resolvedTmdbId)
                    tmdbActors    = bundle.cast
                    tmdbDetails   = bundle.details
                    tmdbBackdrops = bundle.backdrops
                }
            } else {
                tmdbActors    = null
                tmdbDetails   = null
                tmdbBackdrops = emptyList()
            }

            // Await rec — nếu rec đã xong trong lúc bundle chạy thì return ngay, không chờ thêm
            recommendationsList = recDeferred.await()
        }

        // =====================================================================
        // Xây dựng danh sách tập phim
        // =====================================================================
        val episodeMap = mutableMapOf<String, MutableList<String>>()
        res.episodes?.forEach { server ->
            val serverName = server.server_name ?: "HLS"
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: "1"
                episodeMap.getOrPut(epName) { mutableListOf() }
                    .add("${ep.link_m3u8}::$serverName")
            }
        }

        val episodesList = episodeMap.map { (epName, links) ->
            // [FIX 2] Dùng EP_NUM_REGEX đã compile sẵn, không tạo Regex mới mỗi lần
            val epNum = EP_NUM_REGEX.find(epName)?.value?.toIntOrNull()
            val tmdbEp = tmdbEpisodesMap[epNum]

            newEpisode(links.joinToString("|||")) {
                this.name    = tmdbEp?.name ?: epName
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
                val isCompleted    = movie.status == "completed"
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
        // Build response
        // =====================================================================
        return if (isSeries) {
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl           = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.year       = movie.year
                this.plot       = fullPlot
                this.tags       = movieTags
                this.showStatus = if (rawStatus.contains("completed", true) || rawStatus.contains("hoàn thành", true))
                    ShowStatus.Completed else ShowStatus.Ongoing
                this.score      = if (finalRating > 0) Score.from10(finalRating) else null
                this.actors     = finalActors
                this.recommendations = recommendationsList
            }
        } else {
            val movieData = episodesList.firstOrNull()?.data ?: ""
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, movieData) {
                this.posterUrl           = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.year   = movie.year
                this.plot   = fullPlot
                this.tags   = movieTags
                this.score  = if (finalRating > 0) Score.from10(finalRating) else null
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
            val parts      = serverData.split("::")
            val url        = parts.getOrNull(0) ?: return@forEach
            val serverName = parts.getOrNull(1) ?: "HLS"
            callback.invoke(
                newExtractorLink(serverName, serverName, url, type = ExtractorLinkType.M3U8)
            )
        }
        return true
    }
    
}
