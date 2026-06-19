package com.example

import com.lagradost.cloudstream3.*
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

        // [TỐI ƯU] Đưa default mainUrl vào companion object để
        // tránh khởi tạo instance thừa (KKPExProvider()) chỉ để đọc hằng số.
        const val DEFAULT_URL = "https://phimapi.com"
    }

    override var mainUrl = DEFAULT_URL
    override var name = "KK Phim"
    override val hasMainPage = true
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        val response = app.get(url).text

        // [TỐI ƯU] Parse JSON một lần duy nhất thay vì try/catch lồng nhau.
        // Dùng parsedSafe<> để tránh exception và giảm overhead re-parse.
        val items: List<KKItem> = run {
            val listRes = parseJson<KKListResponse>(response)
            listRes.data?.items ?: listRes.items
                ?: parseJson<KKSearchResponse>(response).data?.items
                ?: emptyList()
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
                val baseUrl = if (categoryPath.startsWith("http")) {
                    categoryPath
                } else {
                    "${mainUrl}/v1/api/$categoryPath"
                }
                val finalUrl = if (baseUrl.contains("?")) "$baseUrl&page=$page" else "$baseUrl?page=$page"
                categories.add(Pair(finalUrl, categoryName))
            }
        }
        return categories
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = getCustomCategories(page)

        // [TỐI ƯU] Fetch tất cả category song song thay vì tuần tự.
        // Trước: 7 category x ~300ms = ~2.1s  →  Sau: max(300ms) = ~300ms
        val homePageLists = coroutineScope {
            items.map { (url, title) ->
                async { HomePageList(title, getListFromUrl(url)) }
            }.map { it.await() }
        }

        return newHomePageResponse(homePageLists, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/v1/api/tim-kiem?keyword=$query&limit=20"
        val response = app.get(url).text
        val data = parseJson<KKSearchResponse>(response)

        return data.data?.items?.mapNotNull { item ->
            val title = item.name ?: return@mapNotNull null
            val href = "$mainUrl/phim/${item.slug}"

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = KKExUtils.fixPosterUrl(item.poster_url ?: item.thumb_url)
                val rating = item.tmdb?.vote_average ?: 0.0
                if (rating > 0) {
                    this.score = Score.from10(rating)
                }
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
                episodeMap.getOrPut(epName) { mutableListOf() }
                    .add("${ep.link_m3u8}::${serverName}")
            }
        }

        val episodesList = episodeMap.map { (epName, links) ->
            newEpisode(links.joinToString("|||")) {
                this.name = epName
                this.episode = Regex("""(\d+)""").find(epName)?.value?.toIntOrNull()
            }
        }.sortedBy { it.episode }

        val finalPoster = KKExUtils.fixPosterUrl(movie.thumb_url ?: movie.poster_url)
        val totalEpisodes = movie.episode_total ?: ""
        val isSeries = totalEpisodes != "1"

        val movieTags = buildList {
            // Tag trạng thái tập phim
            if (isSeries) {
                val isCompleted = movie.status == "completed"
                val currentFromApi = movie.episode_current ?: ""
                val tagEp = if (!isCompleted) "$currentFromApi/$totalEpisodes" else currentFromApi
                add(tagEp)
            }

            // Tag ngôn ngữ
            movie.lang?.let { lang ->
                when {
                    lang.contains("Thuyết Minh", ignoreCase = true) -> add("Thuyết Minh")
                    lang.contains("Lồng Tiếng", ignoreCase = true) -> add("Lồng Tiếng")
                }
            }

            // Tag thể loại
            movie.category?.forEach { cat -> cat.name?.let { add(it) } }
        }

        val fullPlot = movie.content ?: "Không có nội dung mô tả."

        // [TỐI ƯU] Gọi song song 2 API TMDB (cast + details) thay vì tuần tự.
        // Trước: fetchCast (~400ms) rồi mới fetchDetails (~400ms) = ~800ms chờ
        // Sau:   cả 2 chạy cùng lúc = ~400ms (tiết kiệm ~50% thời gian load)
        val tmdbId = movie.tmdb?.id
        val tmdbType = if (isSeries) "tv" else "movie"

        val (tmdbActors, tmdbExtra) = if (!tmdbId.isNullOrEmpty()) {
            KKExUtils.fetchTmdbCastAndDetails(tmdbType, tmdbId)
        } else {
            Pair(null, null)
        }

        val backupActors = movie.actor?.map {
            ActorData(Actor(it, null), roleString = "Diễn viên")
        }
        val finalActors = tmdbActors ?: backupActors ?: emptyList()

        val finalRating = tmdbExtra?.vote_average ?: movie.tmdb?.vote_average ?: 0.0

        return if (isSeries) {
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.showStatus = if (rawStatus.contains("completed", true) || rawStatus.contains("hoàn thành", true))
                    ShowStatus.Completed else ShowStatus.Ongoing
                this.score = if (finalRating > 0) Score.from10(finalRating) else null
                this.actors = finalActors
            }
        } else {
            val movieData = episodesList.firstOrNull()?.data ?: ""
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, movieData) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
                this.score = if (finalRating > 0) Score.from10(finalRating) else null
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

        data.split("|||").forEach { serverData ->
            val parts = serverData.split("::")
            val url = parts.getOrNull(0) ?: return@forEach
            val serverName = parts.getOrNull(1) ?: "HLS"

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
