package com.yubico.yubioath.ui.settings

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.ListPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.WindowManager
import android.widget.TextView
import com.yubico.yubioath.R
import com.yubico.yubioath.scancode.KeyboardLayout

class SettingsFragment : PreferenceFragmentCompat() {
    private val viewModel: SettingsViewModel by lazy { ViewModelProviders.of(activity!!).get(SettingsViewModel::class.java) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        activity?.apply {
            preferenceManager.findPreference("hideThumbnail").onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, hide ->
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, if (hide == true) WindowManager.LayoutParams.FLAG_SECURE else 0)
                true
            }

            preferenceManager.findPreference("clearIcons").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                AlertDialog.Builder(this)
                        .setTitle(R.string.clear_icons)
                        .setMessage(R.string.clear_icons_message)
                        .setPositiveButton(R.string.clear) { _, _ -> viewModel.clearIcons() }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                true
            }

            preferenceManager.findPreference("clearPasswords").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                AlertDialog.Builder(this)
                        .setTitle(R.string.clear_passwords)
                        .setMessage(R.string.clear_passwords_message)
                        .setPositiveButton(R.string.clear) { _, _ -> viewModel.clearStoredPasswords() }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                true
            }

            preferenceManager.findPreference("about").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val appVersion: String = packageManager.getPackageInfo(packageName, 0).versionName
                val oathVersion = with(viewModel.lastDeviceInfo) { if (id.isNotEmpty()) "$version" else getString(com.yubico.yubioath.R.string.no_device) }

                AlertDialog.Builder(this)
                        .setTitle(R.string.about_title)
                        .setMessage(Html.fromHtml(String.format(getString(R.string.about_text), appVersion, oathVersion)))
                        .create().apply {
                    show()
                    findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                    findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                    viewModel.onDeviceRefresh = {
                        if(isShowing) {
                            setMessage(Html.fromHtml(String.format(getString(R.string.about_text), appVersion, it.version)))
                        }
                    }
                }

                true
            }

            (preferenceManager.findPreference("keyboardLayout") as ListPreference).apply {
                entryValues = KeyboardLayout.availableLayouts
                entries = entryValues
            }
        }
    }
}