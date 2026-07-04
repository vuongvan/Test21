package recloudstream

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast

class DailymotionSettingsFragment(
    private val sharedPref: SharedPreferences
) : DialogFragment() {

    private val dp by lazy { resources.displayMetrics.density }
    private fun Int.dp() = (this * dp).toInt()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val scroll = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }

        // ── Header ──────────────────────────────────────────────────────────
        layout.addView(TextView(ctx).apply {
            text = "👤 Tài khoản Dailymotion"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp() }
        })

        layout.addView(TextView(ctx).apply {
            text = "Username để load danh sách following (trang chủ):"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp() }
        })

        val userInput = EditText(ctx).apply {
            hint    = DailymotionProvider.DEFAULT_FOLLOWING_USER
            setText(sharedPref.getString(DailymotionProvider.PREF_KEY_USER,
                DailymotionProvider.DEFAULT_FOLLOWING_USER))
            inputType  = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp() }
        }
        layout.addView(userInput)

        // ── Buttons ─────────────────────────────────────────────────────────
        val saveBtn = Button(ctx).apply {
            text = "💾 Lưu"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.dp() }
            setOnClickListener {
                val newUser = userInput.text.toString().trim()
                    .takeIf { it.isNotEmpty() }
                    ?: DailymotionProvider.DEFAULT_FOLLOWING_USER

                sharedPref.edit()
                    .putString(DailymotionProvider.PREF_KEY_USER, newUser)
                    .apply()

                // Reset cache để trang chủ reload theo user mới
                DailymotionProvider.cachedUsers   = null
                DailymotionProvider.cachedForUser = null

                showToast("Đã lưu: $newUser")
                dismiss()
            }
        }

        val resetBtn = Button(ctx).apply {
            text = "🔄 Reset mặc định"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp() }
            setOnClickListener {
                sharedPref.edit()
                    .putString(DailymotionProvider.PREF_KEY_USER,
                        DailymotionProvider.DEFAULT_FOLLOWING_USER)
                    .apply()
                DailymotionProvider.cachedUsers   = null
                DailymotionProvider.cachedForUser = null
                userInput.setText(DailymotionProvider.DEFAULT_FOLLOWING_USER)
                showToast("Đã reset về mặc định")
            }
        }

        layout.addView(saveBtn)
        layout.addView(resetBtn)
        scroll.addView(layout)
        return scroll
    }
}
