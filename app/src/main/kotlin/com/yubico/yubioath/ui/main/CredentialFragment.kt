package com.yubico.yubioath.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.database.DataSetObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.annotation.RequiresApi
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v4.app.ListFragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.*
import android.view.animation.*
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.integration.android.IntentIntegrator
import com.pixplicity.sharp.Sharp
import com.yubico.yubioath.R
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.ui.add.AddCredentialActivity
import kotlinx.android.synthetic.main.fragment_credentials.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.clipboardManager
import org.jetbrains.anko.toast

class CredentialFragment : ListFragment() {
    companion object {
        const private val REQUEST_ADD_CREDENTIAL = 1
        const private val REQUEST_SELECT_ICON = 2
    }

    private val viewModel: OathViewModel by lazy { ViewModelProviders.of(activity!!).get(OathViewModel::class.java) }
    private val timerAnimation = object : Animation() {
        var deadline: Long = 0

        init {
            duration = 30000
            interpolator = LinearInterpolator()
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            progressBar.progress = ((1.0 - interpolatedTime) * 1000).toInt()
        }
    }
    private val listObserver = object : DataSetObserver() {
        override fun onChanged() {
            //Update progress bar
            updateProgressBar()
            listView.alpha = 1f
            swipe_clear_layout.isEnabled = !adapter.isEmpty
        }
    }

    private var actionMode: ActionMode? = null

    private val adapter: CredentialAdapter by lazy { listAdapter as CredentialAdapter }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_credentials, container, false)
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
                activity?.let {
                    val clipboard = it.clipboardManager as ClipboardManager
                    val clip = ClipData.newPlainText("OTP", code.value)
                    clipboard.primaryClip = clip
                    it.toast(R.string.copied)
                }
            }
        }
        context?.let {
            listAdapter = CredentialAdapter(it, actions, viewModel.creds).apply {
                viewModel.credListener = { creds, filter ->
                    launch(UI) {
                        view?.findViewById<TextView>(android.R.id.empty)?.setText(if (filter.isEmpty() || creds.isEmpty()) R.string.swipe_and_hold else R.string.no_match)
                    }
                    setCredentials(creds, filter)
                }
            }
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
            startActivityForResult(Intent(context, AddCredentialActivity::class.java), REQUEST_ADD_CREDENTIAL)
        }

        fixSwipeClearDrawable()
        swipe_clear_layout.apply {
            isEnabled = !listAdapter.isEmpty
            setOnRefreshListener {
                isRefreshing = false
                actionMode?.finish()

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

    override fun onResume() {
        super.onResume()
        adapter.registerDataSetObserver(listObserver)
    }

    override fun onPause() {
        super.onPause()
        adapter.unregisterDataSetObserver(listObserver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val qrActivityResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        activity?.apply {
            if (qrActivityResult != null) {
                qrActivityResult.contents?.let {
                    val uri = Uri.parse(it)
                    try {
                        CredentialData.fromUri(uri)
                        startActivityForResult(Intent(Intent.ACTION_VIEW, uri, context, AddCredentialActivity::class.java), REQUEST_ADD_CREDENTIAL)
                    } catch (e: IllegalArgumentException) {
                        toast(R.string.invalid_barcode)
                    }
                }
            } else when (requestCode) {
                REQUEST_ADD_CREDENTIAL -> if (resultCode == Activity.RESULT_OK && data != null) {
                    toast(R.string.add_credential_success)
                    val credential: Credential = data.getParcelableExtra(AddCredentialActivity.EXTRA_CREDENTIAL)
                    val code: Code? = if (data.hasExtra(AddCredentialActivity.EXTRA_CODE)) data.getParcelableExtra(AddCredentialActivity.EXTRA_CODE) else null
                    viewModel.insertCredential(credential, code)
                }
                REQUEST_SELECT_ICON -> {
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        viewModel.selectedItem?.let { credential ->
                            try {
                                try {
                                    val icon = MediaStore.Images.Media.getBitmap(contentResolver, data.data)
                                    adapter.setIcon(credential, icon)
                                } catch (e: IllegalStateException) {
                                    val svg = Sharp.loadInputStream(contentResolver.openInputStream(data.data))
                                    adapter.setIcon(credential, svg.drawable)
                                }
                            } catch (e: Exception) {
                                toast(R.string.invalid_image)
                            }
                        }
                    }
                    actionMode?.finish()
                }
                else -> super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    private fun fixSwipeClearDrawable() {
        //Hack that changes the drawable using reflection.
        swipe_clear_layout.javaClass.getDeclaredField("mCircleView").apply {
            isAccessible = true
            (get(swipe_clear_layout) as ImageView).setImageResource(R.drawable.ic_close_gray_24dp)
        }
    }

    private fun hideAddToolbar(showFab: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Hide toolbar
            val cx = (fab.left + fab.right) / 2 - toolbar_add.x.toInt()
            val cy = toolbar_add.height / 2
            ViewAnimationUtils.createCircularReveal(toolbar_add, cx, cy, toolbar_add.width * 2f, 0f).apply {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        toolbar_add.visibility = View.INVISIBLE
                        //Show fab
                        if (showFab) {
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
            if (showFab) {
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
            val validFrom = adapter.creds.filterKeys { it.type == OathType.TOTP && it.period == 30 && !it.touch }.values.firstOrNull()?.validFrom
            if (validFrom != null) {
                val validTo = validFrom + 30000
                if (!timerAnimation.hasStarted() || timerAnimation.deadline != validTo) {
                    val now = System.currentTimeMillis()
                    startAnimation(timerAnimation.apply {
                        deadline = validTo
                        duration = validTo - Math.min(now, validFrom)
                        startOffset = Math.min(0, validFrom - now)
                    })
                }
            } else {
                clearAnimation()
                progress = 0
                timerAnimation.deadline = 0
            }
        }
    }

    private fun snackbar(@StringRes message: Int, duration: Int): Snackbar {
        return Snackbar.make(view!!, message, duration).apply {
            setActionTextColor(ContextCompat.getColor(context, R.color.yubicoPrimaryGreen)) //This doesn't seem to be directly styleable, unfortunately.
            addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (!fab.isShown && !toolbar_add.isShown) fab.show()
                }
            })
            if (toolbar_add.isShown) {
                hideAddToolbar(false)
            } else {
                fab.hide()
            }
            show()
        }
    }

    private fun jobWithClient(job: Job, @StringRes successMessage: Int, needsTouch: Boolean) {
        job.invokeOnCompletion {
            launch(UI) {
                if (!job.isCancelled && successMessage != 0) {
                    activity?.toast(successMessage)
                }
            }
        }

        if (!viewModel.lastDeviceInfo.persistent || needsTouch) {
            snackbar(R.string.swipe_and_hold, Snackbar.LENGTH_INDEFINITE).apply {
                job.invokeOnCompletion { dismiss() }
                setAction(R.string.cancel) { job.cancel() }
            }
        }
    }

    private fun selectItem(position: Int, updateViewModel: Boolean = true) {
        val credential = adapter.getItem(position).key
        if (updateViewModel) {
            viewModel.selectedItem = credential
        }

        activity?.let { act ->
            (actionMode ?: act.startActionMode(object : ActionMode.Callback {
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    when (item.itemId) {
                        R.id.pin -> {
                            adapter.setPinned(credential, !adapter.isPinned(credential))
                            actionMode?.finish()
                        }
                        R.id.change_icon -> {
                            AlertDialog.Builder(act)
                                    .setTitle("Select icon")
                                    .setItems(R.array.icon_choices, { _, choice ->
                                        when (choice) {
                                            0 -> startActivityForResult(Intent.createChooser(Intent().apply {
                                                type = "image/*"
                                                action = Intent.ACTION_GET_CONTENT
                                            }, "Select icon"), REQUEST_SELECT_ICON)
                                            1 -> {
                                                adapter.removeIcon(credential)
                                                actionMode?.finish()
                                            }
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show()
                            return true
                        }
                        R.id.delete -> viewModel.apply {
                            selectedItem?.let {
                                AlertDialog.Builder(act)
                                        .setTitle(R.string.delete_cred)
                                        .setMessage(R.string.delete_cred_message)
                                        .setPositiveButton(R.string.delete) { _, _ ->
                                            selectedItem = null
                                            jobWithClient(viewModel.delete(it), R.string.deleted, false)
                                            actionMode?.finish()
                                        }
                                        .setNegativeButton(R.string.cancel, null)
                                        .show()
                            }
                        }
                    }
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
            }).apply {
                actionMode = this
            }).apply {
                menu.findItem(R.id.pin).setIcon(if (adapter.isPinned(credential)) R.drawable.ic_star_24dp else R.drawable.ic_star_border_24dp)
                title = (credential.issuer?.let { it + ": " } ?: "") + credential.name
            }
        }

        listView.setItemChecked(position, true)
    }
}
