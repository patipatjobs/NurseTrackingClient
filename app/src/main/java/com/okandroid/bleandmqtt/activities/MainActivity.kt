package com.phyathai.NurseTrackingClient.activities

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.phyathai.NurseTrackingClient.R
import com.phyathai.NurseTrackingClient.adapters.BleDevicesAdapter
import com.phyathai.NurseTrackingClient.models.BleDevice
import com.phyathai.NurseTrackingClient.services.BleScanService
import com.phyathai.NurseTrackingClient.utils.Constants
import com.phyathai.NurseTrackingClient.utils.MQTTUtils
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private var mqttUtil: MQTTUtils? = null
    private var bleDeviceAdapter: BleDevicesAdapter? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when {
                    intent.hasExtra(Constants.IntentExtras.BLE_DEVICE) -> {
                        val bleDevice = intent.getSerializableExtra(Constants.IntentExtras.BLE_DEVICE) as BleDevice
                        if ( bleDevice != null ) {
                            if (bleDeviceAdapter?.isDeviceAlreadyFound(bleDevice) == false) {
                                bleDeviceAdapter?.bleDevices?.add(bleDevice)
                            }
                            bleDeviceAdapter?.notifyDataSetChanged()
                        }
//                        mqttUtil?.FilterDeviceModel(false,bleDevice,bleDeviceAdapter)
                    }
                    intent.hasExtra(Constants.IntentExtras.SCAN_FINISHED) -> {
                        Thread.sleep(1000)
                        if (refreshBle.isRefreshing) refreshBle.isRefreshing = true

                        if (bleDeviceAdapter?.bleDevices?.size == 0) {
                            llNoData.visibility = View.VISIBLE
                            llData.visibility = View.GONE
                        } else {
                            llNoData.visibility = View.GONE
                            llData.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mqttUtil = MQTTUtils.getInstance(this@MainActivity)
        //
        val sharedPref: SharedPreferences = getSharedPreferences("phyathai", 0)
        val editor = sharedPref.edit()
        //
        editor.clear().apply()
        //
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        editor.putString("deviceId",deviceId).apply()
        editor.commit()
        Log.d("server deviceId",deviceId)
        //
        initViews()
        Thread.sleep(1000)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                Constants.PermissionRequestCode.LOCATION
            )
        } else {
            initBLEScan()
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(bleBroadcastReceiver, IntentFilter(Constants.BLE_INTENT_FILTER))
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bleBroadcastReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        mqttUtil?.disconnect()
        stopService(Intent(this, BleScanService::class.java))
        super.onDestroy()
    }

    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

    private fun initViews() {
        bleDeviceAdapter = BleDevicesAdapter(this)
        rvBleDevices.adapter = bleDeviceAdapter
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                mqttUtil?.BLEDevice(bleDeviceAdapter)
                initBLEScan()
                mainHandler.postDelayed(this, 7000)
            }
        })
        btnSendData.setOnClickListener {
            mqttUtil?.BLEDevice(bleDeviceAdapter)
        }
        refreshBle.setOnRefreshListener {
            initBLEScan()
        }
    }

    private fun initBLEScan() {
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show()
            finish()
        }

        if (bluetoothAdapter?.isEnabled == true) {
            refreshBle.isRefreshing = false
            bleDeviceAdapter?.bleDevices?.clear()
            startBleService()
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, Constants.ActivityResultRequestCode.ENABLE_BT)
        }
    }

    private fun startBleService() {
        try {
            val startScan = Intent(this, BleScanService::class.java)
            startService(startScan)
        } catch (e: Exception) {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.ActivityResultRequestCode.ENABLE_BT -> {
                if (bluetoothAdapter?.isEnabled == true) {
                    startBleService()
                } else {
                    Toast.makeText(this, R.string.ble_required, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Constants.PermissionRequestCode.LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    initBLEScan()
                } else {
                    Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
                    finish()
                }
                return
            }
        }
    }
}