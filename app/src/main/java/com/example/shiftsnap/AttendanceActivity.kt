package com.example.shiftsnap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.shiftsnap.model.AttendanceRecord
import com.example.shiftsnap.model.AttendanceRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceActivity : AppCompatActivity() {

    // ── Views ───────────────────────────────────────────────
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvNama: TextView
    private lateinit var tvWaktu: TextView
    private lateinit var tvStatus: TextView
    private lateinit var toggleJenis: MaterialButtonToggleGroup
    private lateinit var btnMasuk: MaterialButton
    private lateinit var tilShift: TextInputLayout
    private lateinit var spinnerShift: AutoCompleteTextView

    private lateinit var layoutLokasiEmpty: View
    private lateinit var imgLokasi: ImageView
    private lateinit var chipLokasi: Chip
    private lateinit var tvLokasiError: TextView
    private lateinit var tvKoordinat: TextView      // sekarang menampilkan ALAMAT (+ koordinat kecil)
    private lateinit var btnFotoLokasi: MaterialButton

    private lateinit var layoutWajahEmpty: View
    private lateinit var imgWajah: ImageView
    private lateinit var chipWajah: Chip
    private lateinit var tvWajahError: TextView
    private lateinit var btnFotoWajah: MaterialButton

    private lateinit var cardPekerjaan: View
    private lateinit var tilPekerjaan: TextInputLayout
    private lateinit var etPekerjaan: TextInputEditText
    private lateinit var imgKerja: ImageView
    private lateinit var btnFotoKerja: MaterialButton

    private lateinit var btnKirim: MaterialButton

    // ── State ───────────────────────────────────────────────
    private var lokasiUri: Uri? = null
    private var wajahUri: Uri? = null
    private var kerjaUri: Uri? = null
    private var pendingUri: Uri? = null          // uri yang sedang diisi kamera
    private var currentLocation: Location? = null
    private var pendingAction: (() -> Unit)? = null
    private var updatesActive = false

    // Deteksi alamat (reverse geocoding)
    private var alamatText: String? = null       // alamat terakhir yang berhasil dideteksi
    private var lastResolved: Location? = null   // titik terakhir yang sudah di-geocode
    private var resolving = false

    private lateinit var repo: AttendanceRepository
    private lateinit var operatorId: String

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    // Update posisi berkelanjutan → koordinat & alamat ikut diperbarui
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { showLocation(it) }
        }
    }
    private val dateFormat = SimpleDateFormat("EEEE, dd MMM yyyy  HH:mm", Locale("id", "ID"))

    // ── Activity result launchers ───────────────────────────
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                pendingAction?.invoke()
            } else {
                Toast.makeText(this, "Izin kamera/lokasi diperlukan untuk absensi", Toast.LENGTH_LONG).show()
            }
            pendingAction = null
        }

    private val takeLokasiLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                lokasiUri = pendingUri
                showLokasiPreview()
                // Foto lokasi selesai → langsung deteksi alamat dari posisi terbaru
                currentLocation?.let { resolveAlamat(it, force = true) } ?: fetchLocation()
            }
            pendingUri = null
        }

    private val takeWajahLauncher =
        registerForActivityResult(FrontCameraContract()) { success ->
            if (success) {
                wajahUri = pendingUri
                showWajahPreview()
            }
            pendingUri = null
        }

    private val takeKerjaLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                kerjaUri = pendingUri
                imgKerja.visibility = View.VISIBLE
                PhotoLoader.load(imgKerja, kerjaUri.toString())
                btnFotoKerja.text = "Ambil Ulang Foto Hasil Kerja"
            }
            pendingUri = null
        }

    // ── Lifecycle ───────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_attendance)

        repo = AttendanceRepository(this)
        operatorId = SessionManager(this).getOperatorId()

        // Sudah absen pulang → absensi hari ini selesai, tidak bisa absen lagi
        if (repo.todayPulang(operatorId) != null) {
            Toast.makeText(this, "Absensi hari ini sudah selesai", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bindViews()
        setupHeader()
        setupListeners()

        // Izin lokasi dibutuhkan supaya alamat bisa terdeteksi
        ensurePermissions(Manifest.permission.ACCESS_FINE_LOCATION) {
            fetchLocation()
            startLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        stopLocationUpdates()
        super.onPause()
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbar)
        tvNama = findViewById(R.id.tvAbsenNama)
        tvWaktu = findViewById(R.id.tvAbsenWaktu)
        tvStatus = findViewById(R.id.tvAbsenStatus)
        toggleJenis = findViewById(R.id.toggleJenisAbsen)
        btnMasuk = findViewById(R.id.btnAbsenMasuk)
        tilShift = findViewById(R.id.tilShiftAbsen)
        spinnerShift = findViewById(R.id.spinnerShiftAbsen)

        layoutLokasiEmpty = findViewById(R.id.layoutLokasiEmpty)
        imgLokasi = findViewById(R.id.imgFotoLokasi)
        chipLokasi = findViewById(R.id.chipLokasiAttached)
        tvLokasiError = findViewById(R.id.tvLokasiError)
        tvKoordinat = findViewById(R.id.tvKoordinat)
        btnFotoLokasi = findViewById(R.id.btnAmbilFotoLokasi)

        layoutWajahEmpty = findViewById(R.id.layoutWajahEmpty)
        imgWajah = findViewById(R.id.imgFotoWajah)
        chipWajah = findViewById(R.id.chipWajahAttached)
        tvWajahError = findViewById(R.id.tvWajahError)
        btnFotoWajah = findViewById(R.id.btnAmbilFotoWajah)

        cardPekerjaan = findViewById(R.id.cardPekerjaan)
        tilPekerjaan = findViewById(R.id.tilPekerjaan)
        etPekerjaan = findViewById(R.id.etPekerjaan)
        imgKerja = findViewById(R.id.imgFotoKerja)
        btnFotoKerja = findViewById(R.id.btnAmbilFotoKerja)

        btnKirim = findViewById(R.id.btnKirimAbsen)
    }

    private fun setupHeader() {
        toolbar.setNavigationOnClickListener { finish() }
        // Nama operator dikirim dari MainActivity (opsional)
        tvNama.text = intent.getStringExtra(EXTRA_OPERATOR_NAME) ?: "Operator"
        tvWaktu.text = dateFormat.format(Date())
        tvStatus.text = "Hari ini → ${repo.statusText(operatorId)}"

        // Sudah absen masuk → tombol Masuk dikunci, langsung ke Pulang
        val sudahMasuk = repo.todayMasuk(operatorId) != null
        btnMasuk.isEnabled = !sudahMasuk

        val jenis = intent.getStringExtra(EXTRA_JENIS)
            ?: if (sudahMasuk) AttendanceRecord.PULANG else AttendanceRecord.MASUK
        toggleJenis.check(
            if (jenis == AttendanceRecord.PULANG) R.id.btnAbsenPulang else R.id.btnAbsenMasuk
        )
    }

    private fun setupShiftDropdown() {
        spinnerShift.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                AttendanceRecord.SHIFT_OPTIONS
            )
        )
        spinnerShift.setOnClickListener { spinnerShift.showDropDown() }
        spinnerShift.setOnItemClickListener { _, _, _, _ -> tilShift.error = null }
    }

    private fun setupListeners() {
        setupShiftDropdown()

        btnFotoLokasi.setOnClickListener {
            ensurePermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) {
                fetchLocation()
                startLocationUpdates()
                launchCamera(prefix = "ABSEN_LOKASI") { uri -> takeLokasiLauncher.launch(uri) }
            }
        }

        btnFotoWajah.setOnClickListener {
            ensurePermissions(Manifest.permission.CAMERA) {
                launchCamera(prefix = "ABSEN_WAJAH") { uri -> takeWajahLauncher.launch(uri) }
            }
        }

        btnFotoKerja.setOnClickListener {
            ensurePermissions(Manifest.permission.CAMERA) {
                launchCamera(prefix = "ABSEN_KERJA") { uri -> takeKerjaLauncher.launch(uri) }
            }
        }

        // Kartu "Pekerjaan Hari Ini" hanya tampil saat memilih Pulang
        toggleJenis.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) updateJenisUi()
        }
        updateJenisUi()

        btnKirim.setOnClickListener { submitAttendance() }
    }

    private fun updateJenisUi() {
        val pulang = toggleJenis.checkedButtonId == R.id.btnAbsenPulang
        cardPekerjaan.visibility = if (pulang) View.VISIBLE else View.GONE

        // Shift dipilih saat MASUK. Saat PULANG, tampilkan shift yang sudah dipilih (terkunci).
        if (pulang) {
            val shiftHariIni = repo.todayShift(operatorId)
            spinnerShift.setText(shiftHariIni ?: "", false)
            tilShift.isEnabled = false
            tilShift.error = null
        } else {
            tilShift.isEnabled = true
        }
        btnKirim.text = if (pulang) "Kirim Absen Pulang" else "Kirim Absen Masuk"
    }

    // ── Kamera ──────────────────────────────────────────────
    private fun launchCamera(prefix: String, launch: (Uri) -> Unit) {
        val file = createImageFile(prefix)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingUri = uri
        launch(uri)
    }

    private fun createImageFile(prefix: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(dir, "${prefix}_$stamp.jpg")
    }

    private fun showLokasiPreview() {
        PhotoLoader.load(imgLokasi, lokasiUri.toString())
        imgLokasi.visibility = View.VISIBLE
        layoutLokasiEmpty.visibility = View.GONE
        chipLokasi.visibility = View.VISIBLE
        tvLokasiError.visibility = View.GONE
        btnFotoLokasi.text = "Ambil Ulang Foto Lokasi"
    }

    private fun showWajahPreview() {
        PhotoLoader.load(imgWajah, wajahUri.toString())
        imgWajah.visibility = View.VISIBLE
        layoutWajahEmpty.visibility = View.GONE
        chipWajah.visibility = View.VISIBLE
        tvWajahError.visibility = View.GONE
        btnFotoWajah.text = "Ambil Ulang Foto Wajah"
    }

    // ── Lokasi GPS + deteksi alamat ─────────────────────────
    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        if (alamatText == null) tvKoordinat.text = "Mencari lokasi…"
        fusedClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    showLocation(loc, forceResolve = lokasiUri != null)
                } else if (alamatText == null) {
                    tvKoordinat.text = "Lokasi tidak tersedia. Aktifkan GPS."
                }
            }
            .addOnFailureListener {
                if (alamatText == null) tvKoordinat.text = "Gagal mendapatkan lokasi"
            }
    }

    /** Simpan posisi terbaru lalu deteksi alamatnya (di-throttle supaya Geocoder tidak dibanjiri). */
    private fun showLocation(loc: Location, forceResolve: Boolean = false) {
        currentLocation = loc
        if (alamatText == null) {
            // Alamat belum ada → tampilkan koordinat sementara
            tvKoordinat.text = koordinatText(loc)
        }
        resolveAlamat(loc, force = forceResolve)
    }

    /**
     * Reverse geocoding: koordinat → "Jalan Penganten No. 5, Kelurahan, Kecamatan, Kota".
     * Hanya dijalankan lagi bila posisi bergeser > 50 m dari titik terakhir (atau [force]).
     */
    private fun resolveAlamat(loc: Location, force: Boolean = false) {
        val last = lastResolved
        if (!force && last != null && last.distanceTo(loc) < 50f) {
            // Posisi hampir sama: cukup perbarui akurasi di baris koordinat
            alamatText?.let { tvKoordinat.text = "$it\n${koordinatText(loc)}" }
            return
        }
        if (resolving) return
        resolving = true
        if (alamatText == null) tvKoordinat.text = "Mendeteksi alamat…"

        LocationResolver.resolve(this, loc.latitude, loc.longitude) { info ->
            resolving = false
            if (isFinishing || isDestroyed) return@resolve
            lastResolved = loc
            alamatText = info.alamatTampil
            tvKoordinat.text = "${info.alamatTampil}\n${koordinatText(loc)}"
        }
    }

    private fun koordinatText(loc: Location) = String.format(
        Locale.US, "%.6f, %.6f  (±%d m)",
        loc.latitude, loc.longitude, loc.accuracy.toInt()
    )

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (updatesActive) return
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        updatesActive = true
    }

    private fun stopLocationUpdates() {
        if (!updatesActive) return
        fusedClient.removeLocationUpdates(locationCallback)
        updatesActive = false
    }

    // ── Validasi & kirim ────────────────────────────────────
    private fun submitAttendance() {
        var valid = true

        if (lokasiUri == null) {
            tvLokasiError.visibility = View.VISIBLE
            valid = false
        }
        if (wajahUri == null) {
            tvWajahError.visibility = View.VISIBLE
            valid = false
        }
        // Absen masuk wajib memilih shift
        val shiftDipilih = spinnerShift.text?.toString()?.trim().orEmpty()
        val isMasuk = toggleJenis.checkedButtonId == R.id.btnAbsenMasuk
        if (isMasuk && shiftDipilih.isEmpty()) {
            tilShift.error = "Pilih shift terlebih dahulu"
            valid = false
        }
        if (currentLocation == null) {
            Toast.makeText(this, "Lokasi GPS belum didapat. Tunggu sebentar atau aktifkan GPS.", Toast.LENGTH_LONG).show()
            fetchLocation()
            valid = false
        }
        if (!valid) return

        val jenis = if (toggleJenis.checkedButtonId == R.id.btnAbsenMasuk)
            AttendanceRecord.MASUK else AttendanceRecord.PULANG
        val loc = currentLocation!!

        // Aturan absensi harian
        if (jenis == AttendanceRecord.MASUK && repo.todayMasuk(operatorId) != null) {
            Toast.makeText(this, "Kamu sudah absen masuk hari ini", Toast.LENGTH_LONG).show()
            return
        }
        if (jenis == AttendanceRecord.PULANG) {
            if (repo.todayMasuk(operatorId) == null) {
                Toast.makeText(this, "Absen masuk dulu sebelum absen pulang", Toast.LENGTH_LONG).show()
                return
            }
            if (repo.todayPulang(operatorId) != null) {
                Toast.makeText(this, "Kamu sudah absen pulang hari ini", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Absen pulang wajib mengisi pekerjaan hari ini
        var pekerjaan: String? = null
        if (jenis == AttendanceRecord.PULANG) {
            pekerjaan = etPekerjaan.text?.toString()?.trim().orEmpty()
            if (pekerjaan.isEmpty()) {
                tilPekerjaan.error = "Isi dulu pekerjaan yang dikerjakan hari ini"
                tilPekerjaan.requestFocus()
                return
            }
            tilPekerjaan.error = null
        }

        val record = AttendanceRecord(
            operatorId = operatorId,
            jenis = jenis,
            latitude = loc.latitude,
            longitude = loc.longitude,
            fotoLokasiUri = lokasiUri.toString(),
            fotoWajahUri = wajahUri.toString(),
            laporanKerja = pekerjaan,
            fotoKerjaUri = kerjaUri?.toString(),
            shift = if (jenis == AttendanceRecord.MASUK) shiftDipilih else null
        )
        repo.add(record)

        setResult(RESULT_OK, Intent().apply {
            putExtra(RESULT_JENIS, record.jenis)
            putExtra(RESULT_WAKTU, record.timestamp)
            putExtra(RESULT_FOTO_LOKASI, record.fotoLokasiUri)
            putExtra(RESULT_FOTO_WAJAH, record.fotoWajahUri)
            putExtra(RESULT_LAT, record.latitude)
            putExtra(RESULT_LNG, record.longitude)
        })

        val label = if (jenis == AttendanceRecord.MASUK) "masuk" else "pulang"
        Toast.makeText(this, "Absen $label berhasil disimpan", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ── Izin ────────────────────────────────────────────────
    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions(vararg permissions: String, onGranted: () -> Unit) {
        val missing = permissions.filterNot { hasPermission(it) }
        if (missing.isEmpty()) {
            onGranted()
        } else {
            pendingAction = onGranted
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // ── Kontrak kamera depan untuk selfie ───────────────────
    // Catatan: hint kamera depan lewat intent tidak dijamin didukung semua aplikasi kamera.
    private class FrontCameraContract : ActivityResultContracts.TakePicture() {
        override fun createIntent(context: Context, input: Uri): Intent =
            super.createIntent(context, input).apply {
                putExtra("android.intent.extras.CAMERA_FACING", 1)
                putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            }
    }

    companion object {
        const val EXTRA_OPERATOR_NAME = "extra_operator_name"
        const val EXTRA_JENIS = "extra_jenis"
        const val RESULT_JENIS = "result_jenis"
        const val RESULT_WAKTU = "result_waktu"
        const val RESULT_FOTO_LOKASI = "result_foto_lokasi"
        const val RESULT_FOTO_WAJAH = "result_foto_wajah"
        const val RESULT_LAT = "result_lat"
        const val RESULT_LNG = "result_lng"
    }
}