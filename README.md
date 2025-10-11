<p align="center">
  <img src="https://raw.githubusercontent.com/Afihacked/AfitechTok/master/app/src/main/res/mipmap-xxhdpi/ic_launcher.webp" alt="AfitechTok Logo" width="120" height="120" />
</p>

<h1 align="center">AfitechTok</h1>

<p align="center">
  <b>Downloader TikTok & WhatsApp Story Tanpa Watermark — Cepat, Bersih, dan Aman</b>
</p>

<p align="center">
  <a href="https://github.com/Afihacked/AfitechTok/releases">
    <img src="https://img.shields.io/github/v/release/Afihacked/AfitechTok?color=brightgreen&label=versi" alt="Release Version">
  </a>
  <a href="https://github.com/Afihacked/AfitechTok/issues">
    <img src="https://img.shields.io/github/issues/Afihacked/AfitechTok?color=yellow" alt="Issues">
  </a>
  <a href="#">
    <img src="https://img.shields.io/github/stars/Afihacked/AfitechTok?color=orange" alt="Stars">
  </a>
  <img src="https://img.shields.io/badge/Made%20with-Kotlin-blue?logo=kotlin" alt="Kotlin Badge">
  <img src="https://img.shields.io/github/license/Afihacked/AfitechTok?color=blue" alt="License Badge">
</p>

---

## 🧩 Tentang Aplikasi

**AfitechTok** adalah aplikasi Android buatan **Afihacked** untuk mengunduh **video, musik, dan gambar dari TikTok tanpa watermark**, serta **story WhatsApp** secara langsung.  
Setiap hasil unduhan tersimpan otomatis ke **Riwayat Unduhan** yang rapi dan mudah diakses.

🎯 Fokus utama:
- Cepat dan ringan  
- Tidak butuh login  
- Bebas watermark  
- Gratis 100%  
- UI bersih dan mudah dipahami  

---

## ✨ Fitur Utama

| Kategori | Deskripsi |
|-----------|------------|
| 🎬 **Downloader TikTok** | Unduh video, musik, dan slide gambar tanpa watermark |
| 💬 **Downloader WA Story** | Unduh story (status) WhatsApp teman secara otomatis |
| 📋 **Auto Paste Link** | Deteksi link TikTok otomatis dari clipboard |
| 🗂 **Riwayat Unduhan** | Simpan semua hasil unduhan dengan metadata dan waktu |
| 🎞 **Preview Gambar Slide** | Pilih gambar tertentu sebelum diunduh |
| 📢 **Iklan Aman** | Menggunakan AdMob + Start.io dengan fallback otomatis |
| 📱 **UI Modern** | Material Design, responsif, dan ringan |
| 💾 **Dukungan Storage Modern** | Kompatibel dengan Scoped Storage & Android 13+ |

---

## 🧱 Arsitektur Proyek

Proyek ini menggunakan **MVVM (Model-View-ViewModel)** dan arsitektur modular untuk kemudahan maintenance.

```
AfitechTok/
 ├─ app/
 │   ├─ data/
 │   │   ├─ database/         # Room DB, DAO, dan entitas riwayat unduhan
 │   │   └─ model/            # Model data (History, MediaInfo, dsb)
 │   ├─ ui/
 │   │   ├─ fragments/        # DownloadFragmentTT, StoryFragment, dll
 │   │   ├─ viewmodel/        # TiktokViewModel, StoryViewModel
 │   │   ├─ services/         # DownloadServiceTT (ForegroundService)
 │   │   └─ adapters/         # RecyclerView Adapters
 │   ├─ utils/                # Helper (AdsManager, Extensions, Validation)
 │   └─ res/                  # Layout, Drawable, String, Style
 └─ build.gradle.kts
```

---

## ⚙️ Teknologi yang Digunakan

| Komponen | Library / Framework |
|-----------|--------------------|
| Bahasa | **Kotlin** |
| Arsitektur | **MVVM**, ViewModel, LiveData |
| Database | **Room** (AppDatabase, DAO) |
| UI | **Material Components**, RecyclerView, Shimmer |
| Gambar | **Glide** |
| Iklan | **AdMob**, **Start.io** |
| Asinkron | **Kotlin Coroutines**, LifecycleScope |
| Analytics | **Firebase Analytics** |
| Build | **Gradle Kotlin DSL** |
| Minimum SDK | **Android Q/10.0 (API 29)** |

---

## 🧰 Instalasi & Build

### Prasyarat
- Android Studio Narwhal 3 Feature Drop (2025.1.3)**  
- JDK **17 atau lebih baru**  
- Internet aktif untuk sinkronisasi Gradle  

### Cara Build Manual
```bash
git clone https://github.com/Afihacked/AfitechTok.git
cd AfitechTok
./gradlew assembleDebug
```
Atau buka project di **Android Studio**:
```
File → Open → pilih folder AfitechTok → Run ▶️
```

---

## 📦 Struktur Fitur

| File Utama | Deskripsi |
|-------------|------------|
| `DownloadFragmentTT.kt` | UI utama untuk unduh video, musik, atau gambar dari TikTok |
| `DownloadServiceTT.kt` | Service foreground untuk proses download di background |
| `TiktokViewModel.kt` | Validasi dan parsing link TikTok |
| `AppDatabase.kt` | Database Room untuk riwayat unduhan |
| `AdsManager.kt` | Pengelolaan iklan (AdMob + Start.io fallback) |
| `StoryFragment.kt` | Downloader untuk status/story WhatsApp |

---

## 🔒 Keamanan & Kebijakan

> ⚠️ **Disclaimer Penting:**  
> AfitechTok tidak berafiliasi dengan TikTok maupun WhatsApp.  
> Semua konten yang diunduh sepenuhnya menjadi tanggung jawab pengguna.  
> Gunakan hanya untuk keperluan pribadi, bukan untuk distribusi ulang tanpa izin.  
> Aplikasi ini tidak menyimpan data pengguna secara eksternal dan mematuhi kebijakan privasi Android.

---

## 🧑‍💻 Kontribusi

Kontribusi sangat diterima!

1. Fork repositori ini  
2. Buat branch baru:  
   ```bash
   git checkout -b fitur/fitur-baru
   ```  
3. Lakukan perubahan, lalu commit:
   ```bash
   git commit -m "Tambah fitur baru"
   ```
4. Push branch:
   ```bash
   git push origin fitur/fitur-baru
   ```
5. Buat Pull Request 🎉

---

## 🧾 Lisensi

Proyek ini dilisensikan di bawah lisensi **MIT** — Anda bebas menggunakan, memodifikasi, dan menyebarkan ulang dengan mencantumkan atribusi.

```
MIT License © 2025 Afihacked
```

---

<p align="center">
  <img src="https://img.shields.io/badge/Made%20with%20❤️%20by-Afihacked-blue" alt="Made by Afihacked">
</p>

<p align="center">
  <a href="https://github.com/Afihacked">GitHub</a> • •
  <a href="mailto:afitech.services@gmail.com">Email</a>
</p>
