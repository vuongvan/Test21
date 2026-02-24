package com.example

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast

class SettingsFragment(
    plugin: KKPExPlugin,
    private val sharedPref: SharedPreferences,
) : DialogFragment() {

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ctx = requireContext()

        val scrollView = android.widget.ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Domain settings
        val domainLabel = TextView(ctx).apply {
            text = "Domain:"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val domainEdit = EditText(ctx).apply {
            hint = "Domain (e.g. https://example.com)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(sharedPref.getString(KKPExProvider.PREF_DOMAIN, KKPExProvider().mainUrl))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Category settings
        val categoryTitleLabel = TextView(ctx).apply {
            text = "⚙️ Cài Đặt Danh Sách Phim"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 24, 0, 12)
        }

        val categoryDescLabel = TextView(ctx).apply {
            text = "Nhập đường dẫn API sau domain. Ví dụ: quoc-gia/trung-quoc, v1/api/phim-bo, v.v. Để trống để bỏ qua danh sách này.\n\n3 danh sách đầu có giá trị mặc định: Phim Trung Quốc, Phim Hàn Quốc, Phim Hoạt Hình"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val categoryEdits = mutableListOf<EditText>()
        val categoryNames = listOf("Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 4", "Danh Sách 5", "Danh Sách 6")
        for (i in 1..6) {
            val categoryLabel = TextView(ctx).apply {
                text = categoryNames[i - 1] + ":"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, 12, 0, 8)
            }

            val preKey = when (i) {
                1 -> KKPExProvider.PREF_CATEGORY_1
                2 -> KKPExProvider.PREF_CATEGORY_2
                3 -> KKPExProvider.PREF_CATEGORY_3
                4 -> KKPExProvider.PREF_CATEGORY_4
                5 -> KKPExProvider.PREF_CATEGORY_5
                else -> KKPExProvider.PREF_CATEGORY_6
            }

            val defaultValue = when (i) {
                1 -> "quoc-gia/trung-quoc"
                2 -> "quoc-gia/han-quoc"
                3 -> "danh-sach/hoat-hinh"
                else -> ""
            }

            val categoryEdit = EditText(ctx).apply {
                hint = "Ví dụ: quoc-gia/han-quoc hoặc v1/api/phim-le"
                setText(sharedPref.getString(preKey, defaultValue))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            layout.addView(categoryLabel)
            layout.addView(categoryEdit)
            categoryEdits.add(categoryEdit)
        }

        // Save button
        val saveBtn = Button(ctx).apply {
            text = "Lưu Thay Đổi"
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
            setOnClickListener {
                val domain = domainEdit.text.toString().trim()
                
                if (domain.isEmpty()) {
                    showToast("Domain không thể trống")
                    return@setOnClickListener
                }
                
                sharedPref.edit().apply {
                    putString(KKPExProvider.PREF_DOMAIN, domain)
                    // Save custom categories
                    putString(KKPExProvider.PREF_CATEGORY_1, categoryEdits.getOrNull(0)?.text.toString().trim())
                    putString(KKPExProvider.PREF_CATEGORY_2, categoryEdits.getOrNull(1)?.text.toString().trim())
                    putString(KKPExProvider.PREF_CATEGORY_3, categoryEdits.getOrNull(2)?.text.toString().trim())
                    putString(KKPExProvider.PREF_CATEGORY_4, categoryEdits.getOrNull(3)?.text.toString().trim())
                    putString(KKPExProvider.PREF_CATEGORY_5, categoryEdits.getOrNull(4)?.text.toString().trim())
                    putString(KKPExProvider.PREF_CATEGORY_6, categoryEdits.getOrNull(5)?.text.toString().trim())
                    apply()
                }
                
                showToast("Lưu thành công")
                AlertDialog.Builder(ctx)
                    .setTitle("Lưu & Khởi Động Lại")
                    .setMessage("Thay đổi đã được lưu. Khởi động lại ứng dụng để áp dụng?")
                    .setPositiveButton("Có") { _, _ ->
                        dismiss()
                        restartApp()
                    }
                    .setNegativeButton("Không") { _, _ -> dismiss() }
                    .show()
            }
        }

        // Reset button
        val resetBtn = Button(ctx).apply {
            text = "Đặt Lại"
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
            setOnClickListener {
                sharedPref.edit().apply {
                    remove(KKPExProvider.PREF_CATEGORY_1)
                    remove(KKPExProvider.PREF_CATEGORY_2)
                    remove(KKPExProvider.PREF_CATEGORY_3)
                    remove(KKPExProvider.PREF_CATEGORY_4)
                    remove(KKPExProvider.PREF_CATEGORY_5)
                    remove(KKPExProvider.PREF_CATEGORY_6)
                    apply()
                }
                domainEdit.setText(KKPExProvider().mainUrl)
                categoryEdits[0].setText("quoc-gia/trung-quoc")
                categoryEdits[1].setText("quoc-gia/han-quoc")
                categoryEdits[2].setText("danh-sach/hoat-hinh")
                categoryEdits[3].setText("")
                categoryEdits[4].setText("")
                categoryEdits[5].setText("")
                showToast("Đã đặt lại thành mặc định")
                AlertDialog.Builder(ctx)
                    .setTitle("Đặt Lại & Khởi Động Lại")
                    .setMessage("Đã đặt lại hoàn toàn. Khởi động lại ứng dụng để áp dụng?")
                    .setPositiveButton("Có") { _, _ ->
                        dismiss()
                        restartApp()
                    }
                    .setNegativeButton("Không") { _, _ -> dismiss() }
                    .show()
            }
        }

        // Close button
        val closeBtn = Button(ctx).apply {
            text = "Đóng"
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
            setOnClickListener { dismiss() }
        }

        layout.addView(domainLabel)
        layout.addView(domainEdit)
        layout.addView(categoryTitleLabel)
        layout.addView(categoryDescLabel)
        layout.addView(saveBtn)
        layout.addView(resetBtn)
        layout.addView(closeBtn)

        scrollView.addView(layout)
        return scrollView
    }

    private fun restartApp() {
        val context = requireContext().applicationContext
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component

        if (componentName != null) {
            val restartIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(restartIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
