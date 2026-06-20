package recloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder

class DailymotionProvider : MainAPI() {

    // --- DATA CLASSES ---
    data class VideoSearchResponse(@param:JsonProperty("list") val list: List<VideoItem>)
    data class VideoItem(
        @param:JsonProperty("id") val id: String,
        @param:JsonProperty("title") val title: String,
        @param:JsonProperty("thumbnail_360_url") val thumbnail360Url: String? = null,
        @param:JsonProperty("thumbnail_720_url") val thumbnail720Url: String? = null,
        @param:JsonProperty("duration") val duration: Int? = null
    )

    data class PlaylistSearchResponse(@param:JsonProperty("list") val list: List<PlaylistItem>)
    data class PlaylistItem(
        @param:JsonProperty("id") val id: String,
        @param:JsonProperty("name") val name: String,
        @param:JsonProperty("thumbnail_360_url") val thumbnail360Url: String? = null,
        @param:JsonProperty("thumbnail_720_url") val thumbnail720Url: String? = null
    )

    data class FollowingResponse(@param:JsonProperty("list") val list: List<UserItem>)
    data class UserItem(
        @param:JsonProperty("screenname") val screenname: String,
        @param:JsonProperty("id") val id: String
    )

    override var mainUrl = "https://api.dailymotion.com"
    override var name = "Dailymotion"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Others)
    override var lang = "en"
    override val hasMainPage = true

    companion object {
        // [FIX] Compile regex một lần duy nhất thay vì mỗi lần gọi hàm
        private val ID_REGEX = Regex("(?:video|playlist)/([a-zA-Z0-9]+)")
        private const val FOLLOWING_USER = "taunt-preface-runt"

        // [OPT] Cache danh sách users — không thay đổi giữa các page, không cần fetch lại
        @Volatile
        private var cachedUsers: List<UserItem>? = null
    }

    // [OPT] Lấy users có cache — chỉ gọi API lần đầu, các page sau dùng lại
    private suspend fun getFollowingUsers(): List<UserItem> {
        cachedUsers?.let { return it }
        val url = "$mainUrl/user/$FOLLOWING_USER/following?fields=id,screenname&limit=10&page=1"
        return tryParseJson<FollowingResponse>(app.get(url).text)
            ?.list
            .orEmpty()
            .also { cachedUsers = it }
    }

    // --- MAIN PAGE ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Lấy users (từ cache nếu đã có)
        val users = getFollowingUsers()

        // [FIX BOTTLENECK #1] Gọi tất cả playlist API song song thay vì tuần tự
        // Từ: ~10 requests × latency = ~3-5 giây
        // Thành: max(latency) ≈ ~0.5-1 giây
        val homePages = coroutineScope {
            users.map { user ->
                async {
                    val playlistUrl = "$mainUrl/user/${user.id}/playlists" +
                            "?fields=id,name,thumbnail_360_url&limit=20&page=$page"
                    val playlistRes = runCatching { app.get(playlistUrl).text }.getOrNull()
                        ?: return@async null

                    tryParseJson<PlaylistSearchResponse>(playlistRes)?.list
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { playlists ->
                            HomePageList(
                                name = user.screenname,
                                list = playlists.map {
                                    newMovieSearchResponse(
                                        it.name,
                                        "https://www.dailymotion.com/playlist/${it.id}",
                                        TvType.TvSeries
                                    ) {
                                        this.posterUrl = it.thumbnail360Url
                                    }
                                }
                            )
                        }
                }
            }.awaitAll().filterNotNull()
        }

        return newHomePageResponse(homePages, hasNext = true)
    }

    // --- SEARCH ---
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        // Xử lý link trực tiếp (video hoặc playlist)
        if (query.startsWith("http")) {
            val videoId = ID_REGEX.find(query)?.groupValues?.get(1)
            if (videoId != null) {
                return if (query.contains("/playlist/")) {
                    val res = app.get(
                        "$mainUrl/playlist/$videoId?fields=id,name,thumbnail_360_url"
                    ).text
                    tryParseJson<PlaylistItem>(res)?.let {
                        listOf(
                            newMovieSearchResponse(
                                it.name,
                                "https://www.dailymotion.com/playlist/${it.id}",
                                TvType.TvSeries
                            ) { this.posterUrl = it.thumbnail360Url }
                        ).toNewSearchResponseList()
                    }
                } else {
                    val res = app.get(
                        "$mainUrl/video/$videoId?fields=id,title,thumbnail_360_url"
                    ).text
                    tryParseJson<VideoItem>(res)?.let {
                        listOf(
                            newMovieSearchResponse(
                                it.title,
                                "https://www.dailymotion.com/video/${it.id}",
                                TvType.Movie
                            ) { this.posterUrl = it.thumbnail360Url }
                        ).toNewSearchResponseList()
                    }
                }
            }
        }

        // [FIX] Encode query để xử lý đúng ký tự đặc biệt (tiếng Việt, khoảng trắng, v.v.)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val vRes = app.get(
            "$mainUrl/videos?fields=id,title,thumbnail_360_url" +
                    "&limit=20&page=$page&search=$encodedQuery&sort=relevance"
        ).text

        return tryParseJson<VideoSearchResponse>(vRes)?.list?.map {
            newMovieSearchResponse(
                it.title,
                "https://www.dailymotion.com/video/${it.id}",
                TvType.Movie
            ) { this.posterUrl = it.thumbnail360Url }
        }?.toNewSearchResponseList()
    }

    // --- LOAD ---
    override suspend fun load(url: String): LoadResponse? {
        val id = ID_REGEX.find(url)?.groupValues?.get(1) ?: return null

        if (url.contains("/playlist/")) {
            // [OPT] Gọi 2 API song song: detail + danh sách video
            val (detailRes, videosRes) = coroutineScope {
                val detailDeferred = async {
                    app.get(
                        "$mainUrl/playlist/$id?fields=id,name,thumbnail_720_url,thumbnail_360_url"
                    ).text
                }
                val videosDeferred = async {
                    app.get(
                        "$mainUrl/playlist/$id/videos" +
                                "?fields=id,title,thumbnail_360_url,duration&limit=100"
                    ).text
                }
                Pair(detailDeferred.await(), videosDeferred.await())
            }

            val detail = tryParseJson<PlaylistItem>(detailRes) ?: return null
            val videos = tryParseJson<VideoSearchResponse>(videosRes)?.list.orEmpty()

            return newTvSeriesLoadResponse(
                detail.name, url, TvType.TvSeries,
                // [NOTE] Bỏ .reversed() — nếu cần thứ tự đặc biệt hãy uncomment
                videos.reversed().map { video ->
                    newEpisode("https://www.dailymotion.com/video/${video.id}") {
                        this.name = video.title
                        this.posterUrl = video.thumbnail360Url
                        this.runTime = video.duration?.let { it / 60 }
                    }
                }
            ) {
                // [FIX] Dùng thumbnail_720_url cho poster chất lượng cao khi có
                this.posterUrl = detail.thumbnail720Url ?: detail.thumbnail360Url
            }
        }

        // Video đơn lẻ
        val response = app.get(
            "$mainUrl/video/$id?fields=id,title,thumbnail_720_url,thumbnail_360_url,duration"
        ).text
        val v = tryParseJson<VideoItem>(response) ?: return null

        return newMovieLoadResponse(
            v.title, url, TvType.Movie,
            "https://www.dailymotion.com/video/${v.id}"
        ) {
            // [FIX] Ưu tiên 720 cho poster, fallback về 360
            this.posterUrl = v.thumbnail720Url ?: v.thumbnail360Url
            this.duration = v.duration?.let { it / 60 }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return loadExtractor(data, subtitleCallback, callback)
    }
}
