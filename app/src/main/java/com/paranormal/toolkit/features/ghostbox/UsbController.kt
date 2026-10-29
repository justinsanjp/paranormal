package com.paranormal.toolkit.features.ghostbox

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface UsbConnectionState {
    data object NoDevice : UsbConnectionState
    data class PermissionRequired(val device: UsbDevice) : UsbConnectionState
    data class Ready(val device: UsbDevice) : UsbConnectionState
    data class Error(val message: String) : UsbConnectionState
}

/** Known RTL2832U-based "RTL-SDR" dongle vendor/product IDs (Realtek reference + common rebrands). */
private val KNOWN_SDR_DEVICES = setOf(
    0x0bda to 0x2832,
    0x0bda to 0x2838,
    0x1d19 to 0x1101,
    0x1d19 to 0x1102,
    0x1d19 to 0x1103,
    0x1d19 to 0x1104,
    0x0ccd to 0x00a9,
    0x0ccd to 0x00b3,
)

/**
 * Detects a supported USB radio dongle and walks it through Android's USB
 * host permission flow. This never fabricates a device - with nothing
 * attached, [state] simply stays [UsbConnectionState.NoDevice] and GhostBox
 * has no signal to show.
 */
class UsbController(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionUsbPermission = "${context.packageName}.USB_PERMISSION"

    private val _state = MutableStateFlow<UsbConnectionState>(UsbConnectionState.NoDevice)
    val state: StateFlow<UsbConnectionState> = _state

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            when (intent.action) {
                actionUsbPermission -> {
                    val device = intent.usbDeviceExtra()
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    _state.value = if (device != null && granted) {
                        UsbConnectionState.Ready(device)
                    } else {
                        UsbConnectionState.Error("USB-Zugriff wurde verweigert.")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> refresh()
            }
        }
    }

    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(actionUsbPermission)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            registered = true
        }
        refresh()
    }

    fun stop() {
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
    }

    fun refresh() {
        val device = findSupportedDevice()
        _state.value = when {
            device == null -> UsbConnectionState.NoDevice
            usbManager.hasPermission(device) -> UsbConnectionState.Ready(device)
            else -> UsbConnectionState.PermissionRequired(device)
        }
    }

    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(actionUsbPermission).setPackage(context.packageName),
            flags,
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    fun openConnection(device: UsbDevice): UsbDeviceConnection? = usbManager.openDevice(device)

    private fun findSupportedDevice(): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { (it.vendorId to it.productId) in KNOWN_SDR_DEVICES }

    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}
