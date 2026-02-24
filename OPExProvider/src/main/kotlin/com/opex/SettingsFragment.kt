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
import androidx.fragment.app.DialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast

class SettingsFragment(
    plugin: OPExPlugin,
    private val sharedPref: SharedPreferences,
) : DialogFragment() {

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val edit = EditText(ctx).apply {
            hint = "Domain (e.g. https://example.com)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(sharedPref.getString(OPExProvider.PREF_DOMAIN, OPExProvider().mainUrl))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val saveBtn = Button(ctx).apply {
            text = "Save"
            setOnClickListener {
                val domain = edit.text.toString().trim()
                if (domain.isEmpty()) {
                    showToast("Please enter a domain")
                    return@setOnClickListener
                }
                sharedPref.edit().putString(OPExProvider.PREF_DOMAIN, domain).apply()
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
                sharedPref.edit().remove(OPExProvider.PREF_DOMAIN).apply()
                edit.setText(OPExProvider().mainUrl)
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

        layout.addView(edit)
        layout.addView(saveBtn)
        layout.addView(resetBtn)
        layout.addView(closeBtn)

        return layout
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
