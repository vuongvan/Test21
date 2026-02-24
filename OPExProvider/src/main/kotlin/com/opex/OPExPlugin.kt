package com.opex

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class OPExPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký provider OPhim
        val prefs = context.getSharedPreferences("opex_provider_prefs", Context.MODE_PRIVATE)
        val domain = prefs.getString("domain", null)
        val provider = OPExProvider()
        if (!domain.isNullOrEmpty()) provider.mainUrl = domain
        OPExProvider.ctx = context
        registerMainAPI(provider)
    }
}
