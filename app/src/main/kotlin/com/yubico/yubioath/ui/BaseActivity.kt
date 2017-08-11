package com.yubico.yubioath.ui

import android.annotation.SuppressLint
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.yubico.yubioath.R
import nordpol.android.AndroidCard
import nordpol.android.OnDiscoveredTagListener
import nordpol.android.TagDispatcher
import nordpol.android.TagDispatcherBuilder
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.toast

abstract class BaseActivity<T : BaseViewModel>(private var modelClass: Class<T>) : AppCompatActivity() {
    companion object {
        const private val DISABLE_NFC_WARNING = "DISABLE_NFC_WARNING"
    }

    protected lateinit var viewModel: T
    private lateinit var tagDispatcher: TagDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(modelClass)

        tagDispatcher = TagDispatcherBuilder(this, OnDiscoveredTagListener {
            try {
                viewModel.start(this)
                viewModel.nfcConnected(AndroidCard.get(it))
            } catch (e: Exception) {
                Log.e("yubioath", "Error using NFC device", e)
            }
        }).enableReaderMode(false).enableUnavailableNfcUserPrompt(false).build()
    }

    override fun onNewIntent(intent: Intent) {
        tagDispatcher.interceptIntent(intent)
    }

    @SuppressLint("NewApi")
    public override fun onPause() {
        super.onPause()
        tagDispatcher.disableExclusiveNfc()
        viewModel.stop()
    }

    @SuppressLint("NewApi")
    public override fun onResume() {
        super.onResume()
        viewModel.start(this)

        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED && !viewModel.ndefConsumed) {
            viewModel.ndefConsumed = true
            tagDispatcher.interceptIntent(intent)
        }

        //TODO: Add checkbox to disable warning in dialog, and setting to toggle.
        val dontWarn = defaultSharedPreferences.getBoolean(DISABLE_NFC_WARNING, false) || viewModel.nfcWarned
        when (tagDispatcher.enableExclusiveNfc()) {
            TagDispatcher.NfcStatus.AVAILABLE_DISABLED -> {
                if(!dontWarn) {
                    toast(R.string.nfc_off)
                    viewModel.nfcWarned = true
                }
            }
            TagDispatcher.NfcStatus.NOT_AVAILABLE -> {
                if(!dontWarn) {
                    toast(R.string.no_nfc)
                    viewModel.nfcWarned = true
                }
            }
            else -> Unit
        }
    }
}