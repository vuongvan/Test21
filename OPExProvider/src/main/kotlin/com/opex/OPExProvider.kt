package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import android.content.Context
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class OPExProvider : MainAPI() {
    companion object {
        private const val TAG = "OPExProvider"
        lateinit var ctx: Context
        const val PREFS_NAME = "opex_provider_prefs"
        const val PREF_DOMAIN = "domain"
        // Domain mặc định — dùng chung để tránh phải khởi tạo cả OPExProvider() chỉ để đọc mainUrl
        const val DEFAULT_URL = "https://ophim1.com"
        // Timeout (giây) dùng chung cho các network call
        const val NETWORK_TIMEOUT = 15L
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

        // Feature toggles
        const val PREF_USE_TMDB_BACKDROP  = "use_tmdb_backdrop"   // default: true
        const val PREF_USE_TMDB_POSTER    = "use_tmdb_poster"     // default: true
        const val PREF_USE_RECOMMENDATIONS= "use_recommendations"  // default: true
        const val PREF_USE_TMDB_PLOT      = "use_tmdb_plot"       // default: true
        const val PREF_CAST_COUNT         = "cast_count"          // default: 15
        const val PREF_FILTER_TRAILER     = "filter_trailer"      // default: true (lọc trailer) — boolean, không phải count

        private val DEFAULT_PATHS = listOf(
            "v1/api/danh-sach/phim-moi-cap-nhat",
            "v1/api/danh-sach/phim-thuyet-minh",
            "v1/api/danh-sach/phim-long-tieng",
            "v1/api/danh-sach/phim-le",
            "v1/api/danh-sach/hoat-hinh",
            ""
        )
        private val DEFAULT_NAMES = listOf(
            "Mới Cập Nhật", "Phim Thuyết Minh", "Phim Lồng Tiếng",
            "Phim Lẻ", "Phim Hoạt Hình", "Danh Sách 6"
        )

        // Regex dùng chung, compile 1 lần
        internal val EP_NUMBER_REGEX = Regex("""(\d+)""")
        internal val HTML_TAG_REGEX = Regex("<.*?>")

        fun getPreferenceKey(i: Int): String = when (i) {
            1 -> PREF_CATEGORY_1; 2 -> PREF_CATEGORY_2; 3 -> PREF_CATEGORY_3
            4 -> PREF_CATEGORY_4; 5 -> PREF_CATEGORY_5; 6 -> PREF_CATEGORY_6; else -> PREF_CATEGORY_1
        }

        fun getPreferenceNameKey(i: Int): String = when (i) {
            1 -> PREF_CATEGORY_1_NAME; 2 -> PREF_CATEGORY_2_NAME; 3 -> PREF_CATEGORY_3_NAME
            4 -> PREF_CATEGORY_4_NAME; 5 -> PREF_CATEGORY_5_NAME; 6 -> PREF_CATEGORY_6_NAME; else -> PREF_CATEGORY_1_NAME
        }
    }

    override var mainUrl = DEFAULT_URL
    override var name = "OPhim"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? =
        coroutineScope {
            val categories = getCustomCategories(page)
            val filterTrailer = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_FILTER_TRAILER, true)
            val homeItems = categories
                .map { (url, catName) -> async { HomePageList(catName, getListFromUrl(url, filterTrailer)) } }
                .awaitAll()
                .filter { it.list.isNotEmpty() }
            newHomePageResponse(homeItems, hasNext = true)
        }

    private fun getCustomCategories(page: Int): List<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0..5) {
            val path = prefs.getString(getPreferenceKey(i + 1), DEFAULT_PATHS[i]).orEmpty()
            if (path.isEmpty()) continue
            val displayName = prefs.getString(getPreferenceNameKey(i + 1), DEFAULT_NAMES[i]) ?: DEFAULT_NAMES[i]
            val sep = if (path.contains('?')) "&" else "?"
            val finalUrl = if (path.startsWith("http")) "$path${sep}page=$page"
                           else "$mainUrl/$path${sep}page=$page"
            result.add(finalUrl to displayName)
        }
        return result
    }

    private suspend fun getListFromUrl(url: String, filterTrailer: Boolean = true): List<SearchResponse> {
        return try {
            val data = parseJson<OPListResponse>(app.get(url, timeout = NETWORK_TIMEOUT).text)
            val cdn = data.data?.APP_DOMAIN_CDN_IMAGE ?: data.APP_DOMAIN_CDN_IMAGE
            val items = data.data?.items ?: data.items
            items
                ?.filter { !filterTrailer || it.episode_current?.contains("trailer", ignoreCase = true) != true }
                ?.map { item ->
                    val scoreVal = item.tmdb?.vote_average ?: item.imdb?.vote_average ?: 0.0
                    val tvType = when {
                        item.type == "hoathinh" && item.episode_total?.trim() == "1" -> TvType.Movie
                        item.type == "hoathinh" -> TvType.Anime
                        item.episode_total?.trim() == "1" -> TvType.Movie
                        else -> TvType.TvSeries
                    }
                    newAnimeSearchResponse(item.name ?: "", "$mainUrl/v1/api/phim/${item.slug}", tvType) {
                        val currentEp = item.episode_current
                            ?.substringBefore("/")
                            ?.filter { c -> c.isDigit() }
                            ?.toIntOrNull()
                        val langStr = item.lang?.lowercase() ?: ""
                        val isDub = langStr.contains("thuyết minh") || langStr.contains("lồng tiếng")
                        val isSub = langStr.contains("vietsub") || langStr.contains("phụ đề")
                        addDubStatus(isDub, isSub, if (isSub) 0 else currentEp, currentEp)
                        this.posterUrl = "$cdn/uploads/movies/${item.thumb_url}"
                        if (scoreVal > 0) this.score = Score.from10(scoreVal)
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getListFromUrl failed for $url", e)
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? = coroutineScope {
        val slug = url.split("/").last()
        val movieRoot = try {
            parseJson<OPRootResponse>(app.get("$mainUrl/v1/api/phim/$slug", timeout = NETWORK_TIMEOUT).text)
        } catch (e: Exception) {
            Log.e(TAG, "load: failed to fetch detail for $slug", e)
            return@coroutineScope null
        }
        val data = movieRoot.data ?: return@coroutineScope null
        val movie = data.item ?: return@coroutineScope null
        val cdn = data.APP_DOMAIN_CDN_IMAGE
        val prefs = ctx.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val useTmdbBackdrop   = prefs.getBoolean(PREF_USE_TMDB_BACKDROP, true)
        val useTmdbPoster     = prefs.getBoolean(PREF_USE_TMDB_POSTER, true)
        val useRecommendations= prefs.getBoolean(PREF_USE_RECOMMENDATIONS, true)
        val useTmdbPlot       = prefs.getBoolean(PREF_USE_TMDB_PLOT, true)
        val castCount         = prefs.getInt(PREF_CAST_COUNT, 15)

        val isSeries = movie.episode_total?.trim() != "1"
        val tmdbType = movie.tmdb?.type ?: if (isSeries) "tv" else "movie"
        val seasonNumber = movie.tmdb?.season ?: 1

        // Phase 1: build ophim episode map (pure local, không cần network)
        val (subEpsMap, dubEpsMap) = buildEpsMaps(movie.episodes)

        // Phase 1 (song song): resolve tmdbId + fetch recommendations
        // — cả 2 không phụ thuộc nhau, chạy ngay lập tức
        val tmdbIdDeferred = async {
            movie.tmdb?.id?.takeIf { it.isNotEmpty() }
                ?: OPExUtils.findTmdbId(movie.name, movie.origin_name, movie.year, isSeries)
        }
        val recsDeferred = async {
            if (!useRecommendations) return@async emptyList<SearchResponse>()
            val countrySlug = movie.country?.firstOrNull()?.slug ?: ""
            if (countrySlug.isNotEmpty()) {
                val categorySlugs = movie.category?.mapNotNull { it.slug }?.joinToString(",") ?: ""
                val recUrl = "$mainUrl/v1/api/quoc-gia/$countrySlug?limit=20&category=$categorySlugs&sort_field=year&sort_type=desc"
                getListFromUrl(recUrl).take(16)
            } else emptyList<SearchResponse>()
        }

        // Chỉ await tmdbId khi cần để launch 4 TMDB calls — recsDeferred vẫn chạy nền
        val tmdbId = tmdbIdDeferred.await()

        // Phase 2: TMDB calls thực sự song song ngay sau khi có tmdbId
        val detailsDeferred    = async { tmdbId?.let { OPExUtils.fetchTmdbDetails(tmdbType, it) } }
        val seasonDeferred     = async {
            if (tmdbId != null && isSeries) OPExUtils.fetchTmdbSeason(tmdbId, seasonNumber) else null
        }

        // Await tất cả — recsDeferred đã chạy song song từ Phase 1 nên thường đã xong
        val tmdbDetails         = detailsDeferred.await()
        val actorsList          = OPExUtils.parseCast(tmdbDetails?.credits, castCount)
        val tmdbSeason          = seasonDeferred.await()
        val recommendationsList = recsDeferred.await()

        // Merge ophim map với TMDB season data (pure local, không cần thêm network)
        val subEpisodes  = mergeEpisodesFromMap(subEpsMap, tmdbSeason)
        val dubEpisodes  = mergeEpisodesFromMap(dubEpsMap, tmdbSeason)
        // fallback cho phim thường: dùng sub, nếu ko có thì dub
        val episodeList = subEpisodes.ifEmpty { dubEpisodes }

        val posterUrl = if (useTmdbPoster)
            tmdbDetails?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                ?: "$cdn/uploads/movies/${movie.thumb_url}"
        else "$cdn/uploads/movies/${movie.thumb_url}"

        val finalBackdropUrl = if (useTmdbBackdrop)
            tmdbDetails?.images?.backdrops
                ?.mapNotNull { it.filePath?.let { p -> "https://image.tmdb.org/t/p/w1280$p" } }
                ?.randomOrNull()
                ?: tmdbDetails?.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
                ?: "$cdn/uploads/movies/${movie.poster_url}"
        else "$cdn/uploads/movies/${movie.poster_url}"

        // Build meta tags
        val metaTags = buildList {
            if (isSeries) {
                val epCurrent = movie.episode_current ?: ""
                val epTotal = movie.episode_total ?: ""
                add(if (movie.status?.contains("ongoing", ignoreCase = true) == true) "$epCurrent/$epTotal" else epCurrent)
            }
            movie.lang?.split("+")?.forEach { part ->
                val trimmed = part.trim()
                if (!trimmed.contains("Vietsub", ignoreCase = true)) add(trimmed)
            }
            movie.category?.forEach { it.name?.let { n -> add(n) } }
        }

        val finalRating = tmdbDetails?.vote_average ?: movie.tmdb?.vote_average ?: 0.0
        val ophimPlot = (movie.content ?: "").replace(HTML_TAG_REGEX, "").replace("\\n", "\n")
        val plotClean = if (useTmdbPlot && !tmdbDetails?.overview.isNullOrEmpty())
            tmdbDetails!!.overview!! else ophimPlot
        val movieName = movie.name?.split("-", "[")?.first()?.trim() ?: "OPhim"
        val rawStatus = movie.status ?: ""

        val isAnime = movie.type == "hoathinh"
        val showStatus = if (rawStatus.contains("ongoing", ignoreCase = true))
            ShowStatus.Ongoing else ShowStatus.Completed

        return@coroutineScope when {
            // Anime series (hoathinh + nhiều tập)
            isAnime && isSeries -> newAnimeLoadResponse(movieName, url, TvType.Anime) {
                if (subEpisodes.isNotEmpty()) addEpisodes(DubStatus.Subbed, subEpisodes)
                if (dubEpisodes.isNotEmpty()) addEpisodes(DubStatus.Dubbed, dubEpisodes)
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.recommendations = recommendationsList
                this.plot = plotClean
                this.year = movie.year
                this.tags = metaTags
                this.actors = actorsList
                if (finalRating > 0) this.score = Score.from10(finalRating)
                this.showStatus = showStatus
            }
            // Phim lẻ (bao gồm anime movie)
            !isSeries -> newMovieLoadResponse(movieName, url, TvType.Movie, episodeList.firstOrNull()?.data ?: "") {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.recommendations = recommendationsList
                this.plot = plotClean
                this.year = movie.year
                this.tags = metaTags
                this.actors = actorsList
                if (finalRating > 0) this.score = Score.from10(finalRating)
            }
            // Series thường
            else -> newTvSeriesLoadResponse(movieName, url, TvType.TvSeries, episodeList) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.recommendations = recommendationsList
                this.plot = plotClean
                this.year = movie.year
                this.tags = metaTags
                this.actors = actorsList
                if (finalRating > 0) this.score = Score.from10(finalRating)
                this.showStatus = showStatus
            }
        }
    }

    // Phase 1 (sync): chỉ parse local data từ ophim, không có network call
    // Phân loại server thành Sub/Dub dựa theo server_name
    private fun isDubServer(serverName: String): Boolean {
        val lower = serverName.lowercase()
        return lower.contains("thuyết minh") || lower.contains("thuyet minh")
            || lower.contains("lồng tiếng") || lower.contains("long tieng")
            || lower.contains("dub")
    }

    // Phase 1 (sync): build map riêng cho Sub và Dub
    // Map<epNum, Pair<epName, links>>
    private fun buildEpsMaps(ophimServers: List<OPServer>?):
        Pair<MutableMap<Int, Pair<String, MutableList<String>>>,
             MutableMap<Int, Pair<String, MutableList<String>>>> {
        val subMap = mutableMapOf<Int, Pair<String, MutableList<String>>>()
        val dubMap = mutableMapOf<Int, Pair<String, MutableList<String>>>()
        // Đếm ngược cho các tập không parse được số (tránh đè lên nhau tại key mặc định)
        var fallbackNum = -1
        ophimServers?.forEach { server ->
            val sName = server.server_name ?: "Server"
            val targetMap = if (isDubServer(sName)) dubMap else subMap
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: ""
                val link = ep.link_m3u8 ?: return@forEach
                val parsedNum = EP_NUMBER_REGEX.find(epName)?.value?.toIntOrNull()
                if (parsedNum != null) {
                    targetMap.getOrPut(parsedNum) { epName to mutableListOf() }.second.add("$link|$sName")
                } else {
                    // Không tìm được số tập -> gán key riêng biệt, không gộp nhầm các tập khác nhau
                    targetMap[fallbackNum--] = epName to mutableListOf("$link|$sName")
                }
            }
        }
        return subMap to dubMap
    }

    // Phase 2 (sync): merge 1 map với TMDB season data
    private fun mergeEpisodesFromMap(
        epsMap: Map<Int, Pair<String, MutableList<String>>>,
        tmdbSeason: TmdbSeasonResponse?
    ): List<Episode> {
        val tmdbEpsMap = tmdbSeason?.episodes?.associateBy { it.episode_number }
        return epsMap.map { (num, data) ->
            val tmdbEp = tmdbEpsMap?.get(num)
            newEpisode(data.second.joinToString(",")) {
                this.name = tmdbEp?.name
                    ?: if (data.first.contains("Tập", ignoreCase = true)) data.first else "Tập ${data.first}"
                this.episode = num
                this.posterUrl = tmdbEp?.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.description = tmdbEp?.overview
                this.runTime = tmdbEp?.runtime
                val rating = tmdbEp?.vote_average
                if (rating != null && rating > 0) this.score = Score.from10(rating)
                this.addDate(tmdbEp?.air_date)
            }
        }.sortedBy { it.episode }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.split(",").forEach { info ->
            val parts = info.split("|")
            val link = parts.getOrNull(0) ?: return@forEach
            val serverName = parts.getOrNull(1) ?: "OPhim"
            if (link.isNotEmpty()) callback(newExtractorLink(serverName, serverName, link, ExtractorLinkType.M3U8))
        }
        return true
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Encode để tránh gãy URL với query có dấu cách / ký tự đặc biệt / tiếng Việt có dấu
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return getListFromUrl("$mainUrl/v1/api/tim-kiem?keyword=$encoded&limit=30")
    }
}
