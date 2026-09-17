# MH Tour v5.3 – UI, Donasi, Back & Session

Perubahan utama:
- Beranda diperbarui mengikuti referensi tampilan MH Tour: putih, hijau, emas, logo MH Tour besar, tombol Jamaah/Guide/Donasi.
- Logo MH Tour terlampir digunakan konsisten pada setiap halaman utama aplikasi.
- Halaman **Donasi Seikhlasnya** menampilkan QR DANA terlampir, tombol **Unduh QR Code**, dan tombol **DANA • Donasi Langsung** dengan tautan DANA yang diminta.
- Tombol Unduh QR menyimpan `MH-Tour-QR-Donasi-DANA.png` ke folder Download.
- Tombol Back Android dari halaman mana pun kembali ke Beranda dan tidak langsung menutup aplikasi.
- Di Beranda, Back pertama menampilkan `Tekan sekali lagi untuk keluar`; Back kedua dalam 2 detik menutup aplikasi.
- Pada halaman Guide terdapat tombol **Akhiri Sesi**.
- Back dari halaman Guide tidak mengakhiri sesi; kode sesi dan koneksi LiveKit tetap dipertahankan selama aplikasi masih hidup.
- Sesi baru tidak dapat dibuat selama sesi lama masih aktif. Sesi harus diakhiri terlebih dahulu.
- Server dan integrasi LiveKit yang sudah ada tetap dipertahankan.

- Beranda kini menggunakan artwork referensi `home_reference.webp`; area sentuh transparan mempertahankan fungsi tombol tanpa menambahkan elemen visual di atas gambar.
- Workflow `build-release-signed.yml` tidak lagi mewajibkan `MH_TOUR_API_BASE_URL`, karena build Android saat ini menggunakan LiveKit Development Token Server yang sudah dipertahankan.
- Kedua workflow memiliki preflight untuk memeriksa asset, LiveKit, DANA, Back, dan session guard.
