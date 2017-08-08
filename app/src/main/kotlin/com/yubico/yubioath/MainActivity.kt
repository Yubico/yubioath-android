package com.yubico.yubioath

import android.annotation.SuppressLint
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.yubico.yubioath.ui.OathViewModel
import nordpol.android.AndroidCard
import nordpol.android.OnDiscoveredTagListener
import nordpol.android.TagDispatcher
import nordpol.android.TagDispatcherBuilder
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: OathViewModel
    private lateinit var tagDispatcher: TagDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProviders.of(this).get(OathViewModel::class.java)

        tagDispatcher = TagDispatcherBuilder(this, OnDiscoveredTagListener {
            viewModel.start(this)
            viewModel.nfcConnected(AndroidCard.get(it))
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

        when (tagDispatcher.enableExclusiveNfc()) {
            TagDispatcher.NfcStatus.AVAILABLE_DISABLED -> {
                if(!viewModel.nfcWarned) {
                    toast(R.string.nfc_off)
                    viewModel.nfcWarned = true
                }
            }
            TagDispatcher.NfcStatus.NOT_AVAILABLE -> {
                if(!viewModel.nfcWarned) {
                    toast(R.string.no_nfc)
                    viewModel.nfcWarned = true
                }
            }
            else -> Unit
        }
    }
}
