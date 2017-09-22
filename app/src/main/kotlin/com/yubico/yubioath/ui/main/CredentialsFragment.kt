package com.yubico.yubioath.ui.main

import android.animation.Animator
import android.arch.lifecycle.ViewModelProviders
import android.content.ClipData
import android.content.ClipboardManager
import android.database.DataSetObserver
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ListFragment
import android.view.*
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.support.annotation.RequiresApi
import android.support.annotation.StringRes
import android.support.design.widget.*
import android.support.v4.content.ContextCompat
import android.view.animation.*
import android.widget.AdapterView
import android.widget.ImageView
import com.google.zxing.integration.android.IntentIntegrator
import com.yubico.yubioath.ui.add.AddCredentialActivity
import com.yubico.yubioath.R
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.protocol.OathType
import kotlinx.android.synthetic.main.fragment_credentials.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.clipboardManager
import org.jetbrains.anko.toast

class CredentialsFragment : ListFragment() {
    companion object {
        const private val REQEUST_ADD_CREDENTIAL = 1
    }

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

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val actions = object : CredentialAdapter.ActionHandler {
            override fun select(position: Int) = selectItem(position)

            override fun calculate(credential: Credential) {
                viewModel.calculate(credential).let { job ->
                    launch(UI) {
                        if (viewModel.lastDeviceInfo.persistent) {
                            delay(100) // Delay enough to only prompt when touch is required.
                        }
                        jobWithClient(job, 0, job.isActive)
                    }
                }
            }

            override fun copy(code: Code) {
                val clipboard = activity.clipboardManager as ClipboardManager
                val clip = ClipData.newPlainText("OTP", code.value)
                clipboard.primaryClip = clip
                activity.toast(R.string.copied)
            }
        }
        listAdapter = CredentialAdapter(context, actions, viewModel.creds).apply {
            viewModel.credListener = setCredentials
            registerDataSetObserver(object : DataSetObserver() {
                override fun onChanged() {
                    //Update progress bar
                    updateProgressBar()
                    listView.alpha = 1f
                    swipe_clear_layout.isEnabled = !isEmpty
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

        updateProgressBar()

        viewModel.selectedItem?.let {
            selectItem(adapter.getPosition(it), false)
        }

        fab.setOnClickListener { showAddToolbar() }
        btn_close_toolbar_add.setOnClickListener { hideAddToolbar() }

        btn_scan_qr.setOnClickListener {
            hideAddToolbar()
            IntentIntegrator.forSupportFragment(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES).initiateScan()
        }
        btn_manual_entry.setOnClickListener {
            hideAddToolbar()
            startActivityForResult(Intent(context, AddCredentialActivity::class.java), REQEUST_ADD_CREDENTIAL)
        }

        fixSwipeClearDrawable()
        swipe_clear_layout.apply {
            isEnabled = !listAdapter.isEmpty
            setOnRefreshListener {
                isRefreshing = false

                if (viewModel.lastDeviceInfo.persistent) {
                    viewModel.clearCredentials()
                } else {
                    listView.animate().apply {
                        alpha(0f)
                        duration = 195
                        interpolator = LinearInterpolator()
                        setListener(object : Animator.AnimatorListener {
                            override fun onAnimationRepeat(animation: Animator?) = Unit
                            override fun onAnimationCancel(animation: Animator?) = Unit
                            override fun onAnimationStart(animation: Animator?) = Unit
                            override fun onAnimationEnd(animation: Animator?) {
                                viewModel.clearCredentials()
                            }
                        })
                    }.start()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val qrActivityResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if(qrActivityResult != null) {
            qrActivityResult.contents?.let {
                val uri = Uri.parse(it)
                try {
                    CredentialData.from_uri(uri)
                    startActivityForResult(Intent(Intent.ACTION_VIEW, uri, context, AddCredentialActivity::class.java), REQEUST_ADD_CREDENTIAL)
                } catch (e: IllegalArgumentException) {
                    activity.toast(R.string.invalid_barcode)
                }
            }
        } else when(requestCode) {
            REQEUST_ADD_CREDENTIAL -> if(resultCode == Activity.RESULT_OK && data != null) {
                activity.toast(R.string.add_credential_success)
                val credential:Credential = data.getParcelableExtra(AddCredentialActivity.EXTRA_CREDENTIAL)
                val code: Code? = if(data.hasExtra(AddCredentialActivity.EXTRA_CODE)) data.getParcelableExtra(AddCredentialActivity.EXTRA_CODE) else null
                viewModel.insertCredential(credential, code)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun fixSwipeClearDrawable() {
        //Hack that changes the drawable using reflection.
        swipe_clear_layout.javaClass.getDeclaredField("mCircleView").apply {
            isAccessible = true
            (get(swipe_clear_layout) as ImageView).setImageResource(R.drawable.ic_close_gray_24dp)
        }
    }

    private fun hideAddToolbar(showFab:Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Hide toolbar
            val cx = (fab.left + fab.right) / 2 - toolbar_add.x.toInt()
            val cy = toolbar_add.height / 2
            ViewAnimationUtils.createCircularReveal(toolbar_add, cx, cy, toolbar_add.width * 2f, 0f).apply {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        toolbar_add.visibility = View.INVISIBLE
                        //Show fab
                        if(showFab) {
                            val deltaY = (toolbar_add.top + toolbar_add.bottom - (fab.top + fab.bottom)) / 2f
                            fab.startAnimation(TranslateAnimation(0f, 0f, deltaY, 0f).apply {
                                interpolator = DecelerateInterpolator()
                                duration = 50
                            })
                            fab.visibility = View.VISIBLE
                        }
                    }
                })
            }.start()
        } else {
            toolbar_add.visibility = View.INVISIBLE
            if(showFab) {
                fab.show()
            }
        }
    }

    private fun showAddToolbar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Hide fab
            val deltaY = (toolbar_add.top + toolbar_add.bottom - (fab.top + fab.bottom)) / 2f
            fab.startAnimation(TranslateAnimation(0f, 0f, 0f, deltaY).apply {
                interpolator = AccelerateInterpolator()
                duration = 50
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) = Unit
                    override fun onAnimationRepeat(animation: Animation?) = Unit

                    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
                    override fun onAnimationEnd(animation: Animation?) {
                        //Show toolbar
                        toolbar_add.visibility = View.VISIBLE
                        val cx = (fab.left + fab.right) / 2 - toolbar_add.x.toInt()
                        val cy = toolbar_add.height / 2
                        ViewAnimationUtils.createCircularReveal(toolbar_add, cx, cy, fab.width / 2f, view!!.width * 2f).start()
                        fab.visibility = View.INVISIBLE
                    }
                })
            })
        } else {
            toolbar_add.visibility = View.VISIBLE
            fab.hide()
        }
    }

    private fun updateProgressBar() {
        progressBar?.apply {
            val now = System.currentTimeMillis()
            val validFrom = adapter.creds.filterKeys { it.type == OathType.TOTP && it.period == 30 && !it.touch }.values.firstOrNull()?.validFrom
            if (validFrom != null) {
                val validTo = validFrom + 30000
                startAnimation(timerAnimation.apply {
                    duration = validTo - Math.min(now, validFrom)
                    startOffset = Math.min(0, validFrom - now)
                })
            } else {
                clearAnimation()
                progress = 0
            }
        }
    }

    private fun snackbar(@StringRes message: Int, duration: Int): Snackbar {
        return Snackbar.make(view!!, message, duration).apply {
            setActionTextColor(ContextCompat.getColor(context, R.color.yubicoPrimaryGreen))
            addCallback(object: Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (!fab.isShown && !toolbar_add.isShown) fab.show()
                }
            })
            if(toolbar_add.isShown) {
                hideAddToolbar(false)
            } else {
                fab.hide()
            }
            show()
        }
    }

    private fun jobWithClient(job:Job, @StringRes successMessage: Int, needsTouch: Boolean) {
        job.invokeOnCompletion {
            launch(UI) {
                if(!job.isCancelled && successMessage != 0) {
                    activity.toast(successMessage)
                }
            }
        }

        if(!viewModel.lastDeviceInfo.persistent || needsTouch) {
            snackbar(R.string.swipe_and_hold, Snackbar.LENGTH_INDEFINITE).apply {
                job.invokeOnCompletion { dismiss() }
                setAction(R.string.cancel) { job.cancel() }
            }
        }
    }

    private fun selectItem(position: Int, updateViewModel:Boolean = true) {
        val credential = adapter.getItem(position).key
        if(updateViewModel) {
            viewModel.selectedItem = credential
        }

        val mode = actionMode ?: activity.startActionMode(object : ActionMode.Callback {
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when(item.itemId) {
                    R.id.delete -> viewModel.apply {
                        selectedItem?.let {
                            selectedItem = null
                            jobWithClient(viewModel.delete(it), R.string.deleted, false)
                        }
                    }
                }
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
        mode.title = (credential.issuer?.let { it + ": " } ?: "") + credential.name

        listView.setItemChecked(position, true)
    }
}
