package com.yubico.yubioath

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.Tag
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import com.yubico.yubioath.exc.AppletMissingException
import com.yubico.yubioath.exc.AppletSelectException
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.exc.UnsupportedAppletException
import com.yubico.yubioath.fragments.*
import com.yubico.yubioath.model.KeyManager
import com.yubico.yubioath.model.YubiKeyNeo
import nordpol.android.AndroidCard
import nordpol.android.OnDiscoveredTagListener
import nordpol.android.TagDispatcher
import org.jetbrains.anko.*
import java.io.IOException

class MainActivity : AppCompatActivity(), OnDiscoveredTagListener {

    companion object {
        const private val SCAN_BARCODE = 1
        const private val NEO_STORE = "NEO_STORE"
    }

    private lateinit var tagDispatcher: TagDispatcher
    private lateinit var keyManager: KeyManager
    private var totpListener: OnYubiKeyNeoListener? = null
    private var readOnResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //This causes rotation animation to look like crap.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.main_activity)

        keyManager = KeyManager(getSharedPreferences(NEO_STORE, Context.MODE_PRIVATE))

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
        tagDispatcher = TagDispatcher.get(this, this, false, false, true, false, true)
    }

    override fun onBackPressed() {
        when(supportFragmentManager.findFragmentByTag(SwipeListFragment::class.java.name)) {
            null -> openFragment(SwipeListFragment())
            else -> super.onBackPressed()
        }
    }

    public override fun onPause() {
        super.onPause()
        tagDispatcher.disableExclusiveNfc()
    }

    public override fun onResume() {
        super.onResume()

        if (readOnResume) { // On activity creation, check if there is a Tag in the intent
            tagDispatcher.interceptIntent(intent)
            readOnResume = false // Don't check a second time (onNewIntent will be called)
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
        try {
            startActivityForResult(Intent("com.google.zxing.client.android.SCAN").apply {
                putExtra("SCAN_MODE", "QR_CODE_MODE")
                putExtra("SAVE_HISTORY", false)
            }, SCAN_BARCODE)
        } catch (e: ActivityNotFoundException) {
            barcodeScannerNotInstalled(
                    getString(R.string.warning),
                    getString(R.string.barcode_scanner_not_found),
                    getString(R.string.yes),
                    getString(R.string.no))
        }

    }

    private fun barcodeScannerNotInstalled(stringTitle: String,
                                           stringMessage: String, stringButtonYes: String, stringButtonNo: String) {
        AlertDialog.Builder(this).apply {
            setTitle(stringTitle)
            setMessage(stringMessage)
            setPositiveButton(stringButtonYes) { dialog, i ->
                startActivity(Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://search?q=pname:com.google.zxing.client.android"))
                )
            }
            setNegativeButton(stringButtonNo) { dialog, i ->
                dialog.cancel()
            }
        }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == SCAN_BARCODE) {
            if (resultCode == Activity.RESULT_OK) {
                openFragment(AddAccountFragment.newInstance(data.getStringExtra("SCAN_RESULT")))
            } else {
                longToast(R.string.scan_failed)
            }
        }
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
            R.id.menu_about -> openFragment(AboutFragment.newInstance(keyManager))
        }
        return true
    }

    override fun tagDiscovered(tag: Tag) {
        if (totpListener == null) {
            val fragmentManager = supportFragmentManager
            val fragment = fragmentManager.findFragmentByTag(SwipeListFragment::class.java.name)
            if (fragment != null) {
                openFragment(fragment)
            }
        }
        runOnUiThread {
            totpListener?.apply {
                try {
                    YubiKeyNeo(keyManager, AndroidCard.get(tag)).use {
                        if(it.isLocked()) {
                            it.unlock()
                        }
                        onYubiKeyNeo(it)
                    }
                } catch (e: PasswordRequiredException) {
                    onPasswordMissing(keyManager, e.id, e.isMissing)
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
        }
    }

    override fun onNewIntent(intent: Intent) {
        tagDispatcher.interceptIntent(intent)
    }

    interface OnYubiKeyNeoListener {
        @Throws(IOException::class)
        fun onYubiKeyNeo(neo: YubiKeyNeo)

        fun onPasswordMissing(manager: KeyManager, id: ByteArray, missing: Boolean)
    }
}
