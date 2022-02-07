package app.candash.cluster

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.candash.cluster.bluetooth.BluetoothService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.nio.charset.Charset
import java.util.*

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
@AndroidEntryPoint
class FullscreenActivity : AppCompatActivity() {
    private var handler: Handler = Handler()
    private var runnable: Runnable? = null
    private var delay = 1000
    private val TAG = FullscreenActivity::class.java.simpleName
    private val STANDARD_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var socket: BluetoothSocket
    private lateinit var connectedDevice: BluetoothDevice


    fun connectToDevice(bluetoothDevice: BluetoothDevice) = flow {
        emit(ConnectionState.Connecting(bluetoothDevice))
        bluetoothAdapter?.cancelDiscovery()
        try {
            socket =
                bluetoothDevice.createInsecureRfcommSocketToServiceRecord(STANDARD_UUID)?.also {
                    it.connect()
                }!!
            connectedDevice = bluetoothDevice
            socket?.let { emit(ConnectionState.Connected(it)) }
        } catch (e: Exception) {
            emit(ConnectionState.ConnectionFailed(e.message ?: "Failed to connect"))
        }
    }.flowOn(Dispatchers.IO)
    override fun getApplicationContext(): Context {
        return super.getApplicationContext()
    }


    private lateinit var viewModel: DashViewModel

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Checks the orientation of the screen
        if (isInMultiWindowMode) {
            viewModel = ViewModelProvider(this).get(DashViewModel::class.java)
            viewModel.setSplitScreen(true)
        } else {
            viewModel.setSplitScreen(false)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {


        val hotSpotReceiver = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context, intent: Intent) {
                val action = intent.action
                if ("android.net.wifi.WIFI_AP_STATE_CHANGED" == action) {
                    val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0)
                    val prevState = intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, 0)
                    if ((WifiManager.WIFI_STATE_ENABLED == state % 10) && (WifiManager.WIFI_STATE_ENABLED != prevState % 10)) {
                        viewModel.restart()
                    }
                }
            }
        }
        this.registerReceiver(
            hotSpotReceiver,
            IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
        )
        // check every second if battery is connected
        var context = applicationContext

        handler.postDelayed(Runnable {
            handler.postDelayed(runnable!!, delay.toLong())
            // get battery status to decide whether or not to disable screen dimming
            var batteryStatus: Intent? =
                IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context?.registerReceiver(null, ifilter)
                }
            // How are we charging?
            val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val isPlugged: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
                    || chargePlug == BatteryManager.BATTERY_PLUGGED_AC
                    || chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS
            //Log.d(TAG, "keep_screen_on" + isPlugged.toString())

            if (isPlugged) {
                this@FullscreenActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                //Log.d(TAG, "keep_screen_on")
            } else {
                this@FullscreenActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                //Log.d(TAG, "do not keep screen on")
            }
        }.also { runnable = it }, delay.toLong())

        setContentView(R.layout.activity_fullscreen)
        // This is a known unsafe cast, but is safe in the only correct use case:
        // TeslaDashboardApplication extends Hilt_TeslaDashboardApplication
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION xor View.SYSTEM_UI_FLAG_FULLSCREEN xor View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY xor View.SYSTEM_UI_FLAG_LAYOUT_STABLE xor View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)

        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, DashFragment())
            .commit()

        viewModel = ViewModelProvider(this).get(DashViewModel::class.java)
        viewModel.isSplitScreen()
        viewModel.fragmentNameToShow().observe(this) {
            when (it) {
                "dash" -> switchToFragment(DashFragment())
                "info" -> switchToFragment(InfoFragment())
                else -> throw IllegalStateException("Attempting to switch to unknown fragment: $it")
            }
        }
    }

    private fun switchToFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION xor View.SYSTEM_UI_FLAG_FULLSCREEN xor View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY xor View.SYSTEM_UI_FLAG_LAYOUT_STABLE xor View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        Log.d(TAG,"onResume")

        viewModel.startUp()
    }

    override fun onStart() {
        super.onStart()
        // Log.d(TAG,"onStart")
        // viewModel.startUp()
    }

    override fun onStop() {
        super.onStop()
        viewModel.shutdown()
    }
    override fun onPause() {
        super.onPause()
        // viewModel.shutdown()
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable!!)
        super.onDestroy()
    }
}