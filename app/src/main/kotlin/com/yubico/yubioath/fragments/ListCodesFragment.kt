/*
 * Copyright (c) 2013, Yubico AB.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package com.yubico.yubioath.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.DialogFragment
import android.support.v4.app.ListFragment
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import android.widget.ArrayAdapter
import android.widget.ListView
import com.yubico.yubioath.MainActivity
import com.yubico.yubioath.R
import com.yubico.yubioath.model.KeyManager
import com.yubico.yubioath.model.YubiKeyOath
import kotlinx.android.synthetic.main.list_codes_fragment.*
import kotlinx.android.synthetic.main.oath_code_view.view.*
import org.jetbrains.anko.*
import java.io.IOException
import java.util.*

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/26/13
 * Time: 11:08 AM
 * To change this template use File | Settings | File Templates.
 */
class ListCodesFragment : ListFragment(), MainActivity.OnYubiKeyNeoListener, ActionMode.Callback {

    companion object {
        const private val READ_LIST = 0
        const private val READ_SELECTED = 1
        const private val DELETE_SELECTED = 2
    }

    private val timeoutAnimation = TimeoutAnimation()
    private var initialCodes: List<Map<String, String>> = emptyList()
    private var initialTimestamp:Long = 0
    private lateinit var adapter: CodeAdapter
    private lateinit var swipeDialog: SwipeDialog
    private var actionMode: ActionMode? = null
    private var state = READ_LIST
    private var selectedItem: OathCode? = null
    private var needsPassword: DialogFragment? = null
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.list_codes_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState == null) {
            swipeDialog = SwipeDialog()
            adapter = CodeAdapter(ArrayList<OathCode>())
            listAdapter = adapter
        } else {
            //Correct timer
            val now = System.currentTimeMillis()
            if (now - startTime < 30000) {
                timeoutAnimation.startOffset = startTime - now
                timeoutBar.startAnimation(timeoutAnimation)
            } else {
                timeoutBar.clearAnimation()
                timeoutBar.progress = 0
            }
            selectedItem?.let { //Deselect selected item as it doesn't keep well.
                actionMode?.finish()
                listView.setItemChecked(adapter.getPosition(it), false)
                selectedItem = null
            }
        }

        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setOnItemClickListener { parent, view, position, id ->
            actionMode?.apply {
                selectedItem = adapter.getItem(position)
                title = selectedItem?.label
                listView.setItemChecked(position, true)
            } ?: listView.setItemChecked(position, false)
        }
        listView.setOnItemLongClickListener { parent, view, position, id ->
            selectedItem = adapter.getItem(position)
            if (actionMode == null) {
                actionMode = activity.startActionMode(this)
            }
            actionMode?.title = selectedItem?.label
            listView.setItemChecked(position, true)
            true
        }

        if (initialCodes.isNotEmpty()) {
            showCodes(initialCodes, initialTimestamp)
            initialCodes = emptyList()
        } else needsPassword?.let {
            val ft = fragmentManager.beginTransaction()
            it.show(ft, "dialog")
            needsPassword = null
        }
    }

    @Throws(IOException::class)
    override fun onYubiKeyNeo(oath: YubiKeyOath) {
        val now = System.currentTimeMillis()
        val timestamp = if (oath.persistent) {
            now / 30000
        } else {
            //If less than 10 seconds remain until we'd get the next OTP, skip ahead.
            (now / 1000 + 10) / 30
        }
        val timerStart = if (oath.persistent) timestamp * 30000 else now

        if (activity == null || !swipeDialog.isAdded) {
            //If the swipeDialog isn't shown we ignore the state and just list the OTPs.
            state = READ_LIST
        }

        when (state) {
            READ_LIST -> {
                val codes = oath.getCodes(timestamp)
                showCodes(codes, timerStart)
                if (codes.isEmpty()) {
                    Handler().postDelayed(//Give the app some time to get the activity ready.
                            {
                                activity?.longToast(R.string.empty_list)
                            }, 100)
                }
            }
            READ_SELECTED -> {
                selectedItem?.let {
                    it.code = oath.readHotpCode(it.label)
                    adapter.notifyDataSetChanged()
                }
                swipeDialog.dismiss()
                state = READ_LIST
            }
            DELETE_SELECTED -> {
                selectedItem?.let {
                    oath.deleteCode(it.label)
                    selectedItem = null
                }
                swipeDialog.dismiss()
                activity?.toast(R.string.deleted)
                showCodes(oath.getCodes(timestamp), timerStart)
                state = READ_LIST
            }
        }
    }

    fun showCodes(codeMap: List<Map<String, String>>, timestamp: Long) {
        if (activity == null) {
            initialCodes = codeMap
            initialTimestamp = timestamp
            return
        }

        val codes = ArrayList<OathCode>()
        var hasTimeout = false
        for (code in codeMap) {
            val oathCode = OathCode(code["label"] ?: "", code["code"] ?: "")
            hasTimeout = hasTimeout || !oathCode.hotp
            codes.add(oathCode)
        }

        actionMode?.finish()

        adapter.setAll(codes)

        if (hasTimeout) {
            val animationOffset = timestamp - System.currentTimeMillis()
            timeoutAnimation.startOffset = animationOffset
            startTime = timestamp
            timeoutBar?.startAnimation(timeoutAnimation)
        } else {
            startTime = 0
            timeoutBar?.clearAnimation()
            timeoutBar?.progress = 0
        }
    }

    override fun onPasswordMissing(manager: KeyManager, id: ByteArray, missing: Boolean) {
        val dialog = RequirePasswordDialog.newInstance(manager, id, missing)
        if (isAdded) {
            val ft = fragmentManager.beginTransaction()
            val prev = fragmentManager.findFragmentByTag("dialog")
            if (prev != null) {
                ft.remove(prev)
            }
            dialog.show(ft, "dialog")
        } else {  //If we're not yet added, we need to wait for that before prompting.
            needsPassword = dialog
        }
    }

    private fun readHotp(code: OathCode) {
        actionMode?.finish()
        selectedItem = code
        state = READ_SELECTED
        swipeDialog.show(fragmentManager, "dialog")
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.delete -> {
                state = DELETE_SELECTED
                swipeDialog.show(fragmentManager, "dialog")
            }
            else -> return false
        }
        actionMode?.finish()
        return true
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.code_select_actions, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        listView.setItemChecked(listView.checkedItemPosition, false)
    }

    private inner class CodeAdapter(codes: List<OathCode>) : ArrayAdapter<OathCode>(activity, R.layout.oath_code_view, codes) {
        var expired = false

        fun setAll(codes: List<OathCode>) {
            clear()
            expired = false
            addAll(codes)
            sort { lhs, rhs -> lhs.label.toLowerCase().compareTo(rhs.label.toLowerCase()) }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = activity.layoutInflater
            val code = getItem(position)
            val holder: CodeAdapterViewHolder
            if (convertView == null) {
                val view = inflater.inflate(R.layout.oath_code_view, null)
                holder = CodeAdapterViewHolder(view)
                view.tag = holder
            } else {
                holder = convertView.tag as CodeAdapterViewHolder
            }

            listView.setItemChecked(position, actionMode != null && selectedItem === code)

            with(holder) {
                (view as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

                val splitIndex = code.label.indexOf(':')
                if (splitIndex > 0) {
                    issuerView.text = code.label.substring(0, splitIndex)
                    issuerView.visibility = View.VISIBLE
                    labelView.text = code.label.substring(splitIndex + 1)
                } else {
                    issuerView.visibility = View.GONE
                    labelView.text = code.label
                }

                codeView.text = if (code.isRead) code.code else "<refresh to read>"
                val valid = if (code.hotp) code.isRead else !expired
                codeView.setTextColor(resources.getColor(if (valid) android.R.color.primary_text_dark else android.R.color.secondary_text_dark))
                readButton.setOnClickListener { readHotp(code) }
                copyButton.setOnClickListener { code.copyToClipboard() }
                readButton.visibility = if (code.hotp) View.VISIBLE else View.GONE
                copyButton.visibility = if (code.isRead) View.VISIBLE else View.GONE

                return view
            }
        }
    }

    private class CodeAdapterViewHolder(val view: View) {
        val issuerView = view.issuer!!
        val labelView = view.label!!
        val codeView = view.code!!
        val readButton = view.readButton!!
        val copyButton = view.copyButton!!
    }

    private inner class TimeoutAnimation : Animation() {
        init {
            duration = 30000
            interpolator = LinearInterpolator()
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) = Unit
                override fun onAnimationRepeat(animation: Animation) = Unit

                override fun onAnimationEnd(animation: Animation) {
                    adapter.expired = true
                    adapter.notifyDataSetChanged()
                    (activity as MainActivity)?.checkForUsbDevice()
                }
            })
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            super.applyTransformation(interpolatedTime, t)
            timeoutBar.progress = ((1.0 - interpolatedTime) * 1000).toInt()
        }
    }

    inner class OathCode(val label: String, var code: String) {
        var hotp = code.isEmpty()
        val isRead: Boolean
            get() = !code.isEmpty()

        fun copyToClipboard() {
            if (isRead) {
                val clipboard = activity.clipboardManager as ClipboardManager
                val clip = ClipData.newPlainText(label, code)
                clipboard.primaryClip = clip
                activity.toast(R.string.copied)
            }
        }

    }
}
