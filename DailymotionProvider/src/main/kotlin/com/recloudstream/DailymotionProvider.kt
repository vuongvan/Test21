package recloudstream

import android.content.SharedPreferences
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLEncoder

class DailymotionProvider(private val sharedPref: SharedPreferences?) : MainAPI() {

    // Constructor không tham số — bắt buộc để CloudStream có thể khởi tạo lại
    // provider ở những nơi nó cần một instance mặc định (vd: hiển thị icon, tên).
    constructor() : this(null)

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
        private val ID_REGEX = Regex("(?:video|playlist)/([a-zA-Z0-9]+)")

        const val DEFAULT_FOLLOWING_USER = "taunt-preface-runt"
        const val PREF_KEY_USER          = "dailymotion_following_user"
        // [NEW] Setting 2 — danh sách username nhập trực tiếp, mỗi dòng 1 user.
        // Playlist của các user này được lấy thẳng, không qua bước "following".
        const val PREF_KEY_EXTRA_USERS   = "dailymotion_extra_users"

        // Cache — reset khi username thay đổi
        @Volatile var cachedUsers: List<UserItem>? = null
        @Volatile var cachedForUser: String?        = null
    }

    private fun getFollowingUser(): String =
        sharedPref?.getString(PREF_KEY_USER, DEFAULT_FOLLOWING_USER)
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FOLLOWING_USER

    // [NEW] Đọc danh sách user trực tiếp từ setting 2 — mỗi dòng là 1 username.
    // Trả về list rỗng nếu setting trống, đã lọc bỏ dòng trắng và khoảng trắng thừa.
    private fun getExtraUsers(): List<String> =
        sharedPref?.getString(PREF_KEY_EXTRA_USERS, "")
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private suspend fun getFollowingUsers(): List<UserItem> {
        val currentUser = getFollowingUser()
        if (cachedForUser != currentUser) {
            cachedUsers   = null
            cachedForUser = currentUser
        }
        cachedUsers?.let { return it }
        val url = "$mainUrl/user/$currentUser/following?fields=id,screenname&limit=10&page=1"
        return tryParseJson<FollowingResponse>(app.get(url).text)
            ?.list.orEmpty()
            .also { cachedUsers = it }
    }

    // Fetch playlists của 1 user (dùng chung cho cả 2 nguồn: following & extra)
    private suspend fun fetchUserPlaylists(userId: String, page: Int): List<PlaylistItem> {
        val playlistUrl = "$mainUrl/user/$userId/playlists" +
                "?fields=id,name,thumbnail_360_url&limit=20&page=$page"
        val playlistRes = runCatching { app.get(playlistUrl).text }.getOrNull() ?: return emptyList()
        return tryParseJson<PlaylistSearchResponse>(playlistRes)?.list.orEmpty()
    }

    private fun PlaylistItem.toSearchResponse(): SearchResponse {
        val poster = this.thumbnail360Url
        return newMovieSearchResponse(
            this.name,
            "https://www.dailymotion.com/playlist/${this.id}",
            TvType.TvSeries
        ) { this.posterUrl = poster }
    }

    // --- MAIN PAGE ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePages = mutableListOf<HomePageList>()

        // [Setting 1] Following users — lấy playlist của những người user đang follow
        for (user in getFollowingUsers()) {
            val playlists = fetchUserPlaylists(user.id, page)
            if (playlists.isNotEmpty()) {
                homePages.add(HomePageList(
                    name = user.screenname,
                    list = playlists.map { it.toSearchResponse() }
                ))
            }
        }

        // [Setting 2] Extra users — username nhập trực tiếp, lấy playlist thẳng
        // Ở đây "userId" chính là username (Dailymotion API chấp nhận cả username
        // hoặc numeric id trong endpoint /user/{id}/playlists).
        for (username in getExtraUsers()) {
            val playlists = fetchUserPlaylists(username, page)
            if (playlists.isNotEmpty()) {
                homePages.add(HomePageList(
                    name = username,
                    list = playlists.map { it.toSearchResponse() }
                ))
            }
        }

        return newHomePageResponse(homePages, hasNext = true)
    }

    // --- SEARCH ---
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        if (query.startsWith("http")) {
            val videoId = ID_REGEX.find(query)?.groupValues?.get(1)
            if (videoId != null) {
                return if (query.contains("/playlist/")) {
                    tryParseJson<PlaylistItem>(
                        app.get("$mainUrl/playlist/$videoId?fields=id,name,thumbnail_360_url").text
                    )?.let {
                        listOf(newMovieSearchResponse(it.name,
                            "https://www.dailymotion.com/playlist/${it.id}", TvType.TvSeries
                        ) { this.posterUrl = it.thumbnail360Url }).toNewSearchResponseList()
                    }
                } else {
                    tryParseJson<VideoItem>(
                        app.get("$mainUrl/video/$videoId?fields=id,title,thumbnail_360_url").text
                    )?.let {
                        listOf(newMovieSearchResponse(it.title,
                            "https://www.dailymotion.com/video/${it.id}", TvType.Movie
                        ) { this.posterUrl = it.thumbnail360Url }).toNewSearchResponseList()
                    }
                }
            }
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val vRes = app.get(
            "$mainUrl/videos?fields=id,title,thumbnail_360_url" +
                    "&limit=20&page=$page&search=$encodedQuery&sort=relevance"
        ).text

        return tryParseJson<VideoSearchResponse>(vRes)?.list?.map {
            newMovieSearchResponse(it.title,
                "https://www.dailymotion.com/video/${it.id}", TvType.Movie
            ) { this.posterUrl = it.thumbnail360Url }
        }?.toNewSearchResponseList()
    }

    // --- LOAD ---
    override suspend fun load(url: String): LoadResponse? {
        val id = ID_REGEX.find(url)?.groupValues?.get(1) ?: return null

        if (url.contains("/playlist/")) {
            val detail = tryParseJson<PlaylistItem>(
                app.get("$mainUrl/playlist/$id?fields=id,name,thumbnail_720_url,thumbnail_360_url").text
            ) ?: return null
            val videos = tryParseJson<VideoSearchResponse>(
                app.get("$mainUrl/playlist/$id/videos?fields=id,title,thumbnail_360_url,duration&limit=100").text
            )?.list.orEmpty()
                // [FIX] API trả về tập mới nhất ở đầu -> đảo ngược để tập 1 lên đầu
                .reversed()

            return newTvSeriesLoadResponse(detail.name, url, TvType.TvSeries,
                videos.map { video ->
                    newEpisode("https://www.dailymotion.com/video/${video.id}") {
                        this.name     = video.title
                        this.posterUrl = video.thumbnail360Url
                        this.runTime  = video.duration?.let { it / 60 }
                    }
                }
            ) { this.posterUrl = detail.thumbnail720Url ?: detail.thumbnail360Url }
        }

        val v = tryParseJson<VideoItem>(
            app.get("$mainUrl/video/$id?fields=id,title,thumbnail_720_url,thumbnail_360_url,duration").text
        ) ?: return null

        return newMovieLoadResponse(v.title, url, TvType.Movie,
            "https://www.dailymotion.com/video/${v.id}"
        ) {
            this.posterUrl = v.thumbnail720Url ?: v.thumbnail360Url
            this.duration  = v.duration?.let { it / 60 }
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = loadExtractor(data, subtitleCallback, callback)
}
