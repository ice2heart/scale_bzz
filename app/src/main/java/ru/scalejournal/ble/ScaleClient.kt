package ru.scalejournal.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import ru.scalejournal.util.AppLog
import ru.scalejournal.util.toHex
import java.util.ArrayDeque
import java.util.Calendar
import java.util.UUID

@SuppressLint("MissingPermission")
class ScaleClient(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(text: String)
        fun onMeasured(weightKg: Double, impedanceOhm: Double?, source: String)
        fun onSessionEnded(client: ScaleClient, connectionAttempted: Boolean)
    }

    companion object {
        val SVC_BODY = UUID.fromString("0000181b-0000-1000-8000-00805f9b34fb")
        val CHR_LOCKED = UUID.fromString("00002a9c-0000-1000-8000-00805f9b34fb")
        val SVC_UART = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val CHR_UART_RX = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val CHR_UART_TX = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val SVC_TIME = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        val CHR_TIME = UUID.fromString("00002a08-0000-1000-8000-00805f9b34fb")
        val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val UNIT_SYNC_KG = byteArrayOf(0x01, 0x00)
        private const val TIMEOUT_MS = 180_000L
        private const val BROADCAST_STABLE_COUNT = 3
        private const val OP_WATCHDOG_MS = 4000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var lastFrame: ByteArray? = null
    private var frameChanged = false
    private var stableCount = 0
    private var measured = false
    private var ended = false
    private var connectionAttempted = false
    private val broadcastCounts = HashMap<Long, Int>()

    private val pendingOps = ArrayDeque<() -> Boolean>()
    private var opInProgress = false
    private var watchdog: Runnable? = null

    fun start() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            AppLog.log("BLE", "старт отменён: адаптер Bluetooth выключен")
            listener.onStatus("Включите Bluetooth")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            AppLog.log("BLE", "старт отменён: BLE-сканер недоступен")
            listener.onStatus("BLE-сканер недоступен")
            return
        }
        ended = true
        stop()
        ended = false
        measured = false
        connectionAttempted = false
        lastFrame = null
        frameChanged = false
        stableCount = 0
        if (broadcastCounts.size > 64) broadcastCounts.clear()
        AppLog.log("BLE", "=== старт сессии поиска ===")
        listener.onStatus("Ищу весы… встаньте на весы")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord
                val name = record?.deviceName ?: ""
                if (!isCandidate(name, record)) return
                AppLog.log(
                    "SCAN",
                    "${result.device?.address} name=\"$name\" rssi=${result.rssi} ${mfgString(record)}",
                )
                handleBroadcast(record)
                if (measured || gatt != null) return
                val device = result.device ?: return
                listener.onStatus("Найдены весы ${device.address}, подключаюсь…")
                AppLog.log("BLE", "подключение к ${device.address}")
                try {
                    scanner.stopScan(this)
                } catch (_: Exception) {
                }
                scanCallback = null
                connectionAttempted = true
                gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }

            override fun onScanFailed(errorCode: Int) {
                AppLog.log("SCAN", "ошибка сканирования: код $errorCode")
                listener.onStatus("Ошибка сканирования: $errorCode")
            }
        }
        scanCallback = callback
        scanner.startScan(emptyList(), settings, callback)
        handler.postDelayed({
            if (!measured && !ended) {
                AppLog.log("BLE", "таймаут сессии: весы не найдены")
                listener.onStatus("Весы не найдены — встаньте на весы и попробуйте снова")
                stop()
            }
        }, TIMEOUT_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        pendingOps.clear()
        opInProgress = false
        scanCallback?.let { callback ->
            try {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                manager.adapter?.bluetoothLeScanner?.stopScan(callback)
                AppLog.log("BLE", "сканирование остановлено")
            } catch (e: Exception) {
                AppLog.log("BLE", "ошибка остановки скана: $e")
            }
        }
        scanCallback = null
        gatt?.let {
            AppLog.log("BLE", "отключение GATT")
            it.disconnect()
        }
        if (!ended) {
            ended = true
            listener.onSessionEnded(this, connectionAttempted)
        }
    }

    private fun isCandidate(name: String, record: ScanRecord?): Boolean {
        val n = name.lowercase()
        if (n == "chipsea-ble" || n.startsWith("okok") || n == "adv" || n.startsWith("yoda")) return true
        return record?.getManufacturerSpecificData(ChipseaParser.MFG_ID_VF0) != null
    }

    private fun mfgString(record: ScanRecord?): String {
        val mfg = record?.manufacturerSpecificData ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until mfg.size()) {
            parts.add(String.format("0x%04X=[%s]", mfg.keyAt(i), mfg.valueAt(i).toHex()))
        }
        return if (parts.isEmpty()) "mfg нет" else "mfg " + parts.joinToString(" ")
    }

    private fun handleBroadcast(record: ScanRecord?) {
        if (measured) return
        val payload = record?.getManufacturerSpecificData(ChipseaParser.MFG_ID_VF0) ?: return
        val reading = ChipseaParser.parseBroadcast(payload)
        if (reading == null) {
            AppLog.log("ADV", "VF0 не разобран: ${payload.toHex()}")
            return
        }
        val key = Math.round(reading.weightKg * 10)
        val count = (broadcastCounts[key] ?: 0) + 1
        broadcastCounts[key] = count
        AppLog.log("ADV", "VF0 вес=${reading.weightKg} кг повтор=$count сырьё=${payload.toHex()}")
        if (count >= BROADCAST_STABLE_COUNT) {
            measured = true
            AppLog.log("BLE", "измерение принято (броадкаст): ${reading.weightKg} кг")
            listener.onMeasured(reading.weightKg, null, "ble-adv")
            stop()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            AppLog.log("GATT", "connectionState=$newState status=$status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    listener.onStatus("Подключено, обнаружение сервисов…")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    if (gatt === g) gatt = null
                    if (!measured && !ended) {
                        AppLog.log("GATT", "устройство отключилось (status=$status), измерения не было")
                        listener.onStatus("Отключено от весов")
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            AppLog.log("GATT", "servicesDiscovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onStatus("Ошибка обнаружения сервисов: $status")
                g.disconnect()
                return
            }
            for (service in g.services) {
                val chars = service.characteristics.joinToString(" ") { c ->
                    String.format("%s(%s)", c.uuid, charProps(c.properties))
                }
                AppLog.log("GATT", "svc ${service.uuid}: $chars")
            }
            val locked = g.getService(SVC_BODY)?.getCharacteristic(CHR_LOCKED)
            if (locked == null) {
                AppLog.log("GATT", "характеристика 2A9C не найдена — отключаюсь")
                listener.onStatus("Характеристика измерений не найдена")
                g.disconnect()
                return
            }

            pendingOps.clear()
            opInProgress = false

            g.getService(SVC_TIME)?.getCharacteristic(CHR_TIME)?.let { timeChar ->
                enqueue {
                    val ok = performWrite(g, timeChar, buildTimeFrame())
                    AppLog.log("GATT", "синхронизация времени 2A08: ok=$ok")
                    ok
                }
            }
            g.getService(SVC_UART)?.getCharacteristic(CHR_UART_TX)?.let { tx ->
                enqueue {
                    val ok = performWrite(g, tx, UNIT_SYNC_KG)
                    AppLog.log("GATT", "синхронизация единиц (кг) FFF2: ok=$ok")
                    ok
                }
            }
            enqueue {
                val ok = performEnable(g, locked, indication = true)
                if (!ok) {
                    AppLog.log("GATT", "критично: CCCD 2A9C не включён, разрываю соединение")
                    pendingOps.clear()
                    g.disconnect()
                }
                ok
            }
            g.getService(SVC_UART)?.getCharacteristic(CHR_UART_RX)?.let { rx ->
                enqueue {
                    val ok = performEnable(g, rx, indication = false)
                    AppLog.log("GATT", "notify FFF1: ok=$ok")
                    ok
                }
            }
            runNext()
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            AppLog.log("GATT", "write ${shortUuid(characteristic.uuid)} status=$status")
            completeOp()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val chr = descriptor.characteristic?.uuid
            AppLog.log("GATT", "descriptorWrite ${chr?.let { shortUuid(it) }} status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS && chr == CHR_LOCKED) {
                listener.onStatus("Встаньте на весы")
            }
            completeOp()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleFrame(characteristic.uuid, characteristic.value ?: return)
        }
    }

    private fun enqueue(op: () -> Boolean) {
        pendingOps.addLast(op)
    }

    private fun runNext() {
        if (opInProgress) return
        val next = pendingOps.pollFirst() ?: return
        opInProgress = true
        val started = try {
            next()
        } catch (e: Exception) {
            AppLog.log("GATT", "ошибка операции: $e")
            false
        }
        if (!started) {
            opInProgress = false
            runNext()
        } else {
            armWatchdog()
        }
    }

    private fun completeOp() {
        cancelWatchdog()
        opInProgress = false
        runNext()
    }

    private fun armWatchdog() {
        cancelWatchdog()
        watchdog = Runnable {
            if (opInProgress) {
                AppLog.log("GATT", "операция не завершилась за ${OP_WATCHDOG_MS} мс, двигаюсь дальше")
                opInProgress = false
                runNext()
            }
        }.also { handler.postDelayed(it, OP_WATCHDOG_MS) }
    }

    private fun cancelWatchdog() {
        watchdog?.let { handler.removeCallbacks(it) }
        watchdog = null
    }

    private fun performWrite(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        val ok = g.writeCharacteristic(characteristic)
        AppLog.log("GATT", "write ${shortUuid(characteristic.uuid)} запущен ok=$ok значение=${value.toHex()}")
        return ok
    }

    private fun performEnable(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        indication: Boolean,
    ): Boolean {
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD)
        if (descriptor == null) {
            AppLog.log("GATT", "CCCD не найден для ${shortUuid(characteristic.uuid)}")
            return false
        }
        descriptor.value = if (indication) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        val ok = g.writeDescriptor(descriptor)
        AppLog.log(
            "GATT",
            "cccd ${shortUuid(characteristic.uuid)} indication=$indication запущен ok=$ok",
        )
        return ok
    }

    private fun buildTimeFrame(): ByteArray {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        return byteArrayOf(
            (year and 0xFF).toByte(),
            ((year shr 8) and 0xFF).toByte(),
            (cal.get(Calendar.MONTH) + 1).toByte(),
            cal.get(Calendar.DAY_OF_MONTH).toByte(),
            cal.get(Calendar.HOUR_OF_DAY).toByte(),
            cal.get(Calendar.MINUTE).toByte(),
            cal.get(Calendar.SECOND).toByte(),
        )
    }

    private fun shortUuid(uuid: UUID): String = uuid.toString().substring(4, 8).uppercase()

    private fun charProps(props: Int): String {
        val parts = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) parts.add("read")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) parts.add("write")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) parts.add("writeNR")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) parts.add("notify")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) parts.add("indicate")
        return parts.joinToString("+")
    }

    private fun handleFrame(uuid: UUID, data: ByteArray) {
        if (uuid == CHR_LOCKED) {
            AppLog.log("GATT", "фрейм 2A9C len=${data.size}: ${data.toHex()}")
            val reading = ChipseaParser.parseLocked(data)
            if (reading == null) {
                AppLog.log("PARSE", "фрейм 2A9C не разобран: ${data.toHex()}")
                return
            }
            publish(reading)
            return
        }
        if (uuid != CHR_UART_RX) {
            AppLog.log("GATT", "фрейм с неожиданной характеристики $uuid: ${data.toHex()}")
            return
        }
        if (lastFrame == null) {
            lastFrame = data
            frameChanged = false
            stableCount = 0
            AppLog.log("GATT", "первый фрейм FFF1 (возможно повтор прошлого измерения): ${data.toHex()}")
            return
        }
        if (data.contentEquals(lastFrame)) {
            if (frameChanged) {
                stableCount++
                if (stableCount == 2 || stableCount == 10) {
                    AppLog.log("GATT", "стабильный вес повтор x$stableCount: ${data.toHex()}")
                }
                if (stableCount >= 2 && !measured) {
                    val reading = ChipseaParser.parseLocked(data)
                    if (reading == null) {
                        AppLog.log("PARSE", "стабильный фрейм не разобран: ${data.toHex()}")
                    } else {
                        publish(reading)
                    }
                }
            }
            return
        }
        frameChanged = true
        stableCount = 0
        lastFrame = data
        AppLog.log("GATT", "новый фрейм FFF1: ${data.toHex()}")
    }

    private fun publish(reading: ChipseaParser.Reading) {
        if (measured) {
            AppLog.log("PARSE", "фрейм после принятого измерения, пропущен: ${reading.weightKg} кг")
            return
        }
        measured = true
        AppLog.log(
            "BLE",
            "измерение принято: ${reading.weightKg} кг, импеданс ${reading.impedanceOhm} Ом",
        )
        listener.onMeasured(reading.weightKg, reading.impedanceOhm, "ble-gatt")
        gatt?.disconnect()
    }
}
