package com.yubico.yubioath.ui.password

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.yubico.yubioath.R
import kotlinx.android.synthetic.main.fragment_password.*

class PasswordFragment : Fragment() {
    data class PasswordData(val current_password: String, val new_password: String, val remember: Boolean)

    private val viewModel: PasswordViewModel by lazy { ViewModelProviders.of(activity).get(PasswordViewModel::class.java) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_password, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        current_password_wrapper.visibility = if (viewModel.lastDeviceInfo.hasPassword) View.VISIBLE else View.GONE
    }

    fun validateData(): PasswordData? {
        current_password_wrapper.error = null

        if (viewModel.lastDeviceInfo.hasPassword && current_password.text.toString().isEmpty()) {
            current_password_wrapper.error = getString(R.string.password_required)
            return null
        }

        val current = if (viewModel.lastDeviceInfo.hasPassword) current_password.text.toString() else ""
        val pw1 = new_password.text.toString()
        val pw2 = verify_password.text.toString()
        val remember = remember_password.isChecked

        if (pw1 != pw2) {
            verify_password_wrapper.error = getString(R.string.password_mismatch)
        } else {
            verify_password_wrapper.error = null
            return PasswordData(current, pw1, remember)
        }

        return null
    }

    fun setWrongPassword() {
        current_password_wrapper.error = getString(R.string.password_wrong)
        current_password.apply {
            text.clear()
            requestFocus()
        }
    }
}