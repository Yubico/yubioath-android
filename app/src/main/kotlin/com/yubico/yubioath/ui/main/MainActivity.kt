package com.yubico.yubioath.ui.main

import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.SearchView
import android.view.Menu
import com.yubico.yubioath.R
import com.yubico.yubioath.ui.BaseActivity

class MainActivity : BaseActivity<OathViewModel>(OathViewModel::class.java) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchView = MenuItemCompat.getActionView(menu.findItem(R.id.menu_main_search)) as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean = false

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.searchFilter = newText
                return true
            }
        })

        return true
    }
}
