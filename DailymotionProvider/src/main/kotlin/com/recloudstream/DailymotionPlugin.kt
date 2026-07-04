package recloudstream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class DailymotionPlugin : Plugin() {
    override fun load(context: Context) {
        // SharedPreferences riêng của plugin
        val prefs = context.getSharedPreferences(
            "DailymotionPlugin", Context.MODE_PRIVATE
        )

        // Đăng ký provider, truyền sharedPref vào constructor
        val provider = DailymotionProvider(prefs)
        registerMainAPI(provider)

        // Đăng ký nút ⚙️ Settings trong menu plugin
        val activity = context as? AppCompatActivity
        if (activity != null) {
            openSettings = {
                val frag = DailymotionSettingsFragment(prefs)
                frag.show(activity.supportFragmentManager, "DailymotionSettings")
            }
        }
    }
}
