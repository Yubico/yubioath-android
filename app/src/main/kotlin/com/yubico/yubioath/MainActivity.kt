package com.yubico.yubioath

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import com.yubico.yubioath.exc.AppletMissingException
import com.yubico.yubioath.exc.AppletSelectException
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.exc.UnsupportedAppletException
import com.yubico.yubioath.fragments.*
import com.yubico.yubioath.model.KeyManager
import com.yubico.yubioath.model.YubiKeyOath
import com.yubico.yubioath.transport.UsbBackend
import com.yubico.yubioath.transport.NfcBackend
import nordpol.android.AndroidCard
import nordpol.android.OnDiscoveredTagListener
import nordpol.android.TagDispatcher
import nordpol.android.TagDispatcherBuilder
import org.jetbrains.anko.*
import java.io.IOException

class MainActivity : AppCompatActivity(), OnDiscoveredTagListener {
    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    private lateinit var state: StateFragment

    private lateinit var usbManager: UsbManager
    private lateinit var tagDispatcher: TagDispatcher
    private var totpListener: OnYubiKeyNeoListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //This causes rotation animation to look like crap.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.main_activity)

        state = supportFragmentManager.findFragmentByTag(StateFragment::class.java.name) as StateFragment? ?: StateFragment().apply {
            supportFragmentManager.beginTransaction().add(this, javaClass.name).commit()
        }

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        when(supportFragmentManager.findFragmentByTag(SwipeListFragment::class.java.name)) {
            null -> openFragment(SwipeListFragment())
            else -> Unit
        }

        /* Set up Nordpol in the following manner:
         * - opt out of NFC unavailable handling
         * - opt out of disabled sounds
         * - dispatch on UI thread
         * - opt out of broadcom workaround (this is only available in reader mode)
         * - opt out of reader mode completely
         */
        tagDispatcher = TagDispatcherBuilder(this, this).enableReaderMode(false).build()
        //tagDispatcher = TagDispatcher.get(this, this, false, false, true, false, true)
    }

    fun checkForUsbDevice():Boolean {
        usbManager.deviceList.values.find { UsbBackend.isSupported(it) }?.let {
            if(usbManager.hasPermission(it)) {
                useUsbDevice(it)
            } else {
                val mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
                usbManager.requestPermission(it, mPermissionIntent)
            }
            return true
        }
        return false
    }

    private fun useUsbDevice(device:UsbDevice) {
        useOath(YubiKeyOath(state.keyManager, UsbBackend.connect(usbManager, device)))
    }

    private fun useOath(oath:YubiKeyOath) {
        if(oath.isLocked()) {
            oath.unlock()
        }

        val listener = totpListener ?: (supportFragmentManager.findFragmentByTag(SwipeListFragment::class.java.name) as SwipeListFragment)?.current ?: SwipeListFragment().apply {
            openFragment(this)
        }.current

        try {
            listener.onYubiKeyNeo(oath)
        } catch (e: PasswordRequiredException) {
            listener.onPasswordMissing(state.keyManager, e.id, e.isMissing)
        } catch (e: IOException) {
            toast(R.string.tag_error)
            Log.e("yubioath", "IOException in handler", e)
        } catch (e: AppletMissingException) {
            toast(R.string.applet_missing)
            Log.e("yubioath", "AppletMissingException in handler", e)
        } catch (e: UnsupportedAppletException) {
            toast(R.string.unsupported_applet_version)
            Log.e("yubioath", "UnsupportedAppletException in handler", e)
        } catch (e: AppletSelectException) {
            toast(R.string.tag_error)
            Log.e("yubioath", "AppletSelectException in handler", e)
        }
    }

    override fun onBackPressed() {
        when(supportFragmentManager.findFragmentByTag(SwipeListFragment::class.java.name)) {
            null -> openFragment(SwipeListFragment())
            else -> super.onBackPressed()
        }
        Handler().postDelayed({ checkForUsbDevice() }, 10)
    }

    @SuppressLint("NewApi")
    public override fun onPause() {
        super.onPause()
        tagDispatcher.disableExclusiveNfc()
    }

    @SuppressLint("NewApi")
    public override fun onResume() {
        super.onResume()

        if(intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED && !state.ndefConsumed) {
            state.ndefConsumed = true
            tagDispatcher.interceptIntent(intent)
        }
        
        when (tagDispatcher.enableExclusiveNfc()) {
            TagDispatcher.NfcStatus.AVAILABLE_DISABLED -> {
                with(supportFragmentManager) {
                    beginTransaction().let { transaction ->
                        findFragmentByTag("dialog")?.let { transaction.remove(it) }
                        EnableNfcDialog().show(transaction, "dialog")
                    }
                }
            }
            TagDispatcher.NfcStatus.NOT_AVAILABLE -> {
                Toast.makeText(this, R.string.no_nfc, Toast.LENGTH_LONG).show()
                finish()
            }
            else -> Unit
        }
    }

    fun openFragment(fragment: Fragment) {
        totpListener = if (fragment is OnYubiKeyNeoListener) fragment else null

        with(supportFragmentManager.beginTransaction()) {
            replace(R.id.fragment_container, fragment, fragment.javaClass.name)
            commitAllowingStateLoss()
        }
    }

    private fun scanQRCode() {
        IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES).initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.contents?.let {
            openFragment(AddAccountFragment.newInstance(it))
            return
        }
        longToast(R.string.scan_failed)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_account_scan -> scanQRCode()
            R.id.menu_add_account_manual -> openFragment(AddAccountFragment())
            R.id.menu_change_password -> openFragment(SetPasswordFragment())
            R.id.menu_about -> openFragment(AboutFragment.newInstance(state.keyManager))
        }
        return true
    }

    override fun tagDiscovered(tag: Tag) {
        useOath(YubiKeyOath(state.keyManager, NfcBackend(AndroidCard.get(tag))))
    }

    override fun onNewIntent(intent: Intent) {
        tagDispatcher.interceptIntent(intent)
    }

    interface OnYubiKeyNeoListener {
        @Throws(IOException::class)
        fun onYubiKeyNeo(oath: YubiKeyOath)

        fun onPasswordMissing(manager: KeyManager, id: ByteArray, missing: Boolean)
    }
}
