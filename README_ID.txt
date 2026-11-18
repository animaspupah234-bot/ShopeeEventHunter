SHOPEE EVENT HUNTER — MODE TESTING 0.3

PENTING:
- Ini proyek sumber Android, BUKAN APK siap instal.
- Mode ini hanya menguji satu kali klik pada label persis "Beli Sekarang" / "Buy Now".
- Setelah satu klik, mode otomatis nonaktif.
- Aplikasi tidak menekan Checkout, Buat Pesanan, Bayar, atau memasukkan PIN.
- Peringatan: tombol Beli Sekarang pada Shopee dapat langsung membawa ke halaman checkout. Gunakan hanya pada produk yang memang aman untuk diuji dan jangan lanjutkan pesanan.
- Hasil bergantung pada tampilan Shopee. Jika tombol tidak terbaca oleh Accessibility, klik tidak akan dilakukan.
- Jangan bagikan OTP/PIN. Aksesibilitas adalah izin sensitif; aktifkan hanya untuk pengujian ini dan matikan kembali setelah selesai.

BUILD APK ONLINE:
1. Ekstrak ZIP.
2. Buat repository GitHub baru, misalnya "ShopeeEventHunter".
3. Upload seluruh ISI folder proyek ke repository (termasuk folder .github).
4. Buka tab Actions di repository dan pilih workflow "Build Android APK".
5. Tunggu workflow selesai, buka hasil run, lalu unduh artifact "ShopeeEventHunter-TEST-APK".
6. Ekstrak artifact dan instal app-debug.apk pada HP Android 12.

CARA UJI:
1. Instal aplikasi dan buka.
2. Tekan Pengaturan Aksesibilitas lalu aktifkan "Shopee Event Hunter TEST".
3. Kembali ke aplikasi Hunter.
4. Siapkan halaman produk uji yang tidak akan dibeli.
5. Tekan "Aktifkan uji satu kali", lalu kembali ke Shopee.
6. Jika tombol persis "Beli Sekarang" terdeteksi, aplikasi mencoba satu klik lalu otomatis nonaktif.
7. Jangan lanjutkan checkout. Matikan layanan aksesibilitas setelah pengujian.

BUILD NOTES:
GitHub Actions menggunakan Java 17 dan Gradle dari runner. Paket ini belum ditandatangani untuk distribusi publik; debug APK hanya untuk pengujian pribadi.
