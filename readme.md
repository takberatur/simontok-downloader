# Video Downloader - Android Kotlin App

Android app to download videos from various platforms such as YouTube, Instagram, TikTok, Facebook, Twitter, Vimeo, Dailymotion, and Rumble.

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/agcforge/videodownloader/
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   └── Models.kt                    # Data models
│   │   │   └── api/
│   │   │       ├── ApiService.kt                # Retrofit API interface
│   │   │       ├── ApiClient.kt                 # API client builder
│   │   │       └── VideoDownloaderRepository.kt # Repository layer
│   │   │
│   │   ├── ui/
│   │   │   ├── MainActivity.kt                  # Main activity with drawer
│   │   │   ├── SplashActivity.kt                # Splash screen
│   │   │   ├── fragment/
│   │   │   │   ├── HomeFragment.kt              # Fragment home with download form
│   │   │   │   ├── DownloadsFragment.kt         # Fragment list downloads
│   │   │   │   └── SettingsFragment.kt          # Fragment settings
│   │   │   ├── adapter/
│   │   │   │   ├── PlatformAdapter.kt           # RecyclerView adapter for platform
│   │   │   │   ├── DownloadTaskAdapter.kt       # RecyclerView adapter for downloads
│   │   │   │   └── DownloadFormatAdapter.kt     # Adapter for format selection
│   │   │   └── viewmodel/
│   │   │       ├── HomeViewModel.kt             # ViewModel for home
│   │   │       ├── DownloadsViewModel.kt        # ViewModel for downloads
│   │   │       └── AuthViewModel.kt             # ViewModel for authentication
│   │   │
│   │   ├── utils/
│   │   │   ├── PreferenceManager.kt             # DataStore preferences manager
│   │   │   ├── Extensions.kt                    # Kotlin extension functions
│   │   │   ├── Resource.kt                      # Sealed class for UI state
│   │   │   └── UrlValidator.kt                  # URL validation utilities
│   │   │
│   │   └── VideoDownloaderApp.kt                # Application class
│   │
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml                # Layout main with drawer
│   │   │   ├── activity_splash.xml              # Layout splash screen
│   │   │   ├── fragment_home.xml                # Layout home fragment
│   │   │   ├── fragment_downloads.xml           # Layout downloads fragment
│   │   │   ├── fragment_settings.xml            # Layout settings fragment
│   │   │   ├── nav_header.xml                   # Navigation drawer header
│   │   │   ├── item_platform.xml                # Layout item platform
│   │   │   ├── item_download_task.xml           # Layout item download
│   │   │   └── item_download_format.xml         # Layout item format
│   │   │
│   │   ├── menu/
│   │   │   ├── bottom_nav_menu.xml              # Bottom navigation menu
│   │   │   └── drawer_menu.xml                  # Drawer navigation menu
│   │   │
│   │   ├── navigation/
│   │   │   └── nav_graph.xml                    # Navigation graph
│   │   │
│   │   ├── drawable/
│   │   │   ├── bg_platform_tag.xml              # Background for tag platform
│   │   │   ├── ic_home.xml                      # Icon home
│   │   │   ├── ic_download.xml                  # Icon download
│   │   │   ├── ic_settings.xml                  # Icon settings
│   │   │   └── ic_link.xml                      # Icon link
│   │   │
│   │   ├── values/
│   │   │   ├── strings.xml                      # String resources
│   │   │   ├── colors.xml                       # Color resources
│   │   │   └── themes.xml                       # App themes
│   │   │
│   │   └── xml/
│   │       ├── file_paths.xml                   # FileProvider paths
│   │       ├── backup_rules.xml                 # Backup rules
│   │       └── data_extraction_rules.xml        # Data extraction rules
│   │
│   └── AndroidManifest.xml                      # App manifest
│
└── build.gradle.kts                             # Gradle build file
```

## 🚀 Fitur Utama

### 1. **Modern UI/UX**
- Material Design 3
- Splash screen with SplashScreen API
- Bottom navigation
- Navigation drawer
- Swipe to refresh
- Loading indicators

### 2. **Download Manager**
- Support multiple platforms (YouTube, Instagram, TikTok, dll)
- Format selection (kualitas video)
- Download history
- Error handling

### 3. **Architecture**
- MVVM (Model-View-ViewModel)
- Repository pattern
- Kotlin Coroutines for async operations
- StateFlow for reactive UI
- DataStore for preferences

### 4. **Network Layer**
- Retrofit for REST API
- OkHttp with interceptors
- Logging interceptor for debugging
- Authentication with Bearer token

### 5. **1i18n Support**
- Multiple languages support
- String resources for easy localization
- Dynamic text updates
- Support RTL languages
- Locale selection in settings
- Date and number formatting based on locale
- Language fallback mechanism

## 📦 Dependencies Utama

```kotlin
// Core
androidx.core:core-ktx
androidx.appcompat:appcompat
com.google.android.material:material

// Navigation
androidx.navigation:navigation-fragment-ktx
androidx.navigation:navigation-ui-ktx

// Lifecycle & ViewModel
androidx.lifecycle:lifecycle-viewmodel-ktx
androidx.lifecycle:lifecycle-livedata-ktx

// Coroutines
kotlinx-coroutines-android

// Network
com.squareup.retrofit2:retrofit
com.squareup.retrofit2:converter-gson
com.squareup.okhttp3:okhttp
com.squareup.okhttp3:logging-interceptor

// Image Loading
com.github.bumptech.glide:glide

// DataStore
androidx.datastore:datastore-preferences

// SplashScreen
androidx.core:core-splashscreen
```

## 🔧 Setup & Konfigurasi

### 1. Clone & Build
```bash
git clone <repository-url>
cd VideoDownloader
./gradlew build
```

### 2. Konfigurasi API
Edit `ApiClient.kt` for mengubah BASE_URL if needed:
```kotlin
private const val BASE_URL = "https://your-end-point-api.com/"
```

### 3. Build & Run
```bash
./gradlew installDebug
```

## 🔌 API Integration

### Endpoints Used

#### **1. Get Platforms**
```
GET /platforms
Response: List<Platform>
```

#### **2. Create Download**
```
POST /downloads
Body: {
  "url": "string",
  "platform_id": "uuid" (optional),
  "format": "string" (optional)
}
Response: DownloadTask
```

#### **3. Get Downloads**
```
GET /downloads?page=1&limit=20
Response: List<DownloadTask>
```

#### **4. Get Download Detail**
```
GET /downloads/{id}
Response: DownloadTask
```

#### **5. Authentication**
```
POST /auth/login
Body: {
  "email": "string",
  "password": "string"
}
Response: {
  "token": "string",
  "user": User
}
```

### Authentication
Every request that requires authentication will include the header:
```
Authorization: Bearer {token}
```

## 📱 Cara Penggunaan

### Download Video
1. Open the app
2. Paste the video URL on the Home page
3. Click the "Download" button
4. Select the quality if available
5. The video will begin downloading

### Viewing History
1. Open the "Downloads" tab
2. View all previously downloaded videos
3. Click an item to view details
4. Click the download button to access the file

### Settings
1. Open the "Settings" tab
2. Set the default download quality
3. Change the save location
4. Clear the cache if necessary

## 🛠 Customization

### Adding a New Platform
1. Add an icon in `res/drawable/`
2. The platform will automatically appear in the API response.
3. No code modifications required.

### Changing the Theme
Edit `res/values/themes.xml`:
```xml
<item name="colorPrimary">@color/your_color</item>
<item name="colorPrimaryDark">@color/your_color</item>
<item name="colorAccent">@color/your_color</item>
```

### Adding a New Fragment
1. Create a fragment class in `ui/fragment/`
2. Create a layout in `res/layout/`
3. Add it to `nav_graph.xml`
4. Update the menu if necessary.

## 🐛 Troubleshooting

### Network Error
- Ensure internet connection
- Check BASE_URL is correct
- Verify backend API is working Running

### Build Error
- Clean project: `./gradlew clean`
- Sync Gradle files
- Invalidate caches and restart Android Studio

### Download Not Working
- Check permissions in AndroidManifest
- Verify URL is valid
- Check API response in Logcat
## 📄 License

Copyright © 2026 AGCForge. All rights reserved.

## 👨‍💻 Developer Notes

### Best Practices Used
1. **Separation of Concerns** - Separate data, UI, and business logic
2. **Reactive Programming** - Use Flow for reactive updates
3. **Error Handling** - Comprehensive error handling across all layers
4. **Resource Management** - Proper lifecycle management for fragments
5. **Type Safety** - Sealed classes for UI states

### Next Steps
- [ ] Implement download progress tracking
- [ ] Add offline support with Room database
- [ ] Implement WorkManager for background downloads
- [ ] Add video player integration
- [ ] Implement analytics
- [ ] Add crash reporting
- [ ] Implement share functionality
- [ ] Add notification for download completion

### Contact & Support
For questions or support, contact: support@agcforge.com