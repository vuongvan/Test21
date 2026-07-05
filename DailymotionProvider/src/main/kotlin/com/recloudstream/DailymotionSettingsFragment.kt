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

        // ── Setting 1: Following user ──────────────────────────────────────
        layout.addView(TextView(ctx).apply {
            text = "👤 Tài khoản Following"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        layout.addView(TextView(ctx).apply {
            text = "Username để lấy playlist của những người user này đang follow:"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp() }
        })

        val userInput = EditText(ctx).apply {
            hint = DailymotionProvider.DEFAULT_FOLLOWING_USER
            setText(
                sharedPref.getString(
                    DailymotionProvider.PREF_KEY_USER,
                    DailymotionProvider.DEFAULT_FOLLOWING_USER
                )
            )
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp() }
        }
        layout.addView(userInput)

        // ── Setting 2: Extra users (playlist trực tiếp) ────────────────────
        layout.addView(TextView(ctx).apply {
            text = "📋 Danh sách user lấy playlist trực tiếp"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24.dp() }
        })
        layout.addView(TextView(ctx).apply {
            text = "Mỗi dòng 1 username. Playlist của các user này sẽ hiện thẳng " +
                    "lên trang chủ, không cần qua bước following."
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp() }
        })

        val extraUsersInput = EditText(ctx).apply {
            hint = "vd:\nuser-one\nuser-two\nuser-three"
            setText(sharedPref.getString(DailymotionProvider.PREF_KEY_EXTRA_USERS, ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp() }
        }
        layout.addView(extraUsersInput)

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

                val newExtraUsers = extraUsersInput.text.toString()

                sharedPref.edit()
                    .putString(DailymotionProvider.PREF_KEY_USER, newUser)
                    .putString(DailymotionProvider.PREF_KEY_EXTRA_USERS, newExtraUsers)
                    .apply()

                // Reset cache để trang chủ reload theo setting mới
                DailymotionProvider.cachedUsers   = null
                DailymotionProvider.cachedForUser = null

                showToast("Đã lưu cài đặt")
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
                    .putString(
                        DailymotionProvider.PREF_KEY_USER,
                        DailymotionProvider.DEFAULT_FOLLOWING_USER
                    )
                    .putString(DailymotionProvider.PREF_KEY_EXTRA_USERS, "")
                    .apply()
                DailymotionProvider.cachedUsers   = null
                DailymotionProvider.cachedForUser = null
                userInput.setText(DailymotionProvider.DEFAULT_FOLLOWING_USER)
                extraUsersInput.setText("")
                showToast("Đã reset về mặc định")
            }
        }

        layout.addView(saveBtn)
        layout.addView(resetBtn)
        scroll.addView(layout)
        return scroll
    }
}
