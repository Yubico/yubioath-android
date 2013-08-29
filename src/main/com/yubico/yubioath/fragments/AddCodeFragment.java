package com.yubico.yubioath.fragments;

import android.app.DialogFragment;
import android.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.yubico.yubioath.MainActivity;
import com.yubico.yubioath.R;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.YubiKeyNeo;
import org.apache.commons.codec.binary.Base32;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/28/13
 * Time: 10:16 AM
 * To change this template use File | Settings | File Templates.
 */
public class AddCodeFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener {
    private static final String CODE_URI = "codeUri";
    private String name;
    private byte[] key;
    private byte oath_type;
    private byte algorithm_type;
    private int digits;

    public static AddCodeFragment newInstance(String uri) {
        Bundle bundle = new Bundle();
        bundle.putString(CODE_URI, uri);
        AddCodeFragment fragment = new AddCodeFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.add_code_fragment, container, false);
        Uri uri = Uri.parse(getArguments().getString(CODE_URI));
        if(parseUri(uri)) {
            ((TextView)view.findViewById(R.id.name)).setText(name);
        } else {
            Toast.makeText(getActivity(), R.string.invalid_barcode, Toast.LENGTH_LONG).show();
            view.post(new Runnable() {
                @Override
                public void run() {
                    ((MainActivity) getActivity()).openFragment(new ListCodesFragment());
                }
            });
        }

        return view;
    }

    private boolean parseUri(Uri uri) {
        String secret = uri.getQueryParameter("secret");
        if(secret == null || secret.isEmpty()) {
            return false;
        }
        Base32 base32 = new Base32();
        key = base32.decode(secret.toUpperCase());

        String path = uri.getPath(); // user name is stored in path...
        if(path.charAt(0) == '/') {
            path = path.substring(1);
        }
        if(path.length() > 64) {
            path = path.substring(0, 64);
        }
        name = path;

        String typeString = uri.getHost(); // type stored in host, totp/hotp
        if(typeString.equals("totp")) {
            oath_type = YubiKeyNeo.TOTP_TYPE;
        } else if(typeString.equals("hotp")) {
            oath_type = YubiKeyNeo.HOTP_TYPE;
        } else {
            return false;
        }

        String algorithm = uri.getQueryParameter("algorithm");
        if(algorithm == null || algorithm.isEmpty() || algorithm.equals("SHA1")) {
            algorithm_type = YubiKeyNeo.HMAC_SHA1;
        } else if(algorithm.equals("SHA256")) {
            algorithm_type = YubiKeyNeo.HMAC_SHA256;
        } else {
            return false;
        }

        String digit_string = uri.getQueryParameter("digits");
        if(digit_string == null || digit_string.isEmpty()) {
            digits = 6;
        } else {
            try {
                digits = Integer.parseInt(digit_string);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void onPasswordMissing(KeyManager keyManager, byte[] id, boolean missing) {
        DialogFragment dialog = RequirePasswordDialog.newInstance(keyManager, id, missing);
        dialog.show(getFragmentManager(), "dialog");
    }

    @Override
    public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException {
        name = ((EditText)getView().findViewById(R.id.name)).getText().toString();
        neo.storeCode(name, key, (byte) (oath_type | algorithm_type), digits);
        long timestamp = System.currentTimeMillis() / 1000 / 30;
        final List<Map<String, String>> codes = neo.getCodes(timestamp);
        Toast.makeText(getActivity(), R.string.prog_success, Toast.LENGTH_LONG).show();
        getView().post(new Runnable() {
            @Override
            public void run() {
                final ListCodesFragment fragment = new ListCodesFragment();
                ((MainActivity)getActivity()).openFragment(fragment);
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        fragment.showCodes(codes);
                    }
                });
            }
        });
    }
}
