package com.opex

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast

class SettingsFragment(
    plugin: OPExPlugin,
    private val sharedPref: SharedPreferences,
) : DialogFragment() {

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ctx = requireContext()

        val scrollView = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val domainEdit = EditText(ctx).apply {
            hint = "Domain (e.g. https://example.com)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(sharedPref.getString(OPExProvider.PREF_DOMAIN, OPExProvider().mainUrl))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Category lists for labels and default names
        val categoryLabels = listOf("Category 1", "Category 2", "Category 3", "Category 4", "Category 5", "Category 6")
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
        val domainLabel = TextView(ctx).apply {
            text = "Domain:"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
        }
        layout.addView(domainLabel)
        layout.addView(domainEdit)

        // Add category sections (6 categories)
        for (i in 1..6) {
            val categoryHeading = TextView(ctx).apply {
                text = "Category $i:"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (16 * resources.displayMetrics.density).toInt()
                }
            }

            val categoryNameLabel = TextView(ctx).apply {
                text = "Display Name:"
                textSize = 12f
            }

            val categoryNameEdit = EditText(ctx).apply {
                hint = "Display name"
                val savedName = sharedPref.getString(OPExProvider.getPreferenceNameKey(i), categoryNames.getOrNull(i - 1) ?: "Danh Sách $i")
                setText(savedName)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val categoryPathLabel = TextView(ctx).apply {
                text = "API Path:"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (8 * resources.displayMetrics.density).toInt()
                }
            }

            val categoryEdit = EditText(ctx).apply {
                hint = "API path (e.g. v1/api/danh-sach/phim-le)"
                val savedPath = sharedPref.getString(OPExProvider.getPreferenceKey(i), defaultPaths.getOrNull(i - 1) ?: "")
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

        val saveBtn = Button(ctx).apply {
            text = "Save"
            setOnClickListener {
                val domain = domainEdit.text.toString().trim()
                sharedPref.edit().apply {
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
                showToast("Saved")
                AlertDialog.Builder(ctx)
                    .setTitle("Save & Restart")
                    .setMessage("Changes saved. Restart app to apply?")
                    .setPositiveButton("Yes") { _, _ ->
                        dismiss()
                        restartApp()
                    }
                    .setNegativeButton("No") { _, _ -> dismiss() }
                    .show()
            }
        }

        val resetBtn = Button(ctx).apply {
            text = "Reset"
            setOnClickListener {
                sharedPref.edit().apply {
                    remove(OPExProvider.PREF_DOMAIN)
                    for (i in 1..6) {
                        remove(OPExProvider.getPreferenceKey(i))
                        remove(OPExProvider.getPreferenceNameKey(i))
                    }
                    apply()
                }
                // Reset UI
                domainEdit.setText(OPExProvider().mainUrl)
                for (i in 0 until 6) {
                    categoryNameEdits.getOrNull(i)?.setText(categoryNames.getOrNull(i) ?: "Danh Sách ${i + 1}")
                    categoryEdits.getOrNull(i)?.setText(defaultPaths.getOrNull(i) ?: "")
                }
                showToast("Reset")
                AlertDialog.Builder(ctx)
                    .setTitle("Reset & Restart")
                    .setMessage("Reset completed. Restart app to apply?")
                    .setPositiveButton("Yes") { _, _ ->
                        dismiss()
                        restartApp()
                    }
                    .setNegativeButton("No") { _, _ -> dismiss() }
                    .show()
            }
        }

        val closeBtn = ImageView(ctx).apply {
            setOnClickListener { dismiss() }
        }

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
