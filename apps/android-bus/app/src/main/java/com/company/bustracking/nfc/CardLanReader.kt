package com.company.bustracking.nfc

import android.util.Log
import com.cardlan.twoshowinonescreen.CardLanStandardBus
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CardLanReader(
    private val onCardDetected: (String) -> Unit,
    private val onStateChanged: (State) -> Unit,
) {
    sealed interface State {
        data object Starting : State
        data object Ready : State
        data class Error(val message: String) : State
    }

    companion object {
        private const val TAG = "CardLanReader"
        private const val POLL_INTERVAL_MS = 250L
        private const val CARD_BUFFER_SIZE = 32
        private const val NO_CARD_RESULT = 1
        private val INIT_SUCCESS_RESULTS = setOf(0, -2, -3, -4)
        private val CARD_SUCCESS_RESULTS = setOf(8, 32)
    }

    private val running = AtomicBoolean(false)
    private var executor = Executors.newSingleThreadExecutor()

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }
        onStateChanged(State.Starting)
        executor.execute(::readLoop)
    }

    fun stop() {
        running.set(false)
        executor.shutdownNow()
    }

    private fun readLoop() {
        try {
            val reader = CardLanStandardBus()
            val initResult = reader.callInitDev()
            check(initResult in INIT_SUCCESS_RESULTS) {
                "618K initialization failed ($initResult)"
            }
            Log.i(TAG, "618K initialized with result $initResult")
            onStateChanged(State.Ready)

            var lastCardSn: String? = null
            while (running.get()) {
                val cardBytes = ByteArray(CARD_BUFFER_SIZE)
                val cardResult = reader.callCardReset(cardBytes)
                if (cardResult == NO_CARD_RESULT) {
                    lastCardSn = null
                } else if (cardResult in CARD_SUCCESS_RESULTS) {
                    val cardSn = cardBytes.toCardSn()
                    if (cardSn != null && cardSn != lastCardSn) {
                        Log.i(TAG, "618K card result=$cardResult CardSN=$cardSn")
                        lastCardSn = cardSn
                        onCardDetected(cardSn)
                    }
                } else {
                    Log.w(TAG, "618K card reset returned $cardResult")
                }
                Thread.sleep(POLL_INTERVAL_MS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (exception: Throwable) {
            Log.e(TAG, "CardLan reader failed", exception)
            onStateChanged(State.Error(exception.message ?: exception.javaClass.simpleName))
        } finally {
            running.set(false)
        }
    }

    private fun ByteArray.toCardSn(): String? {
        val lastDataIndex = indexOfLast { it.toInt() != 0 }
        if (lastDataIndex < 0) {
            return null
        }
        return copyOfRange(0, lastDataIndex + 1).joinToString("") { byte ->
            String.format(Locale.ROOT, "%02X", byte.toInt() and 0xFF)
        }
    }
}
