package com.opex

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.Context
import android.view.ViewGroup

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(OPExProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(OPExProvider.PREF_DOMAIN, OPExProvider().mainUrl)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(20, 20, 20, 20)
        }

        val domainEdit = EditText(this).apply {
            hint = "Domain (e.g. https://example.com)"
            setText(current)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Category lists for labels and default names
        val categoryNames = listOf("Phim Lẻ Mới", "Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")

        // Lists to hold all edits
        val categoryNameEdits = mutableListOf<EditText>()
        val categoryEdits = mutableListOf<EditText>()

        // Default category API paths for OPExProvider
        val defaultPaths = listOf(
            "v1/api/danh-sach/phim-le",
            "v1/api/quoc-gia/trung-quoc",
            "v1/api/quoc-gia/han-quoc",
            "v1/api/danh-sach/hoat-hinh",
            "",
            ""
        )

        // Add domain label and input
        val domainLabel = TextView(this).apply {
            text = "Domain:"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
        }
        layout.addView(domainLabel)
        layout.addView(domainEdit)

        // Add category sections (6 categories)
        for (i in 1..6) {
            val categoryHeading = TextView(this).apply {
                text = "Category $i:"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 16
                }
            }

            val categoryNameLabel = TextView(this).apply {
                text = "Display Name:"
                textSize = 12f
            }

            val categoryNameEdit = EditText(this).apply {
                hint = "Display name"
                val savedName = prefs.getString(OPExProvider.getPreferenceNameKey(i), categoryNames.getOrNull(i - 1) ?: "Danh Sách $i")
                setText(savedName)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val categoryPathLabel = TextView(this).apply {
                text = "API Path:"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8
                }
            }

            val categoryEdit = EditText(this).apply {
                hint = "API path (e.g. v1/api/danh-sach/phim-le)"
                val savedPath = prefs.getString(OPExProvider.getPreferenceKey(i), defaultPaths.getOrNull(i - 1) ?: "")
                setText(savedPath)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            layout.addView(categoryHeading)
            layout.addView(categoryNameLabel)
            layout.addView(categoryNameEdit)
            layout.addView(categoryPathLabel)
            layout.addView(categoryEdit)

            categoryNameEdits.add(categoryNameEdit)
            categoryEdits.add(categoryEdit)
        }

        val saveBtn = Button(this).apply {
            text = "Lưu"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
            setOnClickListener {
                val domain = domainEdit.text.toString().trim()
                prefs.edit().apply {
                    putString(OPExProvider.PREF_DOMAIN, domain)

                    // Save all category names and paths
                    for (i in 0 until 6) {
                        val nameKey = OPExProvider.getPreferenceNameKey(i + 1)
                        val pathKey = OPExProvider.getPreferenceKey(i + 1)
                        putString(nameKey, categoryNameEdits.getOrNull(i)?.text?.toString() ?: "")
                        putString(pathKey, categoryEdits.getOrNull(i)?.text?.toString() ?: "")
                    }
                    apply()
                }
                finish()
            }
        }

        layout.addView(saveBtn)
        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
