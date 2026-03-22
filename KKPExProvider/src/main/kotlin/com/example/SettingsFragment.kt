package com.example

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.preference.*
import com.lagradost.cloudstream3.CommonActivity.showToast

class SettingsFragment(
    private val plugin: KKPExPlugin,
    private val sharedPref: SharedPreferences,
) : DialogFragment() {

    // Giao diện cài đặt chính
    class PreferenceInside : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)

            // --- PHẦN 1: CẤU HÌNH DOMAIN ---
            val domainCat = PreferenceCategory(context).apply { title = "🌐 Cấu hình Domain" }
            screen.addPreference(domainCat)

            val activeDomainIdx = ListPreference(context).apply {
                key = "selected_domain_index"
                title = "Chọn Domain đang hoạt động"
                summary = "Đang sử dụng: %s"
                entries = arrayOf("Domain 1", "Domain 2", "Domain 3", "Domain 4", "Domain 5", "Domain 6")
                entryValues = arrayOf("0", "1", "2", "3", "4", "5")
                setDefaultValue("0")
            }
            domainCat.addPreference(activeDomainIdx)

            for (i in 0..5) {
                val domainEdit = EditTextPreference(context).apply {
                    key = "custom_domain_$i"
                    title = "Chỉnh sửa Domain ${i + 1}"
                    summary = "URL: %s"
                    dialogTitle = "Nhập URL cho Domain ${i + 1}"
                    setDefaultValue(if (i == 0) "https://ophim1.com" else "https://domain${i+1}.com")
                }
                domainCat.addPreference(domainEdit)
            }

            // --- PHẦN 2: CẤU HÌNH DANH SÁCH PHIM ---
            val movieCat = PreferenceCategory(context).apply { title = "⚙️ Cấu hình Danh Sách Phim" }
            screen.addPreference(movieCat)

            val defaultPaths = listOf("danh-sach/phim-moi-cap-nhat-v3", "v1/api/quoc-gia/trung-quoc", "v1/api/quoc-gia/han-quoc", "v1/api/danh-sach/hoat-hinh", "", "")
            val defaultNames = listOf("Mới cập nhật", "Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")

            for (i in 1..6) {
                // Tạo một màn hình con cho mỗi danh sách (nhấn vào mới hiện chỉnh sửa)
                val subScreen = preferenceManager.createPreferenceScreen(context).apply {
                    key = "sub_screen_$i"
                    title = "Danh sách $i: ${preferenceManager.sharedPreferences?.getString("cat_name_$i", defaultNames[i-1])}"
                    summary = "Nhấn để đổi tên và đường dẫn API"
                }

                val nameEdit = EditTextPreference(context).apply {
                    key = "cat_name_$i"
                    title = "Tên hiển thị"
                    summary = "Hiện tại: %s"
                    setDefaultValue(defaultNames[i-1])
                    setOnPreferenceChangeListener { _, newValue ->
                        subScreen.title = "Danh sách $i: $newValue"
                        true
                    }
                }

                val pathEdit = EditTextPreference(context).apply {
                    key = "cat_path_$i"
                    title = "Đường dẫn API"
                    summary = "Hiện tại: %s"
                    setDefaultValue(defaultPaths[i-1])
                }

                subScreen.addPreference(nameEdit)
                subScreen.addPreference(pathEdit)
                movieCat.addPreference(subScreen)
            }

            // --- PHẦN 3: THAO TÁC ---
            val actionCat = PreferenceCategory(context).apply { title = "Thao tác" }
            screen.addPreference(actionCat)

            actionCat.addPreference(Preference(context).apply {
                title = "💾 Lưu & Khởi động lại"
                setOnPreferenceClickListener {
                    (parentFragment as? SettingsFragment)?.showRestartDialog()
                    true
                }
            })

            actionCat.addPreference(Preference(context).apply {
                title = "🔄 Đặt lại mặc định"
                setOnPreferenceClickListener {
                    preferenceManager.sharedPreferences?.edit()?.clear()?.apply()
                    showToast("Đã đặt lại. Vui lòng khởi động lại app.")
                    (parentFragment as? SettingsFragment)?.restartApp()
                    true
                }
            })

            preferenceScreen = screen
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = FrameLayout(requireContext()).apply {
            id = View.generateViewId()
        }
        childFragmentManager.beginTransaction().replace(root.id, PreferenceInside()).commit()
        return root
    }

    fun showRestartDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Lưu cấu hình")
            .setMessage("Cấu hình đã tự động lưu. Khởi động lại ứng dụng để áp dụng?")
            .setPositiveButton("Có") { _, _ -> restartApp() }
            .setNegativeButton("Không", null)
            .show()
    }

    fun restartApp() {
        val ctx = requireContext().applicationContext
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val restartIntent = Intent.makeRestartActivityTask(intent?.component)
        ctx.startActivity(restartIntent)
        Runtime.getRuntime().exit(0)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
