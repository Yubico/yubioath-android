package com.yubico.yubioath.fragments

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import com.yubico.yubioath.MainActivity
import com.yubico.yubioath.model.KeyManager

/**
 * Created by Dain on 2017-04-21.
 */
class StateFragment : Fragment() {
    companion object {
        const private val KEY_STORE = "NEO_STORE"
    }

    lateinit var keyManager: KeyManager
    var ndefConsumed = false
    var nfcWarned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retainInstance = true

        keyManager = KeyManager(context.getSharedPreferences(KEY_STORE, Context.MODE_PRIVATE))
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).checkForUsbDevice()
    }
}