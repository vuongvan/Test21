package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ScrollView
import android.widget.TextView
import android.content.Context
import android.view.ViewGroup

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(KKPExProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val currentDomain = prefs.getString(KKPExProvider.PREF_DOMAIN, KKPExProvider().mainUrl)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(20, 20, 20, 20)
        }

        val domainLabel = TextView(this).apply {
            text = "Domain Thiết lập"
            textSize = 16f
            setPadding(0, 0, 0, 10)
        }

        val domainEdit = EditText(this).apply {
            hint = "Domain (e.g. https://example.com)"
            setText(currentDomain)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(10, 10, 10, 10)
        }

        // Category settings
        val categoryTitleLabel = TextView(this).apply {
            text = "⚙️ Cài Đặt Danh Sách Phim"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        }

        val categoryDescLabel = TextView(this).apply {
            text = "Nhập đường dẫn API sau domain, ví dụ: quoc-gia/trung-quoc, v1/api/phim-bo, v.v. Để trống để bỏ qua danh sách này.\n\n3 danh sách đầu có giá trị mặc định: Phim Trung Quốc, Phim Hàn Quốc, Phim Hoạt Hình"
            textSize = 12f
            setPadding(0, 0, 0, 15)
        }

        val categoryEdits = mutableListOf<EditText>()
        val categoryNames = listOf("Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 4", "Danh Sách 5", "Danh Sách 6")
        for (i in 1..6) {
            val categoryLabel = TextView(this).apply {
                text = categoryNames[i - 1] + ":"
                textSize = 14f
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

            val categoryEdit = EditText(this).apply {
                hint = "Ví dụ: quoc-gia/han-quoc hoặc v1/api/phim-le"
                setText(prefs.getString(preKey, defaultValue))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(10, 10, 10, 10)
            }

            layout.addView(categoryLabel)
            layout.addView(categoryEdit)
            categoryEdits.add(categoryEdit)
        }

        val saveBtn = Button(this).apply {
            text = "Lưu"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(20, 10, 20, 10)
            setOnClickListener {
                val domain = domainEdit.text.toString().trim()
                
                if (domain.isEmpty()) {
                    Toast.makeText(this@SettingsActivity, "Domain không thể trống", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                prefs.edit().apply {
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
                
                Toast.makeText(this@SettingsActivity, "Lưu thành công", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        layout.addView(domainLabel)
        layout.addView(domainEdit)
        layout.addView(categoryTitleLabel)
        layout.addView(categoryDescLabel)
        layout.addView(saveBtn)

        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
