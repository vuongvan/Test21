package com.example

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.preference.*
import com.lagradost.cloudstream3.CommonActivity.showToast

class SettingsFragment(
    private val plugin: KKPExPlugin,
    private val sharedPref: SharedPreferences,
) : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // --- 1. CẤU HÌNH DOMAIN ---
        val domainCat = PreferenceCategory(context).apply {
            title = "🌐 Cấu hình Domain (Nguồn Phim)"
        }
        screen.addPreference(domainCat)

        // Chọn domain hoạt động
        val activeDomainIdx = ListPreference(context).apply {
            key = "selected_domain_index"
            title = "Chọn Domain đang dùng"
            summary = "Đang sử dụng: %s"
            entries = arrayOf("Domain 1", "Domain 2", "Domain 3", "Domain 4", "Domain 5", "Domain 6")
            entryValues = arrayOf("0", "1", "2", "3", "4", "5")
            setDefaultValue("0")
        }
        domainCat.addPreference(activeDomainIdx)

        // Danh sách 6 Domain để chỉnh sửa
        for (i in 0..5) {
            val domainEdit = EditTextPreference(context).apply {
                key = "custom_domain_$i"
                title = "Chỉnh sửa Domain ${i + 1}"
                summary = "URL: %s"
                dialogTitle = "Nhập URL cho Domain ${i + 1}"
                setDefaultValue(if (i == 0) KKPExProvider().mainUrl else "https://domain${i+1}.com")
            }
            domainCat.addPreference(domainEdit)
        }

        // --- 2. CẤU HÌNH DANH SÁCH PHIM (CATEGORY) ---
        val movieCat = PreferenceCategory(context).apply {
            title = "⚙️ Cấu hình Danh Sách Phim"
        }
        screen.addPreference(movieCat)

        val defaultPaths = listOf("danh-sach/phim-moi-cap-nhat-v3", "v1/api/quoc-gia/trung-quoc", "v1/api/quoc-gia/han-quoc", "v1/api/danh-sach/hoat-hinh", "", "")
        val defaultNames = listOf("Mới cập nhật", "Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")

        for (i in 1..6) {
            // Dùng PreferenceScreen con để gom nhóm Name và Path của từng danh sách
            val subScreen = preferenceManager.createPreferenceScreen(context).apply {
                key = "sub_category_$i"
                title = "Danh sách $i: ${sharedPref.getString("cat_name_$i", defaultNames[i-1])}"
                summary = "Nhấn để đổi tên và đường dẫn API"
            }

            val nameEdit = EditTextPreference(context).apply {
                key = "cat_name_$i"
                title = "Tên hiển thị"
                summary = "Hiện tại: %s"
                setDefaultValue(defaultNames[i-1])
            }

            val pathEdit = EditTextPreference(context).apply {
                key = "cat_path_$i"
                title = "Đường dẫn API"
                summary = "Hiện tại: %s"
                setDefaultValue(defaultPaths[i-1])
            }

            // Khi đổi tên ở trong, cập nhật luôn tiêu đề ở ngoài
            nameEdit.setOnPreferenceChangeListener { _, newValue ->
                subScreen.title = "Danh sách $i: $newValue"
                true
            }

            subScreen.addPreference(nameEdit)
            subScreen.addPreference(pathEdit)
            movieCat.addPreference(subScreen)
        }

        // --- 3. NÚT THAO TÁC (LƯU/RESET) ---
        val actionCat = PreferenceCategory(context).apply { title = "Thao tác" }
        screen.addPreference(actionCat)

        // Nút Lưu & Khởi động lại
        val savePref = Preference(context).apply {
            title = "💾 Lưu & Khởi động lại"
            summary = "Áp dụng thay đổi ngay lập tức"
            setOnPreferenceClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Xác nhận")
                    .setMessage("Bạn có muốn khởi động lại ứng dụng để áp dụng cấu hình mới?")
                    .setPositiveButton("Có") { _, _ -> restartApp() }
                    .setNegativeButton("Không", null)
                    .show()
                true
            }
        }
        actionCat.addPreference(savePref)

        // Nút Reset
        val resetPref = Preference(context).apply {
            title = "🔄 Đặt lại mặc định"
            summary = "Xóa toàn bộ tùy chỉnh về ban đầu"
            setOnPreferenceClickListener {
                sharedPref.edit().clear().apply()
                showToast("Đã xóa toàn bộ cài đặt. Vui lòng khởi động lại.")
                restartApp()
                true
            }
        }
        actionCat.addPreference(resetPref)

        preferenceScreen = screen
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
