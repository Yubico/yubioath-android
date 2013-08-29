package com.yubico.yubioath.fragments;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;
import com.yubico.yubioath.MainActivity;
import com.yubico.yubioath.R;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.YubiKeyNeo;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/26/13
 * Time: 1:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class SetPasswordFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener, View.OnClickListener {
    private byte[] id = null;
    private KeyManager keyManager;
    private boolean needsPassword = false;
    private DialogFragment swipeDialog;
    private boolean needsId = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.set_password_fragment, container, false);
        view.findViewById(R.id.cancelPassword).setOnClickListener(this);
        view.findViewById(R.id.savePassword).setOnClickListener(this);

        swipeDialog = new SwipeDialog();
        swipeDialog.show(getFragmentManager(), "dialog");

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancelPassword:
                closeKeyboard();
                ((MainActivity) getActivity()).openFragment(new ListCodesFragment());
                break;
            case R.id.savePassword:
                closeKeyboard();
                if (needsPassword) {
                    String oldPass = ((EditText) getView().findViewById(R.id.editOldPassword)).getText().toString();
                    keyManager.storeSecret(id, KeyManager.calculateSecret(oldPass, id));
                }
                String newPassword = ((EditText)getView().findViewById(R.id.editNewPassword)).getText().toString();
                String verifyPassword = ((EditText)getView().findViewById(R.id.editVerifyPassword)).getText().toString();
                if(newPassword.equals(verifyPassword)) {
                    swipeDialog.show(getFragmentManager(), "dialog");
                } else {
                    Toast.makeText(getActivity(), R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void closeKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (getActivity().getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getApplicationWindowToken(), 0);
        }
    }

    @Override
    public void onPasswordMissing(KeyManager keyManager, byte[] id, boolean missing) {
        if (!swipeDialog.isAdded()) {
            Toast.makeText(getActivity(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        swipeDialog.dismiss();

        if (needsId) {
            this.id = id;
            this.keyManager = keyManager;
            needsPassword = true;
            needsId = false;
            ((EditText) getView().findViewById(R.id.editDisplayName)).setText(R.string.yubikey_neo);
            getView().findViewById(R.id.requiresPassword).setVisibility(View.VISIBLE);
        } else {
            //Wrong (old) password, try again.
            EditText editOldPassword = (EditText) getView().findViewById(R.id.editOldPassword);
            editOldPassword.setText("");
            editOldPassword.requestFocus();
            Toast.makeText(getActivity(), R.string.wrong_password, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException {
        if (!swipeDialog.isAdded()) {
            Toast.makeText(getActivity(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        swipeDialog.dismiss();

        if (needsId) {
            id = neo.getId();
            ((EditText) getView().findViewById(R.id.editDisplayName)).setText(neo.getDisplayName(getString(R.string.yubikey_neo)));
            needsId = false;
        } else {
            String label = ((EditText) getView().findViewById(R.id.editDisplayName)).getText().toString().trim();
            String newPass = ((EditText) getView().findViewById(R.id.editNewPassword)).getText().toString();
            neo.setDisplayName(label);
            neo.setLockCode(newPass);
            ((MainActivity)getActivity()).openFragment(new ListCodesFragment());
        }
    }
}
