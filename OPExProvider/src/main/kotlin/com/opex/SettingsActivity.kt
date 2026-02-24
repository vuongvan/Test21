package com.opex

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.content.Context
import android.view.ViewGroup

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(OPExProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(OPExProvider.PREF_DOMAIN, OPExProvider().mainUrl)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(20, 20, 20, 20)
        }

        val edit = EditText(this).apply {
            hint = "Domain (e.g. https://example.com)"
            setText(current)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val saveBtn = Button(this).apply {
            text = "Lưu"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val domain = edit.text.toString().trim()
                prefs.edit().putString(OPExProvider.PREF_DOMAIN, domain).apply()
                finish()
            }
        }

        layout.addView(edit)
        layout.addView(saveBtn)
        setContentView(layout)
    }
}
