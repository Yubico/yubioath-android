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
 * Time: 1:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class RequirePasswordDialog extends DialogFragment {
    private static final String DEVICE_ID = "deviceId";
    private static final String MISSING = "missing";

    static RequirePasswordDialog newInstance(KeyManager keyManager, byte[] id, boolean missing) {
        Bundle bundle = new Bundle();
        bundle.putByteArray(DEVICE_ID, id);
        bundle.putBoolean(MISSING, missing);
        RequirePasswordDialog dialog = new RequirePasswordDialog();
        dialog.setArguments(bundle);
        dialog.setKeyManager(keyManager);
        return dialog;
    }

    private KeyManager keyManager;

    private void setKeyManager(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final byte[] id = getArguments().getByteArray(DEVICE_ID);
        final boolean missing = getArguments().getBoolean(MISSING);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(missing ? R.string.password_required : R.string.wrong_password);
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.require_password_dialog, null);
        final EditText editDisplayName = (EditText) view.findViewById(R.id.editDisplayName);
        final EditText editPassword = (EditText) view.findViewById(R.id.editPassword);
        editDisplayName.setText(keyManager.getDisplayName(id, getString(R.string.yubikey_neo)));
        builder.setView(view);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String label = editDisplayName.getText().toString().trim();
                String password = editPassword.getText().toString().trim();
                keyManager.setDisplayName(id, label);
                keyManager.storeSecret(id, KeyManager.calculateSecret(password, id));
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        return builder.create();
    }
}
