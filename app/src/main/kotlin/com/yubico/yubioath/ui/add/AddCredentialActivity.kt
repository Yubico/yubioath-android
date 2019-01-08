package com.yubico.yubioath.ui.add

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.yubico.yubioath.R
import com.yubico.yubioath.exc.DuplicateKeyException
import com.yubico.yubioath.ui.BaseActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddCredentialActivity : BaseActivity<AddCredentialViewModel>(AddCredentialViewModel::class.java) {
    companion object {
        const val EXTRA_CREDENTIAL = "credential"
        const val EXTRA_CODE = "code"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_credential)
        setTitle(R.string.new_credential)

        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.ic_close_24dp)
            setDisplayHomeAsUpEnabled(true)
        }

        intent.data?.let {
            viewModel.handleScanResults(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_add_credential, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.menu_add_credential_save -> (supportFragmentManager.findFragmentById(R.id.fragment) as AddCredentialFragment).apply {
                val data = validateData()
                if (data != null) {
                    isEnabled = false

                    val job = viewModel.addCredential(data)
                    job.invokeOnCompletion {
                        it?.let {
                            launch {
                                isEnabled = true
                            }
                        }
                    }

                    launch {
                        if (viewModel.lastDeviceInfo.persistent) {
                            delay(100)
                        }
                        if (job.isActive) {
                            Snackbar.make(view!!, R.string.swipe_and_hold, Snackbar.LENGTH_INDEFINITE).apply {
                                setActionTextColor(ContextCompat.getColor(context, R.color.yubicoPrimaryGreen))
                                job.invokeOnCompletion { dismiss() }
                                setAction(R.string.cancel) { job.cancel() }
                            }.show()
                        }
                        try {
                            val (credential, code) = job.await()
                            setResult(Activity.RESULT_OK, Intent().apply {
                                putExtra(EXTRA_CREDENTIAL, credential)
                                code?.let {
                                    putExtra(EXTRA_CODE, it)
                                }
                            })
                            finish()
                        } catch (e: CancellationException) {
                        } catch (e: DuplicateKeyException) {
                            markDuplicateName()
                        } catch (e: Exception) {
                            Log.e("yubioath", "exception", e)
                            validateVersion(data, viewModel.lastDeviceInfo.version)
                        }
                    }
                }
            }
        }
        return true
    }
}