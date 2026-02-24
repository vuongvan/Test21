package com.example
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ExamplePlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký provider của bạn tại đây
        // Load saved domain from SharedPreferences (if user changed it in SettingsActivity)
        val prefs = context.getSharedPreferences("example_provider_prefs", Context.MODE_PRIVATE)
        val domain = prefs.getString("domain", null)
        val provider = ExampleProvider()
        if (!domain.isNullOrEmpty()) provider.mainUrl = domain
        ExampleProvider.ctx = context
        registerMainAPI(provider)
    }
}
