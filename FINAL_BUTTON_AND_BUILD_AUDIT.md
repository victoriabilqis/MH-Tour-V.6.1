# MH Tour V5.4 — Final Build & Button Audit

Tanggal audit: 17 September 2026

## Perubahan yang dilakukan

1. Memperbaiki workflow `.github/workflows/build-release.yml` agar tidak lagi menggunakan ekspresi `secrets.MH_KEYSTORE_BASE64` pada `if:`.
2. Release build tetap menggunakan temporary keystore yang dibuat otomatis di runner. Struktur server tidak diubah.
3. Menambahkan pemeriksaan preflight untuk wiring tombol sebelum compile.
4. Menambahkan perlindungan pada tombol Bagikan agar tidak crash ketika tidak ada aplikasi berbagi.
5. Validasi QR rombongan disamakan dengan format kode sesi yang dibuat aplikasi: `UM` + 6 digit.

## Pemeriksaan tombol

| Fungsi | Handler | Kondisi/hasil yang diperiksa |
|---|---|---|
| Jemaah dari Beranda | `jamaahBtn` | Membuka halaman Jemaah setelah internet tersedia |
| Guide dari Beranda | `guideBtn` | Membuka halaman Guide setelah internet tersedia |
| Donasi dari Beranda | `donationBtn` | Membuka halaman Donasi |
| Unduh QR | `download` | Menyimpan PNG ke Download; menangani izin/exception |
| DANA | `dana` | Membuka URI DANA; menangani aplikasi/browser yang tidak tersedia |
| Kembali | `back` | Kembali ke Beranda |
| Buat Sesi | `create` | Membuat kode + QR dan mengaktifkan kontrol sesi |
| Bagikan | `shareQr` | Membagikan kode; tombol hanya aktif saat sesi aktif |
| Akhiri Sesi | `end` | Memutus room dan mengembalikan status sesi |
| Mulai Bicara | `talk` | Meminta izin mic / menghubungkan LiveKit / publish mic |
| Mute | `mute` | Toggle mic saat room aktif |
| Scan QR | `scan` | Meminta izin kamera lalu membuka scanner |
| Gabung | `join` | Validasi kode dan menghubungkan Jemaah ke room |

Hasil static wiring check: **13/13 fungsi interaktif memiliki `setOnClickListener`.**

## Server

Folder `server/` dibandingkan dengan paket sumber awal: **tidak berubah**.

Tidak ada perubahan pada:
- `server/server.js`
- `server/livekit.yaml`
- `server/Dockerfile`
- `server/docker-compose.yml`
- `server/package.json`
- script start/stop server

## Build workflow

- Trigger: `workflow_dispatch` saja.
- Java: 17.
- Gradle: 8.11.1.
- Release APK dibuat dengan temporary keystore.
- APK wajib ada dan tidak kosong sebelum artifact di-upload.
- Tidak ada referensi invalid `secrets.MH_KEYSTORE_BASE64` di workflow release.

## Batas verifikasi

Pemeriksaan lokal ini adalah static/source-level audit. Compile Release APK dan pengujian klik/touch secara nyata harus diselesaikan oleh GitHub Actions dan/atau perangkat Android. Karena emulator/perangkat Android tidak tersedia di lingkungan pemeriksaan ini, audit ini tidak mengklaim telah melakukan tap fisik pada setiap tombol.
