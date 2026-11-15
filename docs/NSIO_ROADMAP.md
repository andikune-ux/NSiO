# NSiO — Roadmap Penyempurnaan

Dokumen ini mencatat keputusan produk yang telah disetujui dan hasil audit awal repository. Dokumen ini bukan klaim bahwa seluruh fitur sudah selesai.

## Prinsip wajib

- Tidak ada kontrol UI yang hanya simulasi; setiap kontrol harus terhubung ke logika nyata.
- Perubahan database wajib memakai migrasi Room yang mempertahankan data pengguna.
- Kegagalan satu sumber/plugin tidak boleh menghentikan sumber lain.
- Cache terakhir dipertahankan saat refresh gagal dan status error harus terlihat.
- Backup portable tidak boleh mengekspor PIN atau kredensial device-bound.
- Sinkronisasi lintas perangkat bersifat opsional; aplikasi tetap berfungsi lokal.
- Uji build dan unit test sebelum menyatakan pekerjaan selesai; uji perangkat tetap perlu diverifikasi pada perangkat nyata.

## Keputusan katalog Home

- Default filter: Semua Sumber.
- Tampilkan kelompok playlist, plugin, dan repository secara terpisah.
- Sumber nonaktif tidak ditampilkan.
- Item duplikat dari sumber berbeda tetap menjadi item terpisah.
- Filter dan pencarian berlaku pada cakupan sumber yang dipilih.
- Ingat filter terakhir per profil.
- Urutan plugin menggabungkan rekomendasi otomatis (aktivitas/jumlah konten terbaru) dan drag-and-drop manual; pilihan manual dipertahankan per profil, dengan reset ke urutan otomatis.
- Jika sumber gagal: section tetap terlihat, gunakan cache terakhir, tampilkan error dan tombol coba lagi.
- Refresh saat Home dibuka, manual, dan periodik setiap 6 jam pada jaringan CONNECTED (Wi-Fi maupun seluler).
- Refresh paralel secara terkontrol, tanpa refresh ganda untuk sumber yang sama.
- Baris katalog horizontal; swipe kanan-ke-kiri memuat konten berikutnya.
- Tombol expand pada tiap baris membuka katalog penuh dengan grid, filter, sorting, dan mempertahankan posisi scroll.
- Fitur discovery: baru ditambahkan, baru diperbarui, A–Z, paling sering ditonton, lanjutkan menonton, favorit, dan riwayat.
- Plugin tanpa katalog utama tetap terlihat dengan status katalog tidak tersedia dan tetap dapat dikelola dari Extension.

## Keputusan lintas fitur

### Konten dan riwayat
- Item dari sumber berbeda tetap terpisah.
- Kemiripan konten dapat ditandai; sinkronisasi favorit/riwayat hanya jika identitas cocok secara meyakinkan.
- Progres tidak boleh digabung jika versi/edisi konten berbeda.

### Kegagalan sumber
- Pertahankan cache.
- Tampilkan status, alasan yang aman, dan tindakan coba lagi.
- Kegagalan satu plugin tidak menghentikan refresh plugin lain.

### Pembaruan aplikasi
Pertahankan profil, pengaturan, playlist, repository, plugin terpasang, favorit, riwayat, progres, dan unduhan sejauh kompatibilitas serta kebijakan migrasi memungkinkan. Jangan menghapus data secara otomatis ketika migrasi gagal.

### Multi-perangkat
- Sinkronisasi opsional.
- Mode lokal tetap lengkap.
- Kredensial PIN/device-bound tidak pernah disalin sebagai teks biasa.

### Crash dan pemulihan
- Catat diagnostik tanpa data rahasia.
- Sediakan ekspor laporan.
- Jangan hapus data otomatis.
- Tawarkan penonaktifan plugin jika terindikasi sebagai penyebab.
- Mode pemulihan setelah kegagalan startup berulang.

## Roadmap implementasi

1. **Fondasi katalog terpadu:** identitas sumber yang eksplisit, state/error/cache per sumber, filter dan urutan per profil, worker refresh 6 jam, UI baris katalog dan halaman penuh.
2. **Plugin & keamanan:** kompatibilitas, integritas artifact, status kesehatan, isolasi kegagalan, pengelolaan izin, dan pengujian .cs3 end-to-end. Catatan: penanganan exception saja bukan sandbox untuk kode plugin.
3. **Pemutar video:** audio/subtitle, sinkronisasi subtitle, kecepatan, rasio/zoom, episode berikutnya, intro marker, fallback sumber, gesture TV/touch, resume yang aman.
4. **Data & profil:** riwayat/favorit/lanjutkan per profil, backup/restore lengkap untuk data portable, migrasi aman, opsi sinkronisasi.
5. **Download & penyimpanan:** antrean, pause/resume/cancel, validasi file, ruang penyimpanan, retry, dan pembersihan cache yang tidak menyentuh unduhan.
6. **UX & aksesibilitas:** fokus D-pad, touch, empty/loading/error state, performa perangkat rendah, ukuran teks/kontras, dan preferensi tampilan.
7. **Reliability & release:** pusat diagnostik, crash recovery, pemeriksaan database, pengujian performa/kompatibilitas, serta jalur update reguler dan Lite yang terpisah.

## Audit awal repository (branch main, commit 70c3ef9)

- Room database saat ini versi 4; `MediaItem` memiliki `playlistId` dan `sourceType`, tetapi belum memiliki identitas `pluginId`/`repositoryId` khusus.
- `HomeScreen` saat ini memfilter daftar media datar; katalog belum dibangun sebagai section per sumber.
- Interface plugin NSiO saat ini menyediakan `searchMedia(query)`, belum memiliki kontrak katalog utama/paging/health status.
- `PluginManager` menyimpan repository/plugin lewat SharedPreferences.
- Refresh playlist saat ini dilakukan saat data awal dimuat dan secara manual; belum ditemukan worker periodik 6 jam.
- `CrashLogStore` dan alur ekspor log crash sudah ada.
- Backup Aman sudah ada, tetapi backup penuh saat ini mencakup profil, pengaturan, dan media; belum mencakup seluruh metadata plugin/repository/unduhan.
- Filter Home sebelumnya direset ke ALL ketika profil diganti. Perbaikan awal untuk menyimpan filter per profil dibuat pada commit `890b44e`; perlu verifikasi CI.

## Status

Roadmap disetujui pengguna. Fitur dinyatakan selesai hanya setelah implementasi, migrasi/test, CI, dan verifikasi perilaku yang relevan.