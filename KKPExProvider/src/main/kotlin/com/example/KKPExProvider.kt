package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import android.content.Context

class KKPExProvider : MainAPI() {
    companion object {
        var ctx: Context? = null

        const val PREFS_NAME = "kkpex_provider_prefs"
        const val PREF_DOMAIN = "domain"
        const val DEFAULT_URL = "https://phimapi.com"

        // Toggle TMDB features — cho phép user bật/tắt từng phần
        const val PREF_USE_TMDB_POSTER     = "use_tmdb_poster"
        const val PREF_USE_TMDB_BACKDROP   = "use_tmdb_backdrop"
        const val PREF_USE_TMDB_PLOT       = "use_tmdb_plot"
        const val PREF_USE_RECOMMENDATIONS = "use_recommendations"
        const val PREF_CAST_COUNT          = "cast_count"

        // Category keys — dùng helper function thay vì 12 const riêng lẻ
        fun getPreferenceKey(index: Int) = "category_$index"
        fun getPreferenceNameKey(index: Int) = "category_${index}_name"

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
        val prefs = ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return emptyList()

        val defaultPaths = listOf("danh-sach/phim-moi-cap-nhat-v3", "v1/api/quoc-gia/trung-quoc", "v1/api/quoc-gia/han-quoc", "v1/api/danh-sach/hoat-hinh", "", "")
        val defaultNames = listOf("Mới Cập Nhật", "Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")

        return buildList {
            for (i in 0 until 6) {
                val categoryPath = prefs.getString(getPreferenceKey(i + 1), defaultPaths[i]).orEmpty()
                if (categoryPath.isEmpty()) continue
                val categoryName = prefs.getString(getPreferenceNameKey(i + 1), defaultNames[i]) ?: defaultNames[i]
                val baseUrl = if (categoryPath.startsWith("http")) categoryPath else "$mainUrl/$categoryPath"
                val finalUrl = if (baseUrl.contains("?")) "$baseUrl&page=$page" else "$baseUrl?page=$page"
                add(Pair(finalUrl, categoryName))
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = getCustomCategories(page)
        val homePageLists = coroutineScope {
            items.map { (url, title) ->
                async { HomePageList(title, getListFromUrl(url)) }
            }.awaitAll()
        }
        return newHomePageResponse(homePageLists, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/v1/api/tim-kiem?keyword=$query&limit=50"
        return getListFromUrl(url)
    }

    override suspend fun load(url: String): LoadResponse? {
        val prefs = ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val useTmdbPoster    = prefs?.getBoolean(PREF_USE_TMDB_POSTER, true) ?: true
        val useTmdbBackdrop  = prefs?.getBoolean(PREF_USE_TMDB_BACKDROP, true) ?: true
        val useTmdbPlot      = prefs?.getBoolean(PREF_USE_TMDB_PLOT, true) ?: true
        val useRecommendations = prefs?.getBoolean(PREF_USE_RECOMMENDATIONS, true) ?: true
        val castCount        = prefs?.getInt(PREF_CAST_COUNT, 15)?.coerceIn(1, 30) ?: 15

        val response = app.get(url).text
        val res = parseJson<KKDetailResponse>(response)
        val movie = res.movie ?: return null

        val rawStatus     = movie.status ?: ""
        val totalEpisodes = movie.episode_total ?: ""

        // Xác định loại nội dung
        val isAnime  = movie.type == "hoathinh"
        val isSeries = (movie.type == "series" || isAnime) && totalEpisodes != "1"
        val tvType   = when {
            isAnime  -> TvType.Anime
            isSeries -> TvType.TvSeries
            else     -> TvType.Movie
        }
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

            // Rec launch song song ngay, không cần chờ tmdbId (trừ khi user tắt tính năng)
            val recDeferred = async {
                if (useRecommendations && countrySlug.isNotEmpty()) {
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
                    val bundle = KKExUtils.fetchTmdbSeriesBundle(resolvedTmdbId, finalSeasonNum, castCount)
                    tmdbActors    = bundle.cast
                    tmdbDetails   = bundle.details
                    tmdbBackdrops = bundle.backdrops
                    bundle.season?.episodes?.forEach { ep ->
                        ep.episodeNumber?.let { tmdbEpisodesMap[it] = ep }
                    }
                } else {
                    val bundle = KKExUtils.fetchTmdbBundle(tmdbType, resolvedTmdbId, castCount)
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
        // Xây dựng danh sách tập phim — tách Sub/Dub theo server_name
        // Sub (Vietsub) = mặc định, Dub (Thuyết Minh / Lồng Tiếng) = slot riêng
        // =====================================================================
        val subEpMap   = mutableMapOf<Int, MutableList<String>>()
        val dubEpMap   = mutableMapOf<Int, MutableList<String>>()
        val subEpNames = mutableMapOf<Int, String>()
        // Fix 3: thu thập link_sub từ KKEpisode để truyền vào subtitleCallback
        val subLinkMap = mutableMapOf<Int, String>()

        res.episodes?.forEach { server ->
            val serverName  = server.server_name ?: "HLS"
            val isDubServer = serverName.contains("Thuyết Minh", ignoreCase = true)
                    || serverName.contains("Lồng Tiếng", ignoreCase = true)

            server.server_data?.forEach { ep ->
                val epName = ep.name ?: "1"
                val epNum  = EP_NUM_REGEX.find(epName)?.value?.toIntOrNull() ?: return@forEach
                val link   = ep.link_m3u8 ?: return@forEach

                if (isDubServer) {
                    dubEpMap.getOrPut(epNum) { mutableListOf() }.add("$link::$serverName")
                } else {
                    subEpMap.getOrPut(epNum) { mutableListOf() }.add("$link::$serverName")
                    subEpNames[epNum] = epName
                    ep.link_sub?.takeIf { it.isNotEmpty() }?.let { subLinkMap[epNum] = it }
                }
            }
        }

        // Fallback: nếu không có sub server (phim 1 server), gộp dub vào sub
        if (subEpMap.isEmpty() && dubEpMap.isNotEmpty()) {
            dubEpMap.forEach { (k, v) -> subEpMap[k] = v }
            dubEpMap.clear()
        }

        val hasDub = dubEpMap.isNotEmpty()

        // Fix 2: 1 hàm buildEpisodeList dùng chung — không build lại nhiều lần
        // Với series có dub: truyền merged links (sub + dub) luôn từ đây
        // Với anime có dub: truyền subEpMap hoặc dubEpMap riêng
        fun buildEpisodeList(epMap: Map<Int, List<String>>): List<Episode> =
            epMap.map { (epNum, links) ->
                val tmdbEp  = tmdbEpisodesMap[epNum]
                val epName  = subEpNames[epNum] ?: "Tập $epNum"
                // Fix 3: gắn link_sub vào cuối data string với prefix SUB::
                val subLink = subLinkMap[epNum]?.let { "SUB::$it" }
                val allData = (links + listOfNotNull(subLink)).joinToString("|||")
                newEpisode(allData) {
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

        // Merge sub+dub map tại đây 1 lần — tránh build lại trong case isSeries
        val mergedEpMap: Map<Int, List<String>> = if (hasDub) {
            val allNums = (subEpMap.keys + dubEpMap.keys).toSortedSet()
            allNums.associateWith { epNum ->
                (subEpMap[epNum] ?: emptyList()) + (dubEpMap[epNum] ?: emptyList())
            }
        } else subEpMap

        val subEpisodesList    = buildEpisodeList(subEpMap)
        val dubEpisodesList    = buildEpisodeList(dubEpMap)
        val mergedEpisodesList = if (hasDub) buildEpisodeList(mergedEpMap) else subEpisodesList

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

        val fullPlot = if (useTmdbPlot) {
            tmdbDetails?.overview?.takeIf { it.isNotEmpty() } ?: movie.content ?: "Không có nội dung mô tả."
        } else {
            movie.content ?: "Không có nội dung mô tả."
        }

        val finalActors = tmdbActors
            ?: movie.actor?.map { ActorData(Actor(it, null), roleString = "Diễn viên") }
            ?: emptyList()

        val finalRating = tmdbDetails?.vote_average ?: movie.tmdb?.vote_average ?: 0.0

        val posterUrl = if (useTmdbPoster) {
            tmdbDetails?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" } ?: movie.poster_url
        } else {
            movie.poster_url
        }

        val finalBackdropUrl = if (useTmdbBackdrop) {
            if (tmdbBackdrops.isNotEmpty()) {
                tmdbBackdrops.random()
            } else {
                tmdbDetails?.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" } ?: movie.thumb_url
            }
        } else {
            movie.thumb_url
        }

        // isDub/isSub đã được xác định chính xác qua việc tách server bên trên
        // hasDub = true nếu API trả về server Thuyết Minh / Lồng Tiếng riêng biệt

        // =====================================================================
        // Build response
        // =====================================================================
        return when {
            isAnime && isSeries -> {
                newAnimeLoadResponse(movie.name ?: "", url, TvType.Anime) {
                    // Sub luôn là mặc định (ưu tiên), Dub thêm nếu có
                    addEpisodes(DubStatus.Subbed, subEpisodesList)
                    if (hasDub) addEpisodes(DubStatus.Dubbed, dubEpisodesList)
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
            }
            isAnime && !isSeries -> {
                val movieData = subEpisodesList.firstOrNull()?.data ?: ""
                newAnimeLoadResponse(movie.name ?: "", url, TvType.AnimeMovie) {
                    addEpisodes(DubStatus.Subbed, subEpisodesList)
                    if (hasDub) addEpisodes(DubStatus.Dubbed, dubEpisodesList)
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
            isSeries -> {
                // mergedEpisodesList đã được build 1 lần ở trên (sub trước, dub sau)
                newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, mergedEpisodesList) {
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
            }
            else -> {
                // Movie: gộp sub + dub links vào data string, sub trước
                val allLinks = (subEpMap.values.flatten() + dubEpMap.values.flatten())
                val movieData = allLinks.joinToString("|||").ifEmpty {
                    subEpisodesList.firstOrNull()?.data ?: ""
                }
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
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false
        data.split("|||").forEach { serverData ->
            // Fix 3: tách subtitle link_sub (prefix SUB::) ra khỏi video links
            if (serverData.startsWith("SUB::")) {
                val subUrl = serverData.removePrefix("SUB::")
                subtitleCallback(SubtitleFile("Vietsub", subUrl))
                return@forEach
            }
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
