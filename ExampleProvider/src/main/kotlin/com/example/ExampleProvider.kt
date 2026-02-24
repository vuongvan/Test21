package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import android.content.Context

class ExampleProvider : MainAPI() {
    companion object {
        lateinit var ctx: Context
        const val PREFS_NAME = "example_provider_prefs"
        const val PREF_DOMAIN = "domain"
    }
    override var mainUrl = "https://phimapi.com"
    override var name = "KK Phim"
    override val hasMainPage = true
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private fun fixPosterUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return if (url.startsWith("http")) url else "https://phimimg.com/$url"
    }

    private suspend fun getListFromUrl(url: String): List<SearchResponse> {
        return try {
            val response = app.get(url).text
            val data = parseJson<KKListResponse>(response)
            val items = data.data?.items ?: data.items 
            items?.map {
                newMovieSearchResponse(it.name ?: "", "$mainUrl/phim/${it.slug}", TvType.Movie) {
                    this.posterUrl = fixPosterUrl(it.poster_url ?: it.thumb_url)
                }
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = listOf(
            Pair("$mainUrl/danh-sach/phim-moi-cap-nhat?page=$page", "Phim Mới Cập Nhật"),
            Pair("$mainUrl/v1/api/quoc-gia/trung-quoc?page=$page", "Phim Trung Quốc"),
            Pair("$mainUrl/v1/api/quoc-gia/han-quoc?page=$page", "Phim Hàn Quốc"),
            Pair("$mainUrl/v1/api/quoc-gia/nhat-ban?page=$page", "Phim Nhật Bản")
        )
        val homePageLists = items.map { (url, title) -> HomePageList(title, getListFromUrl(url)) }
        return newHomePageResponse(homePageLists, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/v1/api/tim-kiem?keyword=$query&limit=20"
        val response = app.get(url).text
        val data = parseJson<KKSearchResponse>(response)
        return data.data?.items?.map {
            newMovieSearchResponse(it.name ?: "", "$mainUrl/phim/${it.slug}", TvType.Movie) {
                this.posterUrl = fixPosterUrl(it.poster_url ?: it.thumb_url)
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val res = parseJson<KKDetailResponse>(response)
        val movie = res.movie ?: return null
        
        val episodeMap = mutableMapOf<String, MutableList<String>>()
        res.episodes?.forEach { server ->
            val serverName = server.server_name ?: "HLS"
            server.server_data?.forEach { ep ->
                val epName = ep.name ?: "1"
                val existingLinks = episodeMap.getOrPut(epName) { mutableListOf() }
                existingLinks.add("${ep.link_m3u8}::${serverName}")
            }
        }

        // FIX LỖI GOM NHÓM TẬP: Chỉ lấy số đầu tiên tìm thấy trong tên tập
        val episodesList = episodeMap.map { (epName, links) ->
            newEpisode(links.joinToString("|||")) {
                this.name = "Tập $epName"
                val s = Regex("""(\d+)""").find(epName)?.value
                this.episode = s?.toIntOrNull()
            }
        }.sortedBy { it.episode }

        val finalPoster = fixPosterUrl(movie.poster_url ?: movie.thumb_url)
        val movieTags = mutableListOf<String>()
        
        // 1. Tag Trạng thái: Ongoing / Completed
        val isCompleted = movie.status == "completed"
        movieTags.add(if (isCompleted) "Completed" else "Ongoing")

        // 2. Tag Điểm TMDB: Làm tròn 1 chữ số thập phân (Ví dụ: 9.0)
        movie.tmdb?.vote_average?.let { score ->
            if (score > 0) {
                val formattedScore = "%.1f".format(score).replace(",", ".")
                movieTags.add("⭐ $formattedScore")
            }
        }

        // 3. Tag Tập phim: Hiển thị dạng 5/16 cho phim Ongoing
        val totalEpisodes = movie.episode_total ?: ""
        movie.episode_current?.let { current ->
            val tagEp = when {
                current.contains("Full", ignoreCase = true) -> "Full"
                !isCompleted && totalEpisodes.isNotEmpty() && !current.contains("/") -> {
                    "${episodesList.size}/$totalEpisodes"
                }
                current.contains("(") -> current.substringAfter("(").substringBefore(")")
                else -> current.replace("Tập ", "")
            }
            movieTags.add("Tập $tagEp")
        }

        // 4. Tag Chất lượng
        movie.quality?.let { movieTags.add(it) }

        val fullPlot = """
            Diễn viên: ${movie.actor?.joinToString(", ") ?: "Đang cập nhật"}
            
            ${movie.content ?: "Không có nội dung mô tả."}
        """.trimIndent()

        val isSeries = movie.type == "series" || movie.type == "hoathinh" || episodesList.size > 1

        return if (isSeries) {
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodesList) {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
            }
        } else {
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, episodesList.firstOrNull()?.data ?: "") {
                this.posterUrl = finalPoster
                this.year = movie.year
                this.plot = fullPlot
                this.tags = movieTags
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        data.split("|||").forEach { item ->
            val parts = item.split("::")
            val link = parts.getOrNull(0) ?: ""
            val serverName = parts.getOrNull(1) ?: "HLS"
            if (link.isNotEmpty()) {
                callback.invoke(newExtractorLink(serverName, serverName, link, type = ExtractorLinkType.M3U8))
            }
        }
        return true
    }
}

// --- DATA MODELS ---
data class KKListResponse(@param:JsonProperty("items") val items: List<KKItem>? = null, @param:JsonProperty("data") val data: KKListData? = null)
data class KKItem(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("slug") val slug: String? = null, @param:JsonProperty("poster_url") val poster_url: String? = null, @param:JsonProperty("thumb_url") val thumb_url: String? = null)
data class KKSearchResponse(@param:JsonProperty("data") val data: KKListData? = null)
data class KKListData(@param:JsonProperty("items") val items: List<KKItem>? = null)
data class KKDetailResponse(@param:JsonProperty("movie") val movie: KKMovie? = null, @param:JsonProperty("episodes") val episodes: List<KKServer>? = null)

data class KKMovie(
    @param:JsonProperty("name") val name: String? = null, 
    @param:JsonProperty("type") val type: String? = null, 
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("actor") val actor: List<String>? = null,
    @param:JsonProperty("tmdb") val tmdb: KKTMDB? = null
)

data class KKTMDB(@param:JsonProperty("vote_average") val vote_average: Double? = null)
data class KKServer(@param:JsonProperty("server_name") val server_name: String? = null, @param:JsonProperty("server_data") val server_data: List<KKEpisode>? = null)
data class KKEpisode(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("link_m3u8") val link_m3u8: String? = null)
