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

    class PreferenceInside : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)

            // --- PHẦN 1: CẤU HÌNH DOMAIN (CHỈ 1 DOMAIN) ---
            val domainCat = PreferenceCategory(context).apply { title = "🌐 Cấu hình Nguồn Phim" }
            screen.addPreference(domainCat)

            val domainEdit = EditTextPreference(context).apply {
                key = "kkpex_domain" // Key này phải khớp với key trong Provider của bạn
                title = "Chỉnh sửa Domain"
                summary = "Hiện tại: %s"
                dialogTitle = "Nhập URL (vd: https://ophim1.com)"
                setDefaultValue("https://ophim1.com")
            }
            domainCat.addPreference(domainEdit)

            // --- PHẦN 2: CẤU HÌNH DANH SÁCH PHIM ---
            val movieCat = PreferenceCategory(context).apply { title = "⚙️ Cấu hình Danh Sách Phim" }
            screen.addPreference(movieCat)

            val defaultPaths = listOf("danh-sach/phim-moi-cap-nhat-v3", "v1/api/quoc-gia/trung-quoc", "v1/api/quoc-gia/han-quoc", "v1/api/danh-sach/hoat-hinh", "", "")
            val defaultNames = listOf("Mới cập nhật", "Phim Trung Quốc", "Phim Hàn Quốc", "Phim Hoạt Hình", "Danh Sách 5", "Danh Sách 6")

            for (i in 1..6) {
                // Tiêu đề nhóm cho từng danh sách
                val groupCat = PreferenceCategory(context).apply { 
                    title = "Danh sách $i"
                }
                screen.addPreference(groupCat)

                // Chỉnh sửa tên - Nhấn vào sẽ hiện hộp thoại nhập
                val nameEdit = EditTextPreference(context).apply {
                    key = "cat_name_$i"
                    title = "  Tên hiển thị $i"
                    summary = "Đang đặt là: %s"
                    dialogTitle = "Nhập tên cho danh sách $i"
                    setDefaultValue(defaultNames[i-1])
                }

                // Chỉnh sửa đường dẫn - Nhấn vào sẽ hiện hộp thoại nhập
                val pathEdit = EditTextPreference(context).apply {
                    key = "cat_path_$i"
                    title = "  Đường dẫn API $i"
                    summary = "Đang chạy: %s"
                    dialogTitle = "Nhập path (vd: v1/api/phim-bo)"
                    setDefaultValue(defaultPaths[i-1])
                }

                groupCat.addPreference(nameEdit)
                groupCat.addPreference(pathEdit)
            }

            // --- PHẦN 3: THAO TÁC ---
            val actionCat = PreferenceCategory(context).apply { title = "Hệ thống" }
            screen.addPreference(actionCat)

            actionCat.addPreference(Preference(context).apply {
                title = "💾 Lưu & Khởi động lại"
                summary = "Nhấn để áp dụng các thay đổi"
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
        val root = FrameLayout(requireContext()).apply { id = View.generateViewId() }
        childFragmentManager.beginTransaction().replace(root.id, PreferenceInside()).commit()
        return root
    }

    fun showRestartDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Xác nhận")
            .setMessage("Thay đổi sẽ có hiệu lực sau khi khởi động lại. Tiếp tục?")
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
