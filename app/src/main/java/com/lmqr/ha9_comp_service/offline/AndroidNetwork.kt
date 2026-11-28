package com.lmqr.ha9_comp_service.offline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager

/**
 * Mobile data is read from the global setting rather than TelephonyManager, which wants a phone
 * permission. `svc data` switches the default data SIM, and its setting is `mobile_data<subId>`:
 * plain `mobile_data` does not change when the SIM sits in the second slot.
 */
class AndroidNetwork(private val context: Context) {
    private var wifiReceiver: BroadcastReceiver? = null
    private var dataObserver: ContentObserver? = null

    private val isDataOn: Boolean get() = Settings.Global.getInt(context.contentResolver, dataSetting(), 0) == 1

    fun listen(onTurnedOn: () -> Unit) {
        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1) == WifiManager.WIFI_STATE_ENABLED)
                    onTurnedOn()
            }
        }.also { context.registerReceiver(it, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)) }

        dataObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (isDataOn) onTurnedOn()
            }
        }.also {
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(dataSetting()), false, it)
        }
    }

    fun stop() {
        wifiReceiver?.let(context::unregisterReceiver)
        dataObserver?.let(context.contentResolver::unregisterContentObserver)
        wifiReceiver = null
        dataObserver = null
    }

    private fun dataSetting(): String {
        val subscription = SubscriptionManager.getDefaultDataSubscriptionId()
        return if (subscription == SubscriptionManager.INVALID_SUBSCRIPTION_ID) MOBILE_DATA else "$MOBILE_DATA$subscription"
    }

    private companion object {
        const val MOBILE_DATA = "mobile_data"
    }
}
