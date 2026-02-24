package com.opex

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class OPExPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký provider OPhim
        registerMainAPI(OPExProvider())
    }
}
