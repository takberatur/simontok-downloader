# Video Downloader - Android Kotlin App

Aplikasi Android untuk mendownload video dari berbagai platform seperti YouTube, Instagram, TikTok, Facebook, Twitter, Vimeo, Dailymotion, dan Rumble.

## 📁 Struktur Project

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
│   │   │   ├── MainActivity.kt                  # Main activity dengan drawer
│   │   │   ├── SplashActivity.kt                # Splash screen
│   │   │   ├── fragment/
│   │   │   │   ├── HomeFragment.kt              # Fragment home dengan download form
│   │   │   │   ├── DownloadsFragment.kt         # Fragment daftar downloads
│   │   │   │   └── SettingsFragment.kt          # Fragment settings
│   │   │   ├── adapter/
│   │   │   │   ├── PlatformAdapter.kt           # RecyclerView adapter untuk platform
│   │   │   │   ├── DownloadTaskAdapter.kt       # RecyclerView adapter untuk downloads
│   │   │   │   └── DownloadFormatAdapter.kt     # Adapter untuk format selection
│   │   │   └── viewmodel/
│   │   │       ├── HomeViewModel.kt             # ViewModel untuk home
│   │   │       ├── DownloadsViewModel.kt        # ViewModel untuk downloads
│   │   │       └── AuthViewModel.kt             # ViewModel untuk authentication
│   │   │
│   │   ├── utils/
│   │   │   ├── PreferenceManager.kt             # DataStore preferences manager
│   │   │   ├── Extensions.kt                    # Kotlin extension functions
│   │   │   ├── Resource.kt                      # Sealed class untuk UI state
│   │   │   └── UrlValidator.kt                  # URL validation utilities
│   │   │
│   │   └── VideoDownloaderApp.kt                # Application class
│   │
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml                # Layout main dengan drawer
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
│   │   │   ├── bg_platform_tag.xml              # Background untuk tag platform
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
- Splash screen dengan SplashScreen API
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
- Kotlin Coroutines untuk async operations
- StateFlow untuk reactive UI
- DataStore untuk preferences

### 4. **Network Layer**
- Retrofit untuk REST API
- OkHttp dengan interceptors
- Logging interceptor untuk debugging
- Authentication dengan Bearer token

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
Edit `ApiClient.kt` untuk mengubah BASE_URL jika diperlukan:
```kotlin
private const val BASE_URL = "https://api-simontok.agcforge.com/"
```

### 3. Build & Run
```bash
./gradlew installDebug
```

## 🔌 API Integration

### Endpoints yang Digunakan

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
Setiap request yang memerlukan autentikasi akan menyertakan header:
```
Authorization: Bearer {token}
```

## 📱 Cara Penggunaan

### Download Video
1. Buka aplikasi
2. Paste URL video di halaman Home
3. Klik tombol "Download"
4. Pilih kualitas jika tersedia
5. Video akan mulai didownload

### Melihat History
1. Buka tab "Downloads"
2. Lihat semua video yang pernah didownload
3. Klik item untuk melihat detail
4. Klik tombol download untuk mengakses file

### Settings
1. Buka tab "Settings"
2. Atur kualitas default download
3. Ubah lokasi penyimpanan
4. Clear cache jika diperlukan

## 🛠 Customization

### Menambah Platform Baru
1. Tambahkan icon di `res/drawable/`
2. Platform akan otomatis muncul dari API response
3. Tidak perlu modifikasi code

### Mengubah Tema
Edit `res/values/themes.xml`:
```xml
<item name="colorPrimary">@color/your_color</item>
<item name="colorPrimaryDark">@color/your_color</item>
<item name="colorAccent">@color/your_color</item>
```

### Menambah Fragment Baru
1. Buat fragment class di `ui/fragment/`
2. Buat layout di `res/layout/`
3. Tambahkan ke `nav_graph.xml`
4. Update menu jika diperlukan

## 🐛 Troubleshooting

### Network Error
- Pastikan internet tersambung
- Check BASE_URL sudah benar
- Verifikasi API backend sudah running

### Build Error
- Clean project: `./gradlew clean`
- Sync gradle files
- Invalidate caches and restart Android Studio

### Download Tidak Berfungsi
- Cek permission di AndroidManifest
- Verifikasi URL valid
- Check API response di Logcat

## 📄 License

Copyright © 2026 AGCForge. All rights reserved.

## 👨‍💻 Developer Notes

### Best Practices yang Digunakan
1. **Separation of Concerns** - Data, UI, dan Business Logic terpisah
2. **Reactive Programming** - Menggunakan Flow untuk reactive updates
3. **Error Handling** - Comprehensive error handling di semua layer
4. **Resource Management** - Proper lifecycle management untuk fragments
5. **Type Safety** - Sealed classes untuk UI states

### Next Steps
- [ ] Implement download progress tracking
- [ ] Add offline support dengan Room database
- [ ] Implement WorkManager untuk background downloads
- [ ] Add video player integration
- [ ] Implement analytics
- [ ] Add crash reporting
- [ ] Implement share functionality
- [ ] Add notification for download completion

### Contact & Support
Untuk pertanyaan atau support, hubungi: support@agcforge.com