package com.yubico.yubioath.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import com.yubico.yubioath.R;
import com.yubico.yubioath.model.KeyManager;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/26/13
 * Time: 4:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class SwipeDialog extends DialogFragment {
    private DialogInterface.OnCancelListener onCancel;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.swipe);

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        return builder.create();
    }

    public void setOnCancel(DialogInterface.OnCancelListener onCancel) {
        this.onCancel = onCancel;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if(onCancel != null) {
            onCancel.onCancel(dialog);
        }
    }
}
