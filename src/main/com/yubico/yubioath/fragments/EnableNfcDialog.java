package com.yubico.yubioath.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import com.yubico.yubioath.R;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 9/20/13
 * Time: 1:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class EnableNfcDialog extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setCancelable(false);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.nfc_off)
                .setPositiveButton(R.string.enable_nfc, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent settings = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
                        startActivity(settings);
                        dialog.dismiss();
                    }
                }).setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        getActivity().finish();
                    }
                }).create();
    }
}
