package com.example.shiftsnap

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Hasil deteksi wilayah dari koordinat GPS.
 *
 * @param isApproximate true bila nama wilayah hanya perkiraan (Geocoder tidak tersedia /
 *                      gagal, jadi dipakai tebakan kota terdekat).
 * @param nomor         nomor bangunan di jalan tsb (mis. "5"), bisa null.
 * @param alamatLengkap baris alamat pertama dari Geocoder, dipakai bila nama jalan kosong.
 */
data class PlaceInfo(
    val kelurahan: String?,
    val kecamatan: String?,
    val kota: String?,
    val jalan: String?,
    val isJakarta: Boolean,
    val isApproximate: Boolean,
    val nomor: String? = null,
    val alamatLengkap: String? = null
) {

    val judul: String
        get() = kelurahan ?: kecamatan ?: kota ?: "Lokasi belum dikenali"

    val detail: String
        get() = listOfNotNull(
            kecamatan?.takeIf { it != judul }?.let { "Kec. $it" },
            kota
        ).joinToString(" · ").ifEmpty { "Di luar wilayah Jabodetabek" }

    val ringkas: String
        get() = listOfNotNull(kelurahan ?: kecamatan, kota).joinToString(", ")
            .ifEmpty { "Lokasi tidak dikenali" }

    val jalanNomor: String?
        get() = jalan?.takeIf { it.isNotBlank() }?.let { j ->
            if (nomor.isNullOrBlank()) j else "$j No. $nomor"
        }

    /**
     * Alamat siap tampil (2 baris), mis.
     * "Jalan Penganten No. 5\nSenayan, Kec. Kebayoran Baru, Jakarta Selatan"
     */
    val alamatTampil: String
        get() {
            val baris1 = jalanNomor ?: alamatLengkap?.substringBefore(",")?.trim()
            val baris2 = listOfNotNull(
                kelurahan,
                kecamatan?.takeIf { it != kelurahan }?.let { "Kec. $it" },
                kota
            ).joinToString(", ")
            return listOf(baris1, baris2)
                .filter { !it.isNullOrBlank() }
                .joinToString("\n")
                .ifEmpty { "Alamat tidak dikenali" }
        }
}

/**
 * Mengubah koordinat menjadi alamat / nama wilayah (jalan, kelurahan, kecamatan, kota).
 *
 * 1. Utama  : android.location.Geocoder (butuh internet + layanan geocoder di perangkat).
 *             Berlaku di seluruh Indonesia
 * 2. Cadangan: tebakan kota terdekat dari titik pusat untuk area Jabodetabek
 *             (offline, ditandai perkiraan).
 */
object LocationResolver {

    private val main = Handler(Looper.getMainLooper())

    // Bounding box kasar Jabodetabek bagian inti (Jakarta, Depok, Bekasi, Tangerang, Tangsel).
    private const val AREA_LAT_MIN = -6.50
    private const val AREA_LAT_MAX = -6.05
    private const val AREA_LNG_MIN = 106.55
    private const val AREA_LNG_MAX = 107.10

    // Titik pusat kota (perkiraan) untuk cadangan offline. Dekat batas kota hasilnya bisa meleset.
    private val KOTA_CENTER = listOf(
        Triple("Jakarta Pusat", -6.1805, 106.8284),
        Triple("Jakarta Utara", -6.1384, 106.8650),
        Triple("Jakarta Barat", -6.1683, 106.7588),
        Triple("Jakarta Selatan", -6.2615, 106.8106),
        Triple("Jakarta Timur", -6.2250, 106.9004),
        Triple("Depok", -6.4025, 106.7942),
        Triple("Bekasi", -6.2383, 106.9756),
        Triple("Tangerang", -6.1783, 106.6319),
        Triple("Tangerang Selatan", -6.2886, 106.7179)
    )

    /** Callback selalu dipanggil di main thread. */
    @Suppress("DEPRECATION")
    fun resolve(context: Context, lat: Double, lng: Double, callback: (PlaceInfo) -> Unit) {
        val app = context.applicationContext
        if (!Geocoder.isPresent()) {
            callback(fallback(lat, lng))
            return
        }
        val geocoder = Geocoder(app, Locale("id", "ID"))
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        val info = addresses.firstOrNull()?.let { parse(it, lat, lng) }
                            ?: fallback(lat, lng)
                        main.post { callback(info) }
                    }

                    override fun onError(errorMessage: String?) {
                        main.post { callback(fallback(lat, lng)) }
                    }
                })
            } else {
                Thread {
                    val parsed: PlaceInfo? = try {
                        geocoder.getFromLocation(lat, lng, 1)
                            ?.firstOrNull()
                            ?.let { parse(it, lat, lng) }
                    } catch (e: Exception) {
                        null
                    }
                    val info = parsed ?: fallback(lat, lng)
                    main.post { callback(info) }
                }.start()
            }
        } catch (e: Exception) {
            callback(fallback(lat, lng))
        }
    }

    // ── Parsing hasil Geocoder ──────────────────────────────

    private fun parse(a: Address, lat: Double, lng: Double): PlaceInfo {
        val adminArea = a.adminArea.orEmpty()          // "Daerah Khusus Ibukota Jakarta" / "Jawa Barat"
        val subAdmin = a.subAdminArea.orEmpty()        // "Kota Jakarta Selatan" / "Kota Depok"

        // Kota dari Geocoder; bila kosong dan posisi di Jabodetabek, tebak kota terdekat.
        val kota = normalizeKota(subAdmin)
            ?: if (insideArea(lat, lng)) nearestKota(lat, lng) else null

        val inJakarta = adminArea.contains("jakarta", true) ||
                subAdmin.contains("jakarta", true) ||
                kota?.startsWith("Jakarta", true) == true

        // Di Indonesia: subLocality ≈ kelurahan, locality ≈ kecamatan (variasi antar perangkat).
        val kelurahan = clean(a.subLocality)
        val kecamatan = clean(a.locality)?.takeIf { !it.contains("jakarta", true) }

        return PlaceInfo(
            kelurahan = kelurahan,
            kecamatan = kecamatan,
            kota = kota,
            jalan = a.thoroughfare,
            isJakarta = inJakarta,
            isApproximate = false,
            nomor = a.subThoroughfare,
            alamatLengkap = if (a.maxAddressLineIndex >= 0) a.getAddressLine(0) else null
        )
    }

    private fun clean(s: String?): String? = s
        ?.replace(Regex("^(Kecamatan|Kec\\.?|Kelurahan|Kel\\.?|Desa)\\s+", RegexOption.IGNORE_CASE), "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun normalizeKota(raw: String): String? {
        if (raw.isBlank()) return null
        var s = raw.trim()
            .replace(Regex("^(Kota Administrasi|Kota|Kabupaten)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+City$", RegexOption.IGNORE_CASE), "")
        // Jaga-jaga bila Geocoder mengembalikan bahasa Inggris
        s = s.replace("South Jakarta", "Jakarta Selatan", true)
            .replace("North Jakarta", "Jakarta Utara", true)
            .replace("East Jakarta", "Jakarta Timur", true)
            .replace("West Jakarta", "Jakarta Barat", true)
            .replace("Central Jakarta", "Jakarta Pusat", true)
            .replace("South Tangerang", "Tangerang Selatan", true)
            .replace("Thousand Islands", "Kepulauan Seribu", true)
        return s.takeIf { it.isNotEmpty() }
    }

    // ── Cadangan offline ────────────────────────────────────

    private fun insideArea(lat: Double, lng: Double) =
        lat in AREA_LAT_MIN..AREA_LAT_MAX && lng in AREA_LNG_MIN..AREA_LNG_MAX

    private fun nearestKota(lat: Double, lng: Double): String {
        val k = cos(Math.toRadians(lat))
        return KOTA_CENTER.minByOrNull { (_, clat, clng) ->
            val dy = lat - clat
            val dx = (lng - clng) * k
            sqrt(dx * dx + dy * dy)
        }!!.first
    }

    /** Dipakai saat Geocoder gagal, juga untuk data absensi lama yang belum punya nama lokasi. */
    fun fallback(lat: Double, lng: Double): PlaceInfo {
        val kota = if (insideArea(lat, lng)) nearestKota(lat, lng) else null
        return PlaceInfo(
            kelurahan = null,
            kecamatan = null,
            kota = kota,
            jalan = null,
            isJakarta = kota?.startsWith("Jakarta", true) == true,
            isApproximate = true
        )
    }
}