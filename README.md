# MH Tour V5.3 — UI Refresh + Donasi + Back + Session

Versi ini tidak lagi mewajibkan hotspot `192.168.43.1` dan tidak memaksa Wi-Fi-only. HP Guide dan HP Jemaah dapat memakai Wi-Fi atau data seluler operator masing-masing, selama keduanya dapat mencapai server MH Tour melalui internet.

## Cara kerja

```text
HP Guide (Wi-Fi / Data Seluler) ─┐
                                ├── INTERNET ──> MH TOUR API + LIVEKIT ──> INTERNET ──> HP Jemaah
HP Jemaah (Wi-Fi / Data Seluler)┘
```

## AUTO ONLINE / LiveKit

Build Android ini mempertahankan koneksi LiveKit melalui Development Token Server ID `mhtour-19kg8e` yang sudah digunakan pada versi sebelumnya. Karena itu workflow tidak lagi memaksa repository variable `MH_TOUR_API_BASE_URL` yang tidak dipakai oleh `MainActivity.kt`.

Untuk penggunaan server produksi sendiri, folder `server/` tetap disertakan dan dapat dijalankan terpisah. Jangan menghapus konfigurasi LiveKit yang sudah ada.

## Build di GitHub

1. Upload isi ZIP ke root repository.
2. Pastikan folder `.github/workflows/` ikut ter-upload.
3. Buka **Actions**.
4. Pilih **MH Tour APK Build** untuk Debug APK, atau **MH Tour APK Release** untuk Release APK.
5. Klik **Run workflow**.
6. Download artifact yang dihasilkan.

## Server

Gunakan `server/.env.example` sebagai acuan. Untuk produksi:
- `LIVEKIT_URL` harus menunjuk ke LiveKit publik (`wss://...`).
- Gunakan secret LiveKit yang panjang dan acak.
- Gunakan HTTPS untuk API.
- Batasi `ALLOWED_ORIGINS` jika diperlukan.

## QR

QR Guide berisi `MHTOUR|JOIN|<API_URL>|<KODE>`. Saat Jemaah scan QR, API URL dari QR disimpan otomatis. Ini membuat alamat server transparan bagi Jemaah.

## Hasil

Guide dan Jemaah dapat berada pada operator atau jaringan berbeda. Yang diperlukan adalah koneksi internet yang memungkinkan keduanya mencapai API dan LiveKit.


## LiveKit connection
This build uses the LiveKit Cloud Development Token Server ID `mhtour-19kg8e` for development/testing, so no separate MH Tour token backend URL is required. The LiveKit project remains unchanged.


## UI dan fungsi yang dipertahankan

- Beranda menggunakan artwork referensi MH Tour yang diberikan.
- Tombol Jemaah, Guide, dan Donasi tetap aktif sebagai area sentuh di atas artwork.
- QR donasi yang dipaketkan sama dengan QR terlampir.
- Link donasi DANA tetap: `https://link.dana.id/minta?full_url=https://qr.dana.id/v1/281012012023022707162488`
- Back dari halaman internal kembali ke Beranda tanpa menghentikan sesi Guide.
- Back di Beranda dua kali dalam 2 detik untuk keluar, dengan peringatan `Tekan sekali lagi untuk keluar`.
- Sesi Guide harus diakhiri sebelum sesi baru dibuat.
- LiveKit Android tetap pada `io.livekit:livekit-android:2.28.2`.
