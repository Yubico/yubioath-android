package com.yubico.yubioath.ui.settings

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.util.Log
import android.view.WindowManager
import com.yubico.yubioath.R

/**
 * Created by Dain on 2017-08-15.
 */
class SettingsFragment : PreferenceFragmentCompat() {
    private val viewModel: SettingsViewModel by lazy { ViewModelProviders.of(activity).get(SettingsViewModel::class.java) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        preferenceManager.findPreference("hideThumbnail").onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, hide ->
            activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, if(hide == true) WindowManager.LayoutParams.FLAG_SECURE else 0)
            true
        }

        preferenceManager.findPreference("memclear").onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AlertDialog.Builder(context)
                    .setTitle(R.string.clear_data)
                    .setMessage(R.string.clear_data_message)
                    .setPositiveButton(R.string.clear) {_, _ -> viewModel.clearStoredPasswords() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            true
        }
    }
}