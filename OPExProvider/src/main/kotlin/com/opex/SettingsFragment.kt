package com.opex

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast

class SettingsFragment(
    plugin: OPExPlugin,
    private val sharedPref: SharedPreferences,
) : DialogFragment() {

    private val dp by lazy { resources.displayMetrics.density }
    private fun Int.dp() = (this * dp).toInt()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }

        // ── Helper builders ──────────────────────────────────────────────────

        fun sectionHeader(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.dp(); bottomMargin = 4.dp() }
        }

        fun label(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp() }
        }

        fun switchRow(labelText: String, prefKey: String, default: Boolean): Switch {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dp() }
            }
            val tv = TextView(ctx).apply {
                text = labelText
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val sw = Switch(ctx).apply {
                isChecked = sharedPref.getBoolean(prefKey, default)
            }
            row.addView(tv); row.addView(sw)
            layout.addView(row)
            return sw
        }

        fun editRow(hintText: String, prefKey: String, default: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
            val et = EditText(ctx).apply {
                hint = hintText
                setText(sharedPref.getString(prefKey, default))
                this.inputType = inputType
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            layout.addView(et)
            return et
        }

        // ── Section: Domain ──────────────────────────────────────────────────
        layout.addView(sectionHeader("🌐 Domain"))
        layout.addView(label("Base URL (để trống dùng mặc định):"))
        val domainEdit = editRow(
            "https://ophim1.com",
            OPExProvider.PREF_DOMAIN,
            OPExProvider().mainUrl,
            InputType.TYPE_TEXT_VARIATION_URI
        )

        // ── Section: TMDB Features ───────────────────────────────────────────
        layout.addView(sectionHeader("🎬 TMDB"))
        val swPoster      = switchRow("Dùng poster từ TMDB",      OPExProvider.PREF_USE_TMDB_POSTER,     true)
        val swBackdrop    = switchRow("Dùng backdrop từ TMDB",    OPExProvider.PREF_USE_TMDB_BACKDROP,   true)
        val swPlot        = switchRow("Dùng nội dung từ TMDB",    OPExProvider.PREF_USE_TMDB_PLOT,       true)
        val swRecs        = switchRow("Hiện phim đề xuất",         OPExProvider.PREF_USE_RECOMMENDATIONS, true)

        layout.addView(label("Số diễn viên hiển thị (1-30):"))
        val castCountEdit = EditText(ctx).apply {
            hint = "15"
            setText(sharedPref.getInt(OPExProvider.PREF_CAST_COUNT, 15).toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(castCountEdit)

        layout.addView(sectionHeader("🔍 Lọc nội dung"))
        val swFilterTrailer = switchRow("Ẩn trailer khỏi danh sách", OPExProvider.PREF_TRAILER_COUNT, true)

        // ── Section: Categories ──────────────────────────────────────────────
        layout.addView(sectionHeader("📋 Danh mục trang chủ"))

        val defaultPaths = listOf(
            "v1/api/danh-sach/phim-moi-cap-nhat",
            "v1/api/danh-sach/phim-thuyet-minh",
            "v1/api/danh-sach/phim-long-tieng",
            "v1/api/danh-sach/phim-le",
            "v1/api/danh-sach/hoat-hinh",
            ""
        )
        val defaultNames = listOf(
            "Mới Cập Nhật", "Phim Thuyết Minh", "Phim Lồng Tiếng",
            "Phim Lẻ", "Phim Hoạt Hình", "Danh Sách 6"
        )

        layout.addView(label("Format: Tên Hiển Thị|api/path (mỗi dòng 1 category)"))

        // Build initial text: mỗi dòng = "Tên|path"
        val initialText = (1..6).joinToString("\n") { i ->
            val name = sharedPref.getString(OPExProvider.getPreferenceNameKey(i), defaultNames[i-1]) ?: defaultNames[i-1]
            val path = sharedPref.getString(OPExProvider.getPreferenceKey(i), defaultPaths[i-1]) ?: defaultPaths[i-1]
            "$name|$path"
        }
        val categoryEdit = EditText(ctx).apply {
            setText(initialText)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
            minLines = 6
            maxLines = 10
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(categoryEdit)

        // ── Buttons ──────────────────────────────────────────────────────────
        fun promptRestart(title: String, message: String) {
            AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Khởi động lại") { _, _ -> dismiss(); restartApp() }
                .setNegativeButton("Để sau") { _, _ -> dismiss() }
                .show()
        }

        val saveBtn = Button(ctx).apply {
            text = "💾 Lưu"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp() }
            setOnClickListener {
                val castVal = castCountEdit.text.toString().toIntOrNull()?.coerceIn(1, 30) ?: 15
                sharedPref.edit().apply {
                    putString(OPExProvider.PREF_DOMAIN, domainEdit.text.toString().trim())
                    putBoolean(OPExProvider.PREF_USE_TMDB_POSTER,      swPoster.isChecked)
                    putBoolean(OPExProvider.PREF_USE_TMDB_BACKDROP,    swBackdrop.isChecked)
                    putBoolean(OPExProvider.PREF_USE_TMDB_PLOT,        swPlot.isChecked)
                    putBoolean(OPExProvider.PREF_USE_RECOMMENDATIONS,  swRecs.isChecked)
                    putBoolean(OPExProvider.PREF_TRAILER_COUNT,        swFilterTrailer.isChecked)
                    putInt(OPExProvider.PREF_CAST_COUNT, castVal)
                    // Parse "Tên|path" per line
                    val lines = categoryEdit.text.toString().lines()
                    for (i in 0 until 6) {
                        val line = lines.getOrNull(i) ?: ""
                        val parts = line.split("|", limit = 2)
                        putString(OPExProvider.getPreferenceNameKey(i + 1), parts.getOrNull(0)?.trim() ?: defaultNames[i])
                        putString(OPExProvider.getPreferenceKey(i + 1),     parts.getOrNull(1)?.trim() ?: defaultPaths[i])
                    }
                    apply()
                }
                showToast("Đã lưu")
                promptRestart("Lưu thành công", "Cần khởi động lại để áp dụng thay đổi.")
            }
        }

        val resetBtn = Button(ctx).apply {
            text = "🔄 Reset mặc định"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp() }
            setOnClickListener {
                sharedPref.edit().clear().apply()
                domainEdit.setText(OPExProvider().mainUrl)
                swPoster.isChecked      = true
                swBackdrop.isChecked    = true
                swPlot.isChecked        = true
                swRecs.isChecked        = true
                swFilterTrailer.isChecked = true
                castCountEdit.setText("15")
                categoryEdit.setText(
                    (0 until 6).joinToString("\n") { i -> "${defaultNames[i]}|${defaultPaths[i]}" }
                )
                showToast("Đã reset")
                promptRestart("Reset thành công", "Cần khởi động lại để áp dụng thay đổi.")
            }
        }

        layout.addView(saveBtn)
        layout.addView(resetBtn)
        scroll.addView(layout)
        return scroll
    }

    private fun restartApp() {
        val ctx = requireContext().applicationContext
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val component = intent?.component ?: return
        ctx.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }
}
