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

    override suspend fun load(url: String): LoadResponse? = coroutineScope {
        val prefs = ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val useTmdbPoster       = prefs?.getBoolean(PREF_USE_TMDB_POSTER, true) ?: true
        val useTmdbBackdrop     = prefs?.getBoolean(PREF_USE_TMDB_BACKDROP, true) ?: true
        val useTmdbPlot         = prefs?.getBoolean(PREF_USE_TMDB_PLOT, true) ?: true
        val useRecommendations  = prefs?.getBoolean(PREF_USE_RECOMMENDATIONS, true) ?: true
        val castCount           = prefs?.getInt(PREF_CAST_COUNT, 15)?.coerceIn(1, 30) ?: 15

        val response = app.get(url).text
        val res = parseJson<KKDetailResponse>(response)
        val movie = res.movie ?: return@coroutineScope null

        val rawStatus     = movie.status ?: ""
        val totalEpisodes = movie.episode_total ?: ""
        val isAnime  = movie.type == "hoathinh"
        val isSeries = (movie.type == "series" || isAnime) && totalEpisodes != "1"
        val tmdbType = if (isSeries) "tv" else "movie"
        val finalSeasonNum = movie.tmdb?.season ?: 1

        // ---------------------------------------------------------------
        // Phase 1 (sync, local): tách server thành 3 nhóm audio riêng biệt
        // ---------------------------------------------------------------
        val epsByAudio       = buildEpsMaps(res.episodes)
        val subEpsMap        = epsByAudio.getValue(AudioType.SUB)
        val thuyetMinhEpsMap = epsByAudio.getValue(AudioType.THUYET_MINH)
        val longTiengEpsMap  = epsByAudio.getValue(AudioType.LONG_TIENG)

        val countrySlug   = movie.country?.firstOrNull()?.slug ?: ""
        val categorySlugs = movie.category?.mapNotNull { it.slug }?.joinToString(",") ?: ""

        // ---------------------------------------------------------------
        // Phase 2 (song song): resolve tmdbId + fetch recommendations
        // — cả 2 không phụ thuộc nhau, chạy song song ngay lập tức
        // ---------------------------------------------------------------
        val tmdbIdDeferred = async {
            movie.tmdb?.id?.takeIf { it.isNotEmpty() }
                ?: KKExUtils.findTmdbId(movie.name, movie.origin_name, movie.year, isSeries)
        }
        val recsDeferred = async {
            if (!useRecommendations || countrySlug.isEmpty()) return@async emptyList<SearchResponse>()
            val recUrl = "$mainUrl/v1/api/quoc-gia/$countrySlug?limit=20&category=$categorySlugs&sort_field=year&sort_type=desc"
            getListFromUrl(recUrl).take(16)
        }

        // Chỉ await tmdbId để launch tiếp các TMDB call — recsDeferred vẫn chạy nền song song
        val tmdbId = tmdbIdDeferred.await()

        // ---------------------------------------------------------------
        // Phase 3 (song song): cast + details + backdrops + season
        // ---------------------------------------------------------------
        val tmdbActors: List<ActorData>?
        val tmdbDetails: TmdbDetailResponse?
        val tmdbBackdrops: List<String>
        var tmdbSeason: TmdbSeasonResponse? = null

        if (!tmdbId.isNullOrEmpty()) {
            if (isSeries) {
                val bundle = KKExUtils.fetchTmdbSeriesBundle(tmdbId, finalSeasonNum, castCount)
                tmdbActors    = bundle.cast
                tmdbDetails   = bundle.details
                tmdbBackdrops = bundle.backdrops
                tmdbSeason    = bundle.season
            } else {
                val bundle = KKExUtils.fetchTmdbBundle(tmdbType, tmdbId, castCount)
                tmdbActors    = bundle.cast
                tmdbDetails   = bundle.details
                tmdbBackdrops = bundle.backdrops
            }
        } else {
            tmdbActors    = null
            tmdbDetails   = null
            tmdbBackdrops = emptyList()
        }

        // recsDeferred đã chạy song song từ Phase 2 nên thường đã xong lúc này
        val recommendationsList = recsDeferred.await()

        // ---------------------------------------------------------------
        // Merge ophim-style map với TMDB season data (local, không cần thêm network)
        // Dùng CƠ CHẾ Sub/Dub GỐC của Cloudstream (newAnimeLoadResponse + addEpisodes)
        // cho MỌI phim bộ — không chỉ Anime — để có tab Subbed/Dubbed đúng nghĩa.
        // ---------------------------------------------------------------
        val subEpisodes = mergeEpisodesFromMap(subEpsMap, tmdbSeason)
        // Gộp Thuyết Minh + Lồng Tiếng chung 1 tab Dubbed, phân biệt qua tên server khi chọn nguồn
        val dubEpisodes = mergeEpisodesFromMap(mergeAudioMaps(thuyetMinhEpsMap, longTiengEpsMap), tmdbSeason)
        val hasDub = dubEpisodes.isNotEmpty()

        // Phim lẻ: gộp toàn bộ Vietsub + Thuyết Minh + Lồng Tiếng làm data cho 1 "tập" duy nhất
        val movieData = mergeEpisodesFromMap(
            mergeAudioMaps(subEpsMap, thuyetMinhEpsMap, longTiengEpsMap), tmdbSeason
        ).firstOrNull()?.data ?: ""

        // ---------------------------------------------------------------
        // Metadata
        // ---------------------------------------------------------------
        val movieTags = buildList {
            if (isSeries) {
                val isCompleted    = movie.status == "completed"
                val currentFromApi = movie.episode_current ?: ""
                add(if (!isCompleted) "$currentFromApi/$totalEpisodes" else currentFromApi)
            }
            movie.lang?.split("+")?.forEach { part ->
                val trimmed = part.trim()
                if (trimmed.isNotEmpty() && !trimmed.contains("Vietsub", ignoreCase = true)) add(trimmed)
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
        } else movie.poster_url

        val finalBackdropUrl = if (useTmdbBackdrop) {
            tmdbBackdrops.randomOrNull()
                ?: tmdbDetails?.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
                ?: movie.thumb_url
        } else movie.thumb_url

        val showStatus = if (rawStatus.contains("completed", true) || rawStatus.contains("hoàn thành", true))
            ShowStatus.Completed else ShowStatus.Ongoing

        // ---------------------------------------------------------------
        // Build response
        // ---------------------------------------------------------------
        when {
            !isSeries -> newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, movieData) {
                this.posterUrl           = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.year   = movie.year
                this.plot   = fullPlot
                this.tags   = movieTags
                this.score  = if (finalRating > 0) Score.from10(finalRating) else null
                this.actors = finalActors
                this.recommendations = recommendationsList
            }
            // Phim bộ (Anime lẫn thường) — dùng chung builder để có tab Subbed/Dubbed gốc
            // của Cloudstream, không phụ thuộc TvType.
            else -> newAnimeLoadResponse(movie.name ?: "", url, if (isAnime) TvType.Anime else TvType.TvSeries) {
                if (subEpisodes.isNotEmpty()) addEpisodes(DubStatus.Subbed, subEpisodes)
                if (hasDub) addEpisodes(DubStatus.Dubbed, dubEpisodes)
                this.posterUrl           = posterUrl
                this.backgroundPosterUrl = finalBackdropUrl
                this.year       = movie.year
                this.plot       = fullPlot
                this.tags       = movieTags
                this.showStatus = showStatus
                this.score      = if (finalRating > 0) Score.from10(finalRating) else null
                this.actors     = finalActors
                this.recommendations = recommendationsList
            }
        }
    }

    // =====================================================================
    // Phase 1 (sync): phân loại server thành 3 nhóm audio riêng biệt
    // dựa theo server_name. "dub"/"lồng tiếng" -> LONG_TIENG,
    // "thuyết minh" -> THUYET_MINH, còn lại mặc định -> SUB (Vietsub)
    // =====================================================================
    private enum class AudioType { SUB, THUYET_MINH, LONG_TIENG }

    private fun audioTypeOf(serverName: String): AudioType {
        val lower = serverName.lowercase()
        return when {
            lower.contains("lồng tiếng") || lower.contains("long tieng") || lower.contains("dub") -> AudioType.LONG_TIENG
            lower.contains("thuyết minh") || lower.contains("thuyet minh") -> AudioType.THUYET_MINH
            else -> AudioType.SUB
        }
    }

    // Map<epNum, EpData(epName, links, subUrl)> cho từng loại audio
    // subUrl (link_sub) chỉ có ý nghĩa với server Vietsub, giữ riêng để loadLinks
    // có thể tự động gọi subtitleCallback mà không cần user tìm thủ công.
    private data class EpData(val name: String, val links: MutableList<String>, var subUrl: String? = null)

    private fun buildEpsMaps(servers: List<KKServer>?): Map<AudioType, MutableMap<Int, EpData>> {
        val result: Map<AudioType, MutableMap<Int, EpData>> =
            AudioType.values().associateWith { mutableMapOf<Int, EpData>() }
        var fallbackNum = -1
        servers?.forEach { server ->
            val sName = server.server_name ?: "HLS"
            val audioType = audioTypeOf(sName)
            val targetMap = result.getValue(audioType)
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: ""
                val link = ep.link_m3u8 ?: return@forEach
                val parsedNum = EP_NUM_REGEX.find(epName)?.value?.toIntOrNull()
                val key = parsedNum ?: fallbackNum--
                val entry = targetMap.getOrPut(key) { EpData(epName, mutableListOf()) }
                entry.links.add("$link::$sName")
                // link_sub chỉ áp dụng cho server Vietsub (SUB)
                if (audioType == AudioType.SUB) {
                    ep.link_sub?.takeIf { it.isNotEmpty() }?.let { entry.subUrl = it }
                }
            }
        }
        return result
    }

    // Gộp nhiều map audio theo số tập thành 1 map — dùng cho phim lẻ hoặc gộp Dub chung
    private fun mergeAudioMaps(vararg maps: Map<Int, EpData>): Map<Int, EpData> {
        val combined = mutableMapOf<Int, EpData>()
        maps.forEach { map ->
            map.forEach { (num, data) ->
                val entry = combined.getOrPut(num) { EpData(data.name, mutableListOf()) }
                entry.links.addAll(data.links)
                if (entry.subUrl == null) entry.subUrl = data.subUrl
            }
        }
        return combined
    }

    // Phase (sync): merge 1 map với TMDB season data để lấy tên/ảnh/mô tả tập chính xác hơn.
    // Nếu có link_sub, gắn thêm entry "SUB::url" vào cuối data string —
    // loadLinks() sẽ nhận ra prefix này và tự gọi subtitleCallback.
    private fun mergeEpisodesFromMap(
        epsMap: Map<Int, EpData>,
        tmdbSeason: TmdbSeasonResponse?
    ): List<Episode> {
        val tmdbEpsMap = tmdbSeason?.episodes?.associateBy { it.episodeNumber }
        return epsMap.map { (num, data) ->
            val tmdbEp = tmdbEpsMap?.get(num)
            val subEntry = data.subUrl?.let { "SUB::$it" }
            val allData = (data.links + listOfNotNull(subEntry)).joinToString("|||")
            newEpisode(allData) {
                this.name = tmdbEp?.name
                    ?: if (data.name.contains("Tập", ignoreCase = true)) data.name else "Tập ${data.name}"
                this.episode = num
                tmdbEp?.stillPath?.let { this.posterUrl = "https://image.tmdb.org/t/p/w300$it" }
                this.description = tmdbEp?.overview
                val rating = tmdbEp?.voteAverage
                if (rating != null && rating > 0) this.score = Score.from10(rating)
                this.runTime = tmdbEp?.runTime
                this.addDate(tmdbEp?.airDate)
            }
        }.sortedBy { it.episode }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false
        data.split("|||").forEach { serverData ->
            if (serverData.startsWith("SUB::")) {
                val subUrl = serverData.removePrefix("SUB::")
                subtitleCallback(newSubtitleFile("Vietsub", subUrl))
                return@forEach
            }
            val parts      = serverData.split("::")
            val linkUrl    = parts.getOrNull(0) ?: return@forEach
            val serverName = parts.getOrNull(1) ?: "HLS"
            callback.invoke(
                newExtractorLink(serverName, serverName, linkUrl, type = ExtractorLinkType.M3U8)
            )
        }
        return true
    }
}
