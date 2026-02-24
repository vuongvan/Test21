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
        val currentUsername = prefs.getString(KKPExProvider.PREF_USERNAME, "")
        val currentPassword = prefs.getString(KKPExProvider.PREF_PASSWORD, "")

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

        val usernameLabel = TextView(this).apply {
            text = "Tên đăng nhập"
            textSize = 16f
            setPadding(0, 20, 0, 10)
        }

        val usernameEdit = EditText(this).apply {
            hint = "Tên đăng nhập hoặc Email"
            setText(currentUsername)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(10, 10, 10, 10)
        }

        val passwordLabel = TextView(this).apply {
            text = "Mật khẩu"
            textSize = 16f
            setPadding(0, 20, 0, 10)
        }

        val passwordEdit = EditText(this).apply {
            hint = "Mật khẩu"
            setText(currentPassword)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(10, 10, 10, 10)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        // Category settings
        val categoryTitleLabel = TextView(this).apply {
            text = "⚙️ Cài Đặt Danh Sách Phim"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        }

        val categoryDescLabel = TextView(this).apply {
            text = "Nhập đường dẫn API sau domain, ví dụ: quoc-gia/trung-quoc, v1/api/phim-bo, v.v. Để trống để bỏ qua danh sách này."
            textSize = 12f
            setPadding(0, 0, 0, 15)
        }

        val categoryEdits = mutableListOf<EditText>()
        for (i in 1..6) {
            val categoryLabel = TextView(this).apply {
                text = "Danh Sách $i:"
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

            val categoryEdit = EditText(this).apply {
                hint = "Ví dụ: quoc-gia/han-quoc hoặc v1/api/phim-le"
                setText(prefs.getString(preKey, ""))
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
                val username = usernameEdit.text.toString().trim()
                val password = passwordEdit.text.toString().trim()
                
                if (domain.isEmpty()) {
                    Toast.makeText(this@SettingsActivity, "Domain không thể trống", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this@SettingsActivity, "Tên đăng nhập và mật khẩu không thể trống", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                prefs.edit().apply {
                    putString(KKPExProvider.PREF_DOMAIN, domain)
                    putString(KKPExProvider.PREF_USERNAME, username)
                    putString(KKPExProvider.PREF_PASSWORD, password)
                    putBoolean(KKPExProvider.PREF_IS_LOGGED_IN, true)
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
        layout.addView(usernameLabel)
        layout.addView(usernameEdit)
        layout.addView(passwordLabel)
        layout.addView(passwordEdit)
        layout.addView(categoryTitleLabel)
        layout.addView(categoryDescLabel)
        layout.addView(saveBtn)

        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
