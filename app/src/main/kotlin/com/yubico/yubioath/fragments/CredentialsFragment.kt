package com.yubico.yubioath.fragments

import android.arch.lifecycle.ViewModelProviders
import android.content.ClipData
import android.content.ClipboardManager
import android.database.DataSetObserver
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.design.widget.SwipeDismissBehavior
import android.support.v4.app.ListFragment
import android.view.*
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import android.widget.AdapterView
import android.widget.ListView
import com.yubico.yubioath.R
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.ui.CredentialAdapter
import com.yubico.yubioath.ui.OathViewModel
import kotlinx.android.synthetic.main.fragment_credentials.*
import org.jetbrains.anko.clipboardManager

/**
 * A placeholder fragment containing a simple view.
 */
class CredentialsFragment : ListFragment() {
    private val viewModel: OathViewModel by lazy { ViewModelProviders.of(activity).get(OathViewModel::class.java) }
    private val timerAnimation = object : Animation() {
        init {
            duration = 30000
            interpolator = LinearInterpolator()
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            progressBar.progress = ((1.0 - interpolatedTime) * 1000).toInt()
        }
    }

    private var actionMode: ActionMode? = null

    private val adapter: CredentialAdapter by lazy { listAdapter as CredentialAdapter }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_credentials, container, false)
    }

    private val swipeDismissBehavior = SwipeDismissBehavior<ListView>().apply {
        setListener(object : SwipeDismissBehavior.OnDismissListener {
            override fun onDismiss(view: View) {
                viewModel.clearCredentials()
                view.alpha = 1.0f  //Needed to re-display the list after dismissal.
            }

            override fun onDragStateChanged(state: Int) = Unit
        })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val actions = object : CredentialAdapter.ActionHandler {
            override fun select(position: Int) = selectItem(position)

            override fun calculate(credential: Credential, prompt: Boolean) {
                viewModel.calculate(credential).let { job ->
                    Snackbar.make(view!!, R.string.swipe_and_hold, Snackbar.LENGTH_INDEFINITE).apply {
                        job.invokeOnCompletion { dismiss() }
                        setAction(R.string.cancel, { job.cancel() })
                    }.show()
                }
            }

            override fun copy(code: Code) {
                val clipboard = activity.clipboardManager as ClipboardManager
                val clip = ClipData.newPlainText("OTP", code.value)
                clipboard.primaryClip = clip
                Snackbar.make(view!!, R.string.copied, Snackbar.LENGTH_SHORT).show()
            }
        }
        listAdapter = CredentialAdapter(context, actions, viewModel.creds).apply {
            viewModel.credListener = setCredentials
            registerDataSetObserver(object : DataSetObserver() {
                override fun onChanged() {
                    //Update progress bar
                    updateProgressBar()

                    try {
                        makeListviewDraggable()
                    } catch (e: IllegalStateException) {
                        // This might be called before the ListView has been created...
                    }
                }
            })
        }

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            actionMode?.apply {
                if (listView.isItemChecked(position)) {
                    finish()
                } else {
                    selectItem(position)
                }
            } ?: listView.setItemChecked(position, false)
        }
        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            selectItem(position)
            true
        }

        makeListviewDraggable()
        updateProgressBar()

        viewModel.selectedItem?.let {
            selectItem(adapter.getPosition(it), false)
        }
    }

    private fun updateProgressBar() {
        val startTime = adapter.creds.filterKeys { it.type == OathType.TOTP && it.period == 30 && !it.touch }.values.firstOrNull()?.validFrom ?: -1
        progressBar?.apply {
            if (startTime > 0) {
                timerAnimation.startOffset = startTime - System.currentTimeMillis()
                startAnimation(timerAnimation)
            } else {
                clearAnimation()
                progress = 0
            }
        }
    }

    private fun makeListviewDraggable() {
        (listView.layoutParams as CoordinatorLayout.LayoutParams).behavior = if (adapter.creds.isEmpty() || viewModel.lastDeviceInfo.persistent) null else swipeDismissBehavior
    }

    private fun selectItem(position: Int, updateViewModel:Boolean = true) {
        val credential = adapter.getItem(position).key
        if(updateViewModel) {
            viewModel.selectedItem = credential
        }

        val mode = actionMode ?: activity.startActionMode(object : ActionMode.Callback {
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                actionMode?.finish()
                return true
            }

            override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
                mode.menuInflater.inflate(R.menu.code_select_actions, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

            override fun onDestroyActionMode(mode: ActionMode?) {
                actionMode = null
                listView.setItemChecked(listView.checkedItemPosition, false)
                viewModel.selectedItem = null
            }
        }).apply { actionMode = this }
        mode.title = credential.name

        listView.setItemChecked(position, true)
    }
}
