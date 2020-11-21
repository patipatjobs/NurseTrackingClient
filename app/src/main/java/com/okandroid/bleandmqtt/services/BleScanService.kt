package com.phyathai.NurseTrackingClient.services

import android.R
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import com.google.gson.GsonBuilder
import com.phyathai.NurseTrackingClient.adapters.BleDevicesAdapter
import com.phyathai.NurseTrackingClient.models.*
import com.phyathai.NurseTrackingClient.utils.Constants
import com.phyathai.NurseTrackingClient.utils.MQTTUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class BleScanService : Service() {

    private val DEFAULT_VALUE = "000"
    private var gson = GsonBuilder().setPrettyPrinting().create()
    private val handler = Handler()
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        scanLeDevice()
        return Service.START_STICKY
    }

    private fun scanLeDevice() {
        handler.postDelayed({
            bluetoothAdapter?.stopLeScan(scanCallback)
            val scanFinished = Intent(Constants.BLE_INTENT_FILTER)
            scanFinished.putExtra(Constants.IntentExtras.SCAN_FINISHED,true)
            broadcastData(scanFinished)
        }, Constants.SCAN_PERIOD)
//        bluetoothAdapter?.startLeScan(arrayOf(UUID.fromString("00001803-0000-1000-8000-00805F9B34FB")), scanCallback)
        bluetoothAdapter?.startLeScan(scanCallback)
    }

    private val scanCallback = BluetoothAdapter.LeScanCallback { bluetoothDevice, rssi, p2 ->
        val payload = Intent(Constants.BLE_INTENT_FILTER)
        val BLEname = bluetoothDevice.name
        val BLEaddress = bluetoothDevice.address
        val BLErssi = rssi.toString()
        FilterScanCallback(true,payload,BLEname,BLEaddress,BLErssi)
        broadcastData(payload)
    }

    private fun FilterScanCallback(FILTER: Boolean,payload: Intent,BLEname: String?, BLEaddress: String,  BLErssi: String) {
        if( !BLEname.isNullOrEmpty() ) {
            if(FILTER==false){
                payload.putExtra(
                    Constants.IntentExtras.BLE_DEVICE,
                    BleDevice(BLEname, BLEaddress, BLErssi)
                )
            }else{
                val sharedPreferences: SharedPreferences =
                    applicationContext.getSharedPreferences("phyathai", 0)
                val iTAGListServerSetting =
                    sharedPreferences.getString("iTAGListServerSetting", DEFAULT_VALUE)
                if (iTAGListServerSetting != DEFAULT_VALUE) {
                    val iTAGListServer =
                        gson.fromJson(iTAGListServerSetting, SendPublish::class.java).itag.itag_list
                    iTAGListServer?.forEach {
                        val MacAddressServer = it.mac_address
                        val MacAddressClient = BLEaddress
                        if (MacAddressServer == MacAddressClient) {
                            val distance = it.distance!!
                            val rssi = BLErssi.toInt()!!
                            if ((rssi > distance)) {
                                payload.putExtra(
                                    Constants.IntentExtras.BLE_DEVICE,
                                    BleDevice(BLEname, BLEaddress, BLErssi)
                                )
                            }
                        }
                    }
                }else{
                    payload.putExtra(
                        Constants.IntentExtras.BLE_DEVICE,
                        BleDevice(BLEname, BLEaddress, BLErssi)
                    )
                }
            }
        }
    }

    private fun broadcastData(payload: Intent) {
        LocalBroadcastManager.getInstance(this@BleScanService).sendBroadcast(payload)
    }

    private fun startForegroundService() {
        val channelId = "com.example.simpleapp"
        val channelName = "My Background Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.ic_media_play)
            .setContentTitle("Scanning BLE devices")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(1, notification)
    }

    private fun stopForegroundService() {
        stopForeground(true)
        stopSelf()
    }

}