# MH Tour V5.3 Final UI / Function Audit

## Beranda
- Artwork beranda menggunakan gambar referensi yang diberikan pengguna.
- Tiga area sentuh transparan mempertahankan fungsi:
  - Dengarkan Sebagai Jemaah
  - Lanjut Sebagai Pemandu
  - Donasi Seikhlasnya
- Struktur halaman internal tidak diubah.

## Donasi
- `donation_qr.png` diverifikasi byte-for-byte sama dengan QR terlampir.
- QR dapat dibaca dan menghasilkan payload DANA.
- Link DANA yang digunakan aplikasi:
  `https://link.dana.id/minta?full_url=https://qr.dana.id/v1/281012012023022707162488`
- Tombol Unduh QR menggunakan MediaStore pada Android 10+ dan folder Download pada Android lama.

## Navigasi Back
- Back dari halaman internal kembali ke Beranda.
- Back dari Guide tidak mengakhiri sesi atau memutus Room.
- Back di Beranda pertama menampilkan `Tekan sekali lagi untuk keluar`.
- Back kedua dalam 2 detik menutup aplikasi.

## Guide / Session
- Sesi baru diblokir selama sesi lama masih aktif.
- Tombol `Akhiri Sesi` memutus Room, menghapus kode sesi, dan mengizinkan sesi baru.
- QR dan kode sesi tetap tersedia saat kembali ke Guide selama sesi masih aktif.

## LiveKit
- Dependency `io.livekit:livekit-android:2.28.2` dipertahankan.
- Koneksi tetap menggunakan `LiveKit.create(...)` dan `room.connect(...)`.
- Development Token Server ID `mhtour-19kg8e` dipertahankan.
- Folder `server/` dan konfigurasi LiveKit tidak dihapus.

## GitHub Actions
- Kedua workflow menggunakan `workflow_dispatch` yang valid.
- Java 17 dan Gradle 8.11.1 dipertahankan.
- Preflight memeriksa AndroidX, LiveKit, asset UI, link donasi, fungsi Back, dan session guard.
- Workflow Debug tidak lagi gagal hanya karena repository variable `MH_TOUR_API_BASE_URL` belum dibuat, karena variable tersebut memang tidak digunakan oleh `MainActivity.kt`.
- Workflow Release membuat temporary CI keystore dan mengunggah APK Release.

## Verifikasi lingkungan
- YAML workflow: valid.
- AndroidManifest/styles XML: valid.
- Asset logo: identik dengan file logo terlampir.
- Asset QR donasi: identik dengan file QR terlampir dan berhasil didekode.
- Compile APK belum dapat dilakukan di lingkungan pemeriksaan ini karena executable Gradle tidak tersedia dan jaringan untuk mengunduh Gradle tidak tersedia. GitHub Actions disiapkan untuk melakukan compile sebenarnya.
