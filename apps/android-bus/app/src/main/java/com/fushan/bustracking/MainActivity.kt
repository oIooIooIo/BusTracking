package com.fushan.bustracking

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fushan.bustracking.data.BusDatabase
import com.fushan.bustracking.data.LocalStore
import com.fushan.bustracking.databinding.ActivityMainBinding
import com.fushan.bustracking.device.DedicatedDeviceController
import com.fushan.bustracking.device.DeviceIdentity
import com.fushan.bustracking.nfc.CardLanReader
import com.fushan.bustracking.sync.SyncScheduler
import com.fushan.bustracking.tracking.LocationTrackingService
import com.fushan.bustracking.tracking.ScanLocationProvider
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    companion object {
        private const val TAG = "BusTrackingNfc"
        private const val CARD_INPUT_TIMEOUT_MS = 1_000L
        private const val SCAN_RESULT_DISPLAY_MS = 5_000L
        private const val SUCCESS_TONE_DURATION_MS = 250
        private const val FAILURE_TONE_DURATION_MS = 500
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: BusDatabase
    private lateinit var localStore: LocalStore
    private lateinit var scanLocationProvider: ScanLocationProvider
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var cardLanReader: CardLanReader
    @Volatile
    private var cardLanState: CardLanReader.State = CardLanReader.State.Starting
    private val cardInputBuffer = StringBuilder()
    private var lastCardInputAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private val latestScanId = AtomicLong()
    private var resetScanResult: Runnable? = null
    private var manualSyncId: UUID? = null

    private val foregroundPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startTrackingIfAllowed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        database = BusDatabase.get(this)
        localStore = LocalStore(database)
        scanLocationProvider = ScanLocationProvider(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        cardLanReader = CardLanReader(
            onCardDetected = ::processCard,
            onStateChanged = { state ->
                cardLanState = state
                runOnUiThread(::refreshStatus)
            },
        )

        binding.syncNow.setOnClickListener {
            startManualSync()
        }
        binding.root.setOnKeyListener { _, _, event ->
            handleCardReaderKey(event)
        }

        handleNfcIntent(intent)
        requestRequiredPermissions()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        DedicatedDeviceController.enterLockTask(this)
        binding.root.requestFocus()
        cardLanReader.start()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
        refreshStatus()
    }

    override fun onPause() {
        cardLanReader.stop()
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
    }

    override fun onDestroy() {
        resetScanResult?.let(mainHandler::removeCallbacks)
        toneGenerator.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onTagDiscovered(tag: Tag) {
        val cardSn = tag.id.joinToString("") { byte ->
            String.format(Locale.ROOT, "%02X", byte.toInt() and 0xFF)
        }
        processCard(cardSn)
    }

    private fun handleCardReaderKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastCardInputAt > CARD_INPUT_TIMEOUT_MS) {
            cardInputBuffer.clear()
        }
        lastCardInputAt = now

        if (event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        ) {
            val cardSn = cardInputBuffer.toString()
            cardInputBuffer.clear()
            if (cardSn.isNotBlank()) {
                processCard(cardSn)
                return true
            }
        }

        val character = event.unicodeChar.takeIf { it != 0 }?.toChar()
        if (character != null && character.isLetterOrDigit()) {
            cardInputBuffer.append(character.uppercaseChar())
            return true
        }

        return false
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            intent?.action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            return
        }

        val tag = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        if (tag == null) {
            Log.w(TAG, "NFC intent did not contain a tag")
            return
        }
        onTagDiscovered(tag)
    }

    private fun processCard(rawCardSn: String) {
        val cardSn = rawCardSn.trim().uppercase(Locale.ROOT)
        if (cardSn.isEmpty()) {
            return
        }
        val scanId = latestScanId.incrementAndGet()
        resetScanResult?.let(mainHandler::removeCallbacks)
        Log.i(TAG, "Card detected")
        val scannedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            val decision = localStore.checkCard(cardSn)
            runOnUiThread {
                if (scanId == latestScanId.get()) {
                    showDecision(cardSn, decision, scanId)
                }
            }
            val capture = scanLocationProvider.capture()
            localStore.recordBoarding(cardSn, decision, scannedAt, capture)
            SyncScheduler.enqueueNow(this@MainActivity)
            runOnUiThread { refreshStatus() }
        }
    }

    private fun showDecision(
        cardSn: String,
        decision: LocalStore.CardDecision,
        scanId: Long,
    ) {
        when (decision.result) {
            "ALLOWED" -> {
                binding.scanResult.setBackgroundColor(Color.parseColor("#DFF5E5"))
                binding.scanResult.setTextColor(Color.parseColor("#176B2C"))
                binding.scanResult.text =
                    "ALLOWED\n${decision.employee?.employeeName}\n$cardSn"
                toneGenerator.startTone(
                    ToneGenerator.TONE_PROP_ACK,
                    SUCCESS_TONE_DURATION_MS,
                )
            }
            "AUTH_DATA_NOT_READY" -> {
                binding.scanResult.setBackgroundColor(Color.parseColor("#FFF3CD"))
                binding.scanResult.setTextColor(Color.parseColor("#7A5700"))
                binding.scanResult.text = "AUTH DATA NOT READY\n$cardSn"
                playFailureTone()
            }
            else -> {
                binding.scanResult.setBackgroundColor(Color.parseColor("#FDE2E2"))
                binding.scanResult.setTextColor(Color.parseColor("#9A1B1B"))
                binding.scanResult.text = "DENIED\n$cardSn"
                playFailureTone()
            }
        }
        scheduleScanResultReset(scanId)
    }

    private fun playFailureTone() {
        toneGenerator.startTone(
            ToneGenerator.TONE_PROP_NACK,
            FAILURE_TONE_DURATION_MS,
        )
    }

    private fun startManualSync() {
        val workId = SyncScheduler.enqueueManualNow(this)
        manualSyncId = workId
        binding.deviceStatus.text = "Synchronization scheduled"

        val workInfo = WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId)
        val observer = object : Observer<WorkInfo?> {
            override fun onChanged(value: WorkInfo?) {
                val state = value?.state ?: return
                if (!state.isFinished) return
                workInfo.removeObserver(this)
                if (manualSyncId != workId) return

                manualSyncId = null
                refreshStatus()
                if (state == WorkInfo.State.FAILED && !isFinishing) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Synchronization failed")
                        .setMessage("Unable to synchronize. Please check the connection and try again.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
        workInfo.observe(this, observer)
    }

    private fun scheduleScanResultReset(scanId: Long) {
        resetScanResult?.let(mainHandler::removeCallbacks)
        resetScanResult = Runnable {
            if (scanId == latestScanId.get()) {
                binding.scanResult.setBackgroundColor(Color.TRANSPARENT)
                binding.scanResult.setTextColor(Color.parseColor("#445064"))
                binding.scanResult.text = "Waiting for employee card"
            }
        }.also {
            mainHandler.postDelayed(it, SCAN_RESULT_DISPLAY_MS)
        }
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val state = database.deviceState().get()
            val gpsPending = database.gps().pendingCount()
            val eventPending = database.boardingEvents().pendingCount()
            val readerState = when (val state = cardLanState) {
                CardLanReader.State.Starting -> "CardLan reader starting"
                CardLanReader.State.Ready -> "CardLan reader ready"
                is CardLanReader.State.Error -> "CardLan error: ${state.message}"
            }
            val nfcState = when {
                nfcAdapter == null -> readerState
                nfcAdapter?.isEnabled != true -> "NFC disabled / $readerState"
                else -> "Android NFC ready / $readerState"
            }
            binding.deviceStatus.text = buildString {
                append(state?.busCode ?: "Bus not synchronized")
                if (!state?.routeNames.isNullOrBlank()) {
                    append(" / ")
                    append(state?.routeNames)
                }
                append(" / ")
                append(nfcState)
                append("\nPermission version: ")
                append(state?.permissionVersion ?: "none")
                append("\nDevice: ")
                append(
                    DeviceIdentity.value(this@MainActivity)
                        ?: "Dedicated Device enrollment required",
                )
                append("\nPending GPS: ")
                append(gpsPending)
                append(" / Pending events: ")
                append(eventPending)
                append("\nDropped GPS: ")
                append(state?.droppedGpsCount ?: 0)
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isEmpty()) {
            startTrackingIfAllowed()
        } else {
            foregroundPermissionRequest.launch(permissions.toTypedArray())
        }
    }

    private fun startTrackingIfAllowed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, LocationTrackingService::class.java),
            )
        } else {
            binding.deviceStatus.text = "Fine location permission is required"
        }
    }
}
