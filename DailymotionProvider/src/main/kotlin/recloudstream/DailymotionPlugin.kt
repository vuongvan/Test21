package recloudstream

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DailymotionPlugin : Plugin() {

    override fun load(context: Context) {
        // Đăng ký provider
        registerMainAPI(DailymotionProvider())

        // Đăng ký nút setting trong menu của plugin
        openSettings = {
            val ctx = activity ?: return@openSettings
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val currentUser = prefs.getString(
                DailymotionProvider.PREF_KEY_USER,
                DailymotionProvider.DEFAULT_FOLLOWING_USER
            ) ?: DailymotionProvider.DEFAULT_FOLLOWING_USER

            // Layout: label + EditText
            val layout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 32, 48, 16)
            }
            layout.addView(TextView(ctx).apply {
                text = "Dailymotion Username\n(người dùng để load trang chủ)"
                setPadding(0, 0, 0, 16)
            })
            val input = EditText(ctx).apply {
                setText(currentUser)
                hint = "vd: taunt-preface-runt"
                setSingleLine()
            }
            layout.addView(input)

            AlertDialog.Builder(ctx)
                .setTitle("Dailymotion Settings")
                .setView(layout)
                .setPositiveButton("Lưu") { _, _ ->
                    val newUser = input.text.toString().trim()
                    if (newUser.isNotEmpty()) {
                        prefs.edit()
                            .putString(DailymotionProvider.PREF_KEY_USER, newUser)
                            .apply()
                        // Reset cache
                        DailymotionProvider.cachedUsers = null
                        DailymotionProvider.cachedForUser = null
                    }
                }
                .setNegativeButton("Hủy", null)
                .setNeutralButton("Reset mặc định") { _, _ ->
                    prefs.edit()
                        .putString(
                            DailymotionProvider.PREF_KEY_USER,
                            DailymotionProvider.DEFAULT_FOLLOWING_USER
                        )
                        .apply()
                    DailymotionProvider.cachedUsers = null
                    DailymotionProvider.cachedForUser = null
                }
                .show()
        }
    }
}
