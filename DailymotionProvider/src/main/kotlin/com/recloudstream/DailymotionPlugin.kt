package recloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DailymotionPlugin : Plugin() {

    override fun load(context: Context) {
        // SharedPreferences riêng của plugin — dùng tên unique để không conflict
        val sharedPref = context.getSharedPreferences(
            "DailymotionPlugin", Context.MODE_PRIVATE
        )

        // Đăng ký provider, truyền sharedPref vào constructor
        registerMainAPI(DailymotionProvider(sharedPref))

        // Đăng ký nút ⚙️ Settings trong menu plugin
        openSettings = {
            val frag = DailymotionSettingsFragment(sharedPref)
            // activity được inject bởi CloudStream framework vào Plugin
            val fm = (activity as? androidx.fragment.app.FragmentActivity)
                ?.supportFragmentManager
                ?: return@openSettings
            frag.show(fm, "DailymotionSettings")
        }
    }
}
