package com.yubico.yubioath.ui.settings

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.yubico.yubioath.R
import com.yubico.yubioath.scancode.KeyboardLayout
import com.yubico.yubioath.ui.main.IconManager
import org.jetbrains.anko.toast

class SettingsFragment : PreferenceFragmentCompat() {
    private val viewModel: SettingsViewModel by lazy { ViewModelProviders.of(activity!!).get(SettingsViewModel::class.java) }

    private inline fun onPreferenceChange(key: String, crossinline func: (value: Any) -> Unit) {
        preferenceManager.findPreference(key).onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            func(value)
            true
        }
    }

    private inline fun onPreferenceClick(key: String, crossinline func: () -> Unit) {
        preferenceManager.findPreference(key).onPreferenceClickListener = Preference.OnPreferenceClickListener {
            func()
            true
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        activity?.apply {
            onPreferenceChange("hideThumbnail") {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, if (it == true) WindowManager.LayoutParams.FLAG_SECURE else 0)
            }

            onPreferenceClick("clearIcons") {
                AlertDialog.Builder(this)
                        .setTitle(R.string.clear_icons)
                        .setMessage(R.string.clear_icons_message)
                        .setPositiveButton(R.string.clear) { _, _ ->
                            IconManager(context!!).clearIcons()
                            toast(R.string.icons_cleared)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
            }

            onPreferenceClick("clearPasswords") {
                AlertDialog.Builder(this)
                        .setTitle(R.string.clear_passwords)
                        .setMessage(R.string.clear_passwords_message)
                        .setPositiveButton(R.string.clear) { _, _ -> viewModel.setClearPasswords(true) }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
            }

            onPreferenceClick("about") {
                val appVersion: String = packageManager.getPackageInfo(packageName, 0).versionName
                val oathVersion = with(viewModel.deviceInfo.value!!) { if (id.isNotEmpty()) "$version" else getString(com.yubico.yubioath.R.string.no_device) }

                AlertDialog.Builder(this)
                        .setTitle(R.string.about_title)
                        .setMessage(Html.fromHtml(String.format(getString(R.string.about_text), appVersion, oathVersion)))
                        .create().apply {
                            show()
                            findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                            viewModel.deviceInfo.observe(viewLifecycleOwner, Observer {
                                if (isShowing) {
                                    setMessage(Html.fromHtml(String.format(getString(R.string.about_text), appVersion, it.version)))
                                }
                            })
                        }
            }

            onPreferenceChange("readNdefData") {
                preferenceManager.findPreference("keyboardLayout").isEnabled = it == true
            }

            (preferenceManager.findPreference("keyboardLayout") as ListPreference).apply {
                entryValues = KeyboardLayout.availableLayouts
                entries = entryValues
                isEnabled = preferenceManager.sharedPreferences.getBoolean("readNdefData", false)
            }
        }
    }
}