package com.example
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class KKPExPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký provider của bạn tại đây
        // Load saved domain from SharedPreferences (if user changed it in SettingsActivity)
        val prefs = context.getSharedPreferences(KKPExProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val domain = prefs.getString(KKPExProvider.PREF_DOMAIN, null)
        val provider = KKPExProvider()
        if (!domain.isNullOrEmpty()) provider.mainUrl = domain
        KKPExProvider.ctx = context
        registerMainAPI(provider)
        // Expose settings UI in CloudStream settings
        val activity = context as? AppCompatActivity
        if (activity != null) {
            openSettings = {
                val frag = SettingsFragment(this, prefs)
                frag.show(activity.supportFragmentManager, "KKPExSettings")
            }
        }
    }
}
