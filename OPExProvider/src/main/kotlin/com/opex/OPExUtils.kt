package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty

object OPExUtils {
    // Nên được nạp từ GitHub Secret qua YAML khi build





    // Trả MAL ID để CS3 tracker tự fetch nhân vật anime với ảnh artwork
// --- Data Classes ---

data class OPListResponse(
    @param:JsonProperty("data") val data: OPListData? = null,
    @param:JsonProperty("items") val items: List<OPItem>? = null,
    @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null
)

data class OPListData(
    @param:JsonProperty("items") val items: List<OPItem>? = null,
    @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null
)

data class OPItem(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("imdb") val imdb: OPImdbListItem? = null
)

data class OPTmdb(
    @param:JsonProperty("vote_average") val vote_average: Double? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("season") val season: Int? = null
)

data class OPImdbListItem(@param:JsonProperty("vote_average") val vote_average: Double? = null)

data class OPRootResponse(@param:JsonProperty("data") val data: OPDataContent? = null)

data class OPDataContent(
    @param:JsonProperty("item") val item: OPItemDetail? = null,
    @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null
)


data class OPItemDetail(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("origin_name") val origin_name: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("country") val country: List<OPCountry>? = null,
    @param:JsonProperty("category") val category: List<OPCat>? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("episodes") val episodes: List<OPServer>? = null
)

data class OPCountry(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null
)

data class OPCat(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("slug") val slug: String? = null
)

data class OPServer(
    @param:JsonProperty("server_name") val server_name: String? = null,
    @param:JsonProperty("server_data") val server_data: List<OPEpisode>? = null
)

data class OPEpisode(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("link_m3u8") val link_m3u8: String? = null
)

