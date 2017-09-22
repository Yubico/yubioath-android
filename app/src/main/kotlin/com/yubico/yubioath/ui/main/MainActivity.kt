package com.yubico.yubioath.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import com.yubico.yubioath.R
import com.yubico.yubioath.ui.BaseActivity
import com.yubico.yubioath.ui.password.PasswordActivity
import com.yubico.yubioath.ui.settings.SettingsActivity
import org.jetbrains.anko.toast

class MainActivity : BaseActivity<OathViewModel>(OathViewModel::class.java) {
    companion object {
        const private val REQEUST_PASSWORD = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Clear storage from older version of app
        getSharedPreferences("NEO_STORE", Context.MODE_PRIVATE).edit().clear().apply()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchView = menu.findItem(R.id.menu_main_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean = false

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.searchFilter = newText
                return true
            }
        })

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_main_password).isEnabled = viewModel.lastDeviceInfo.version.compare(0,0,0) > 0

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.menu_main_password -> startActivityForResult(Intent(this, PasswordActivity::class.java), REQEUST_PASSWORD)
            R.id.menu_main_settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            REQEUST_PASSWORD -> if (resultCode == Activity.RESULT_OK) {
                toast(R.string.password_updated)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
