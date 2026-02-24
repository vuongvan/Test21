package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.content.Context
import android.view.ViewGroup

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(KKPExProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val currentDomain = prefs.getString(KKPExProvider.PREF_DOMAIN, KKPExProvider().mainUrl)
        val currentUsername = prefs.getString(KKPExProvider.PREF_USERNAME, "")
        val currentPassword = prefs.getString(KKPExProvider.PREF_PASSWORD, "")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(20, 20, 20, 20)
        }

        val domainLabel = android.widget.TextView(this).apply {
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

        val usernameLabel = android.widget.TextView(this).apply {
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

        val passwordLabel = android.widget.TextView(this).apply {
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
        layout.addView(saveBtn)
        setContentView(layout)
    }
}
