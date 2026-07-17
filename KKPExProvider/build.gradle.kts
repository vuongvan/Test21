dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
}

// Use an integer for version numbers
// Use an integer for version numbers
version = 14

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "Movies"
    authors = listOf("VM", "Gemini")

    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Movie")
    language = "vi"

    // Random CC logo I found
    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}
