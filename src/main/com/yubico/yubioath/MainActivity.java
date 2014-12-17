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

package com.yubico.yubioath;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import com.yubico.yubioath.exc.AppletMissingException;
import com.yubico.yubioath.exc.AppletSelectException;
import com.yubico.yubioath.exc.PasswordRequiredException;
import com.yubico.yubioath.exc.UnsupportedAppletException;
import com.yubico.yubioath.fragments.*;
import com.yubico.yubioath.model.*;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/26/13
 * Time: 10:43 AM
 * To change this template use File | Settings | File Templates.
 */
public class MainActivity extends Activity {
    private static final int SCAN_BARCODE = 1;
    private static final String NEO_STORE = "NEO_STORE";

    private NfcAdapter adapter;
    private KeyManager keyManager;
    private OnYubiKeyNeoListener totpListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //This causes rotation animation to look like crap.
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.main_activity);

        adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter == null) {
            Toast.makeText(this, R.string.no_nfc, Toast.LENGTH_LONG).show();
            finish();
        } else {
        	SharedPreferences preferences = getSharedPreferences(NEO_STORE, Context.MODE_PRIVATE);
        	keyManager = new KeyManager(preferences);

        	openFragment(new SwipeListFragment());
        }
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return super.onCreateView(name, context, attrs);
    }

    public void onPause() {
        super.onPause();
        if (totpListener != null) {
            adapter.disableForegroundDispatch(this);
        }
    }

    public void onResume() {
        super.onResume();

        if(!adapter.isEnabled()) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            Fragment prev = getFragmentManager().findFragmentByTag("dialog");
            if (prev != null) {
                ft.remove(prev);
            }
            DialogFragment dialog = new EnableNfcDialog();
            dialog.show(ft, "dialog");
        } else if (totpListener != null) {
            Intent intent = getIntent();
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent tagIntent = PendingIntent.getActivity(this, 0, intent, 0);
            IntentFilter iso = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
            adapter.enableForegroundDispatch(this, tagIntent, new IntentFilter[]{iso},
                    new String[][]{new String[]{IsoDep.class.getName()}});
        }
    }

    public void openFragment(Fragment fragment) {
        if (fragment instanceof OnYubiKeyNeoListener) {
            totpListener = (OnYubiKeyNeoListener) fragment;
        } else if (totpListener != null) {
            totpListener = null;
        }

        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }

    private void scanQRCode() {
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        intent.putExtra("SAVE_HISTORY", false);

        try {
            startActivityForResult(intent, SCAN_BARCODE);
        } catch (ActivityNotFoundException e) {
            barcodeScannerNotInstalled(
                    getString(R.string.warning),
                    getString(R.string.barcode_scanner_not_found),
                    getString(R.string.yes),
                    getString(R.string.no));
        }
        return;
    }

    private void barcodeScannerNotInstalled(String stringTitle,
                                            String stringMessage, String stringButtonYes, String stringButtonNo) {
        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(this);
        downloadDialog.setTitle(stringTitle);
        downloadDialog.setMessage(stringMessage);
        downloadDialog.setPositiveButton(stringButtonYes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Uri uri = Uri.parse("market://search?q=pname:com.google.zxing.client.android");
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    }
                });
        downloadDialog.setNegativeButton(stringButtonNo,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                });
        downloadDialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCAN_BARCODE) {
            if (resultCode == RESULT_OK) {
                openFragment(AddCodeFragment.newInstance(data.getStringExtra("SCAN_RESULT")));
            } else {
                Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_account:
                scanQRCode();
                break;
            case R.id.menu_change_password:
                openFragment(new SetPasswordFragment());
                break;
            case R.id.menu_about:
                openFragment(AboutFragment.newInstance(keyManager));
                break;
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null && totpListener != null) {
            YubiKeyNeo yubiKeyNeo = null;
            try {
                yubiKeyNeo = new YubiKeyNeo(keyManager, IsoDep.get(tag));
                totpListener.onYubiKeyNeo(yubiKeyNeo);
            } catch (PasswordRequiredException e) {
                totpListener.onPasswordMissing(keyManager, e.getId(), e.isMissing());
            } catch (IOException e) {
                Toast.makeText(this, R.string.tag_error, Toast.LENGTH_SHORT).show();
                Log.e("yubioath", "IOException in handler", e);
            } catch (AppletMissingException e) {
                Toast.makeText(this, R.string.applet_missing, Toast.LENGTH_SHORT).show();
                Log.e("yubioath", "AppletMissingException in handler", e);
            } catch (UnsupportedAppletException e) {
                Toast.makeText(this, R.string.unsupported_applet_version, Toast.LENGTH_SHORT).show();
                Log.e("yubioath", "UnsupportedAppletException in handler", e);
            } catch (AppletSelectException e) {
                Toast.makeText(this, R.string.tag_error, Toast.LENGTH_SHORT).show();
                Log.e("yubioath", "AppletSelectException in handler", e);
            } finally {
                if (yubiKeyNeo != null) {
                    try {
                        yubiKeyNeo.close();
                    } catch (IOException e) {
                        Toast.makeText(this, R.string.tag_error, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    public interface OnYubiKeyNeoListener {
        public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException;

        public void onPasswordMissing(KeyManager keyManager, byte[] id, boolean missing);
    }
}
