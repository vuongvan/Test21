package com.opex

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Locale
import android.content.Context

// --- DATA CLASSES (Giữ nguyên) ---
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
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("imdb") val imdb: OPImdbListItem? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null
)
data class OPImdbListItem(@param:JsonProperty("vote_average") val vote_average: Double? = null)
data class OPRootResponse(@param:JsonProperty("data") val data: OPDataContent? = null)
data class OPDataContent(
    @param:JsonProperty("item") val item: OPItemDetail? = null,
    @param:JsonProperty("seoOnPage") val seoOnPage: OPSeoOnPage? = null,
    @param:JsonProperty("APP_DOMAIN_CDN_IMAGE") val APP_DOMAIN_CDN_IMAGE: String? = null
)
data class OPSeoOnPage(@param:JsonProperty("seoSchema") val seoSchema: OPSeoSchema? = null)
data class OPSeoSchema(@param:JsonProperty("image") val image: String? = null)
data class OPItemDetail(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("content") val content: String? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("episode_current") val episode_current: String? = null,
    @param:JsonProperty("episode_total") val episode_total: String? = null,
    @param:JsonProperty("lang") val lang: String? = null,
    @param:JsonProperty("tmdb") val tmdb: OPTmdb? = null,
    @param:JsonProperty("category") val category: List<OPCat>? = null,
    @param:JsonProperty("poster_url") val poster_url: String? = null,
    @param:JsonProperty("thumb_url") val thumb_url: String? = null,
    @param:JsonProperty("episodes") val episodes: List<OPServer>? = null
)
data class OPTmdb(@param:JsonProperty("vote_average") val vote_average: Double? = null, @param:JsonProperty("id") val id: Any? = null)
data class OPCat(@param:JsonProperty("name") val name: String? = null)
data class OPServer(@param:JsonProperty("server_name") val server_name: String? = null, @param:JsonProperty("server_data") val server_data: List<OPEpisode>? = null)
data class OPEpisode(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("link_m3u8") val link_m3u8: String? = null)
data class TmdbCreditsResponse(@param:JsonProperty("cast") val cast: List<TmdbCast>? = null)
data class TmdbCast(@param:JsonProperty("name") val name: String? = null, @param:JsonProperty("profile_path") val profile_path: String? = null, @param:JsonProperty("character") val character: String? = null)
data class TmdbDetailResponse(@param:JsonProperty("vote_average") val vote_average: Double? = null, @param:JsonProperty("overview") val overview: String? = null)
