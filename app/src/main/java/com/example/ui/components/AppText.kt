package com.example.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as Material3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val LocalAppLanguage = staticCompositionLocalOf { "ID" }

private val englishText = mapOf(
    "Aplikasi mengalami crash" to "The app crashed",
    "Salin Log" to "Copy log",
    "Hapus" to "Delete",
    "Batal" to "Cancel",
    "Otomatis" to "Automatic",
    "Gelap Glass" to "Dark",
    "Terang" to "Light",
    "TV Landscape" to "TV landscape",
    "HP Portrait" to "Phone portrait",
    "Pengaturan" to "Settings",
    "Profil" to "Profile",
    "Pengguna Google" to "Google user",
    "Pilih Playlist" to "Choose playlist",
    "Filter media berdasarkan sumber tersimpan" to "Filter media by saved source",
    "Tekan kembali atau area luar untuk menutup tirai" to "Press back or tap outside to close the panel",
    "Menu Profil" to "Profile",
    "Multi-User, Proteksi PIN, Cloud Firestore & Backup Terpadu" to "Multi-user, PIN protection, Cloud Firestore & unified backup",
    "Tambah Profil" to "Add profile",
    "Akun Cloud (Firebase Firestore)" to "Cloud account (Firebase Firestore)",
    "Penyimpanan cloud untuk semua profil & media streaming" to "Cloud storage for all profiles & streaming media",
    "Sync" to "Sync",
    "Menghubungkan ke akun Google..." to "Connecting to Google account...",
    "Hubungkan Akun Google (Cloud Firestore)" to "Connect Google account (Cloud Firestore)",
    "Daftar Profil Aktif & Tersedia:" to "Active & available profiles:",
    "Aktif" to "Active",
    "PIN Dilindungi" to "PIN protected",
    "Bebas PIN" to "No PIN",
    "Atur PIN" to "Set PIN",
    "Beralih" to "Switch",
    "Cadangan Data (Backup & Restore):" to "Data backup (Backup & Restore):",
    "1. Backup Biasa (Per-Profil)" to "1. Standard backup (per profile)",
    "2. Backup Aman (Full Backup Keseluruhan)" to "2. Secure backup (full backup)",
    "Tambah Profil Baru" to "Add new profile",
    "Nama Profil..." to "Profile name...",
    "Pilih Avatar:" to "Choose avatar:",
    "Kunci dengan PIN?" to "Protect with PIN?",
    "PIN wajib tepat 4 digit angka." to "PIN must be exactly 4 digits.",
    "PIN 4 Digit" to "4-digit PIN",
    "Simpan" to "Save",
    "Atur Ulang PIN" to "Reset PIN",
    "PIN baru" to "New PIN",
    "Ulangi PIN baru" to "Confirm new PIN",
    "Buka menu Extension untuk menambahkan link streaming, file lokal, atau upload server." to "Open Extension to add streaming links, local files, or server uploads.",
    "Buka Menu Extension" to "Open Extension",
    "Cari judul video, film, atau kategori..." to "Search videos, movies, or categories...",
    "Belum Ada Konten" to "No content yet",
    "Semua Playlist" to "All playlists",
    "Belum ada video di antrean" to "No videos in queue",
    "Kendala Pemutaran Media" to "Playback problem",
    "Putar Video Demo (bukan sumber asli)" to "Play demo video (not the original source)",
    "Coba Lagi" to "Try again",
    "Tutup" to "Close",
    "GESER UNTUK MENCARI POSISI" to "DRAG TO SEEK",
    "LIVE / TIDAK DAPAT DI-SEEK" to "LIVE / NOT SEEKABLE",
    "Menu Pengaturan" to "Settings",
    "Konfigurasi tampilan UI, Cloud Sync Firebase, ukuran grid, dan preferensi" to "Configure the interface, Firebase Cloud Sync, grid size, and preferences",
    "Cloud Terhubung" to "Cloud connected",
    "Keluar Akun Google" to "Sign out of Google",
    "Menghubungkan ke layanan Google Sign-In..." to "Connecting to Google Sign-In...",
    "Masuk dengan akun Google untuk mengamankan data streaming, bookmark, dan pengaturan multi-profil Anda ke Cloud Firestore." to "Sign in with Google to secure streaming data, bookmarks, and multi-profile settings in Cloud Firestore.",
    "Masuk dengan Google (Cloud Sync)" to "Sign in with Google (Cloud Sync)",
    "Pengaturan Bahasa" to "Language settings",
    "Pilih bahasa antarmuka aplikasi Net Stream iO" to "Choose the NSiO interface language",
    "Pengaturan Tema" to "Theme settings",
    "Pilih gaya visualisasi tema antarmuka" to "Choose the interface theme",
    "Pengaturan Tampilan & Orientasi" to "Display & orientation",
    "Optimasi tata letak remote TV D-pad (Landscape) atau sentuhan HP (Portrait)" to "Optimize the layout for TV remote (landscape) or phone touch (portrait)",
    "Pengaturan Ukuran Grid Beranda" to "Home grid size",
    "Keamanan PIN Sumber Server" to "Server source PIN security",
    "PIN terenkripsi untuk membuka menu upload dan kelola server" to "Encrypted PIN for server uploads and management",
    "Ubah PIN keamanan" to "Change security PIN",
    "Pembaruan Aplikasi" to "App updates",
    "Periksa rilis resmi NSiO. APK akan divalidasi sebelum installer Android dibuka." to "Check official NSiO releases. The APK is validated before opening the Android installer.",
    "Lokasi APK: Penyimpanan internal/NSiO/Pembaruan Aplikasi" to "APK location: Internal storage/NSiO/App Updates",
    "Izinkan akses folder NSiO" to "Allow access to NSiO folder",
    "Riwayat unduhan APK" to "APK download history",
    "Muat ulang riwayat" to "Refresh history",
    "Instal" to "Install",
    "Rusak" to "Invalid",
    "Ukuran tidak diketahui" to "Size unknown",
    "Memeriksa..." to "Checking...",
    "Cek Update" to "Check for updates",
    "Unduh APK" to "Download APK",
    "PIN lama" to "Current PIN",
    "PIN baru (6 digit)" to "New PIN (6 digits)",
    "Verifikasi" to "Verify",
    "Media Sosial & Komunitas" to "Social media & community",
    "Ikuti kanal resmi pengembang" to "Follow the developer's official channels",
    "Menu Extension" to "Extension",
    "Pusat Manajemen Sumber Konten Video, Foto & Ekstensi CloudStream" to "Manage video sources, photos, and CloudStream extensions",
    "B1. Tambah Sumber Link" to "B1. Add link source",
    "Mendukung Repo CloudStream JSON, Google Drive Publik, m3u/m3u8, dan direct video/audio URL." to "Supports CloudStream JSON repos, public Google Drive, m3u/m3u8, and direct video/audio URLs.",
    "Nama playlist (opsional)" to "Playlist name (optional)",
    "Kosongkan untuk memakai nama otomatis" to "Leave blank to use an automatic name",
    "Beranda" to "Home",
    "B2. Sumber File (Unggah dari Perangkat)" to "B2. File source (upload from device)",
    "Impor file video/foto dari penyimpanan internal atau eksternal perangkat untuk muncul di beranda." to "Import video/photo files from internal or external storage to show them on Home.",
    "Judul File / Video..." to "File / video title...",
    "Path atau Uri file perangkat..." to "Device file path or URI...",
    "Mengimpor..." to "Importing...",
    "Impor ke Beranda" to "Import to Home",
    "B3. Sumber Server (Terproteksi PIN)" to "B3. Server source (PIN protected)",
    "Pilih file dari perangkat. File disimpan privat ke Firebase Storage milik aplikasi dan otomatis dicatat di beranda. Login Google tidak diperlukan; aplikasi memakai sesi anonim khusus perangkat." to "Choose a file from your device. It is stored privately in the app's Firebase Storage and automatically listed on Home. Google sign-in is not required; the app uses a device-specific anonymous session.",
    "Judul konten server..." to "Server content title...",
    "Pilih file video dari perangkat" to "Choose a video file from device",
    "Pilih file foto dari perangkat" to "Choose a photo file from device",
    "Mengunggah..." to "Uploading...",
    "Upload File ke Server" to "Upload file to server",
    "Buka Beranda" to "Open Home",
    "Belum ada konten yang di-upload ke server." to "No content has been uploaded to the server.",
    "Belum ada link sumber yang dimasukkan." to "No source links have been added.",
    "Repository Eksternal" to "External repository",
    "Simpan URL katalog/repository eksternal. Saat ini NSiO dapat memperbarui sumber playlist M3U dari URL yang didukung, tetapi belum menjalankan plugin CloudStream berbasis Kotlin/JS secara langsung." to "Save an external catalog/repository URL. NSiO can currently refresh supported M3U playlist sources, but cannot directly run Kotlin/JS-based CloudStream plugins.",
    "Repository Tersimpan" to "Saved repositories",
    "Plugin Tercatat" to "Registered plugins",
    "Runtime eksekusi plugin eksternal belum tersedia. Entri di bawah hanya metadata plugin yang tercatat." to "External plugin execution is not available yet. Entries below are registered plugin metadata only.",
    "Belum ada metadata plugin. Catatan: NSiO belum menjalankan kode plugin CloudStream eksternal." to "No plugin metadata yet. Note: NSiO does not execute external CloudStream plugin code.",
    "Rename Playlist" to "Rename playlist",
    "Nama playlist" to "Playlist name",
    "Refresh" to "Refresh",
    "Belum ada playlist tersimpan." to "No saved playlists yet.",
    "PIN lama salah. Akses perubahan ditolak." to "Incorrect current PIN. Change access denied.",
    "PIN baru harus tepat 6 digit." to "The new PIN must be exactly 6 digits.",
    "Konfirmasi PIN tidak cocok." to "PIN confirmation does not match.",
    "Gunakan PIN yang berbeda dari PIN lama." to "Choose a PIN different from the current one.",
    "Buat PIN baru" to "Create a new PIN",
    "Verifikasi PIN lama" to "Verify current PIN",
    "Masukkan PIN baru 6 digit, lalu ulangi untuk konfirmasi." to "Enter a new 6-digit PIN, then repeat it to confirm.",
    "Masukkan PIN saat ini untuk membuka pengaturan keamanan." to "Enter your current PIN to access security settings."
)

fun translateAppText(text: String, language: String): String {
    if (language != "EN") return text
    englishText[text]?.let { return it }
    return when {
        text.startsWith("Tidak ada hasil untuk") -> text.replaceFirst("Tidak ada hasil untuk", "No results for")
        text.startsWith("Menampilkan ") -> text.replaceFirst("Menampilkan ", "Showing ").replace(" media", " items").replace(" untuk ", " for ")
        text.startsWith("Jumlah kolom kartu video di beranda: ") -> text.replaceFirst("Jumlah kolom kartu video di beranda: ", "Home video grid: ").replace(" kolom", " columns")
        text.startsWith("Daftar Konten Server Terupload") -> text.replaceFirst("Daftar Konten Server Terupload", "Uploaded server content").replace(" Item):", " items:")
        text.startsWith("Riwayat Playlist Sumber") -> text.replaceFirst("Riwayat Playlist Sumber", "Source playlist history")
        text.startsWith("Plugin Tercatat") -> text.replaceFirst("Plugin Tercatat", "Registered plugins")
        text.startsWith("Repository Tersimpan") -> text.replaceFirst("Repository Tersimpan", "Saved repositories")
        text.startsWith("Versi terbaru:") -> text.replaceFirst("Versi terbaru:", "Latest version:")
        text.startsWith("Progres unduhan:") -> text.replaceFirst("Progres unduhan:", "Download progress:")
        text.startsWith("NSiO sudah menggunakan versi terbaru") -> text.replaceFirst("NSiO sudah menggunakan versi terbaru", "NSiO is up to date")
        text.startsWith("Pembaruan v") -> text.replaceFirst("Pembaruan", "Update")
        text.startsWith("Mem-backup ") -> text.replaceFirst("Mem-backup", "Backing up")
        text.startsWith("Memulihkan ") -> text.replaceFirst("Memulihkan", "Restoring")
        else -> text
    }
}

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val readableFontSize = if (fontSize != TextUnit.Unspecified && fontSize.value < 10f) 10.sp else fontSize
    Material3Text(
        text = translateAppText(text, LocalAppLanguage.current),
        modifier = modifier,
        color = color,
        fontSize = readableFontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
