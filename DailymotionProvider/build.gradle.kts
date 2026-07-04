// Use an integer for version numbers
version = 10

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "Watch content from Dailymotion"
    authors = listOf("Luna712")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Others")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.dailymotion.com&sz=%size%"

    // [FIX] Đã xóa "isCrossPlatform = true"
    // Flag này bắt buộc module build ở chế độ không phụ thuộc Android,
    // khiến toàn bộ API Android (DialogFragment, AppCompatActivity, Context,
    // androidx.fragment...) bị loại khỏi classpath -> gây lỗi "Unresolved reference"
    // dù code và import hoàn toàn đúng.
    // Bỏ dòng này để dùng được Plugin (có openSettings) giống OPExProvider.
}
