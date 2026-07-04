package recloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
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

    // --- SETTINGS ---
    // Hiển thị ô nhập liệu trong phần Settings của plugin trên CloudStream
    override val settingsJson: String = """
        [
          {
            "inputType": "EditText",
            "key":       "following_user",
            "title":     "Dailymotion Username",
            "hint":      "Nhập username Dailymotion (vd: taunt-preface-runt)",
            "default":   "$DEFAULT_FOLLOWING_USER"
          }
        ]
    """.trimIndent()

    companion object {
        // [OPT] Compile regex 1 lần duy nhất — tránh tạo lại mỗi lần gọi hàm
        private val ID_REGEX = Regex("(?:video|playlist)/([a-zA-Z0-9]+)")
        private const val DEFAULT_FOLLOWING_USER = "taunt-preface-runt"
        private const val PREF_KEY = "following_user"

        // [OPT] Cache users — reset khi username thay đổi
        @Volatile
        private var cachedUsers: List<UserItem>? = null

        @Volatile
        private var cachedForUser: String? = null
    }

    // Đọc username từ setting, fallback về default nếu chưa set hoặc để trống
    private fun getFollowingUser(): String =
        Plugin.getSharedPreferences()
            ?.getString(PREF_KEY, DEFAULT_FOLLOWING_USER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FOLLOWING_USER

    // Lấy following users — cache tự động reset nếu user đổi setting
    private suspend fun getFollowingUsers(): List<UserItem> {
        val currentUser = getFollowingUser()
        // Reset cache nếu username thay đổi
        if (cachedForUser != currentUser) {
            cachedUsers = null
            cachedForUser = currentUser
        }
        cachedUsers?.let { return it }
        val url = "$mainUrl/user/$currentUser/following?fields=id,screenname&limit=10&page=1"
        return tryParseJson<FollowingResponse>(app.get(url).text)
            ?.list
            .orEmpty()
            .also { cachedUsers = it }
    }

    // --- MAIN PAGE ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val users = getFollowingUsers()
        val homePages = mutableListOf<HomePageList>()

        // Sequential — nhưng đã tiết kiệm 1 API call nhờ cache users
        // Để parallel thực sự, xem hướng dẫn build.gradle bên dưới
        for (user in users) {
            val playlistUrl = "$mainUrl/user/${user.id}/playlists" +
                    "?fields=id,name,thumbnail_360_url&limit=20&page=$page"
            val playlistRes = runCatching { app.get(playlistUrl).text }.getOrNull() ?: continue

            tryParseJson<PlaylistSearchResponse>(playlistRes)?.list
                ?.takeIf { it.isNotEmpty() }
                ?.let { playlists ->
                    homePages.add(HomePageList(
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
                    ))
                }
        }

        return newHomePageResponse(homePages, hasNext = true)
    }

    // --- SEARCH ---
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        // Xử lý link trực tiếp paste vào ô search
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

        // [FIX] URLEncoder để search tiếng Việt / ký tự đặc biệt không bị lỗi
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
            // Hai request này vẫn tuần tự, nhưng đã tối ưu fields chính xác
            val detailRes = app.get(
                "$mainUrl/playlist/$id?fields=id,name,thumbnail_720_url,thumbnail_360_url"
            ).text
            val detail = tryParseJson<PlaylistItem>(detailRes) ?: return null

            val videosRes = app.get(
                "$mainUrl/playlist/$id/videos" +
                        "?fields=id,title,thumbnail_360_url,duration&limit=100"
            ).text
            val videos = tryParseJson<VideoSearchResponse>(videosRes)?.list.orEmpty()

            return newTvSeriesLoadResponse(
                detail.name, url, TvType.TvSeries,
                videos.map { video ->
                    newEpisode("https://www.dailymotion.com/video/${video.id}") {
                        this.name = video.title
                        this.posterUrl = video.thumbnail360Url
                        this.runTime = video.duration?.let { it / 60 }
                    }
                }
            ) {
                // [FIX] 720 cho chất lượng cao, fallback 360
                this.posterUrl = detail.thumbnail720Url ?: detail.thumbnail360Url
            }
        }

        // Video đơn
        val response = app.get(
            "$mainUrl/video/$id?fields=id,title,thumbnail_720_url,thumbnail_360_url,duration"
        ).text
        val v = tryParseJson<VideoItem>(response) ?: return null

        return newMovieLoadResponse(
            v.title, url, TvType.Movie,
            "https://www.dailymotion.com/video/${v.id}"
        ) {
            // [FIX] Trước đây request 720 nhưng assign sai field → luôn null
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
