package com.yubico.yubioath;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
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
import com.yubico.yubioath.fragments.AboutFragment;
import com.yubico.yubioath.fragments.AddAccountFragment;
import com.yubico.yubioath.fragments.EnableNfcDialog;
import com.yubico.yubioath.fragments.SetPasswordFragment;
import com.yubico.yubioath.fragments.SwipeListFragment;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.YubiKeyNeo;

import java.io.IOException;

import nordpol.android.AndroidCard;
import nordpol.android.OnDiscoveredTagListener;
import nordpol.android.TagDispatcher;

public class MainActivity extends AppCompatActivity implements OnDiscoveredTagListener {
    private static final int SCAN_BARCODE = 1;
    private static final String NEO_STORE = "NEO_STORE";
    private static final String ALLOW_ROTATION_SHARED_PREF = "ALLOW_ROTATION";

    private TagDispatcher tagDispatcher;
    private KeyManager keyManager;
    private SharedPreferences preferences;
    private OnYubiKeyNeoListener totpListener;
    private boolean readOnResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //This causes rotation animation to look like crap.
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.main_activity);

        preferences = getSharedPreferences(NEO_STORE, Context.MODE_PRIVATE);
        keyManager = new KeyManager(preferences);

        openFragment(new SwipeListFragment());

        /* Set up Nordpol in the following manner:
         * - opt out of NFC unavailable handling
         * - opt out of disabled sounds
         * - dispatch on UI thread
         * - opt out of broadcom workaround (this is only available in reader mode)
         * - opt out of reader mode completely
         */
        tagDispatcher = TagDispatcher.get(this, this, false, false, true, false, true);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(SwipeListFragment.class.getName());
        if (fragment == null) {
            openFragment(new SwipeListFragment());
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return super.onCreateView(name, context, attrs);
    }

    public void onPause() {
        super.onPause();
        tagDispatcher.disableExclusiveNfc();
    }

    public void onResume() {
        super.onResume();

        if (readOnResume) { // On activity creation, check if there is a Tag in the intent
            tagDispatcher.interceptIntent(getIntent());
            readOnResume = false; // Don't check a second time (onNewIntent will be called)
        }
        switch(tagDispatcher.enableExclusiveNfc()) {
            case AVAILABLE_DISABLED:
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
                if (prev != null) {
                    ft.remove(prev);
                }
                DialogFragment dialog = new EnableNfcDialog();
                dialog.show(ft, "dialog");
                break;
            case NOT_AVAILABLE:
                Toast.makeText(this, R.string.no_nfc, Toast.LENGTH_LONG).show();
                finish();
        }
    }

    public void openFragment(Fragment fragment) {
        if (fragment instanceof OnYubiKeyNeoListener) {
            totpListener = (OnYubiKeyNeoListener) fragment;
        } else if (totpListener != null) {
            totpListener = null;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment, fragment.getClass().getName());
        fragmentTransaction.commitAllowingStateLoss();
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

    private boolean getSavedAllowRotation(){
        return preferences.getBoolean(ALLOW_ROTATION_SHARED_PREF, true);
    }

    private void implementAllowRotation(MenuItem item, boolean value){
        item.setChecked(value);
        if (value){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void setAllowRotation(MenuItem item, boolean value){
        preferences.edit().putBoolean(ALLOW_ROTATION_SHARED_PREF, value).commit();
        implementAllowRotation(item, value);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCAN_BARCODE) {
            if (resultCode == RESULT_OK) {
                openFragment(AddAccountFragment.newInstance(data.getStringExtra("SCAN_RESULT")));
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        implementAllowRotation(menu.findItem(R.id.menu_allow_rotation), getSavedAllowRotation());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_account_scan:
                scanQRCode();
                break;
            case R.id.menu_add_account_manual:
                openFragment(new AddAccountFragment());
                break;
            case R.id.menu_change_password:
                openFragment(new SetPasswordFragment());
                break;
            case R.id.menu_allow_rotation:
                setAllowRotation(item, !item.isChecked());
                break;
            case R.id.menu_about:
                openFragment(AboutFragment.newInstance(keyManager));
                break;
        }
        return true;
    }

    @Override
    public void tagDiscovered(final Tag tag) {
        //Run in UI thread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (totpListener != null) {
                    YubiKeyNeo yubiKeyNeo = null;
                    try {
                        yubiKeyNeo = new YubiKeyNeo(keyManager, AndroidCard.get(tag));
                        if (yubiKeyNeo.isLocked()) {
                            yubiKeyNeo.unlock();
                        }
                        totpListener.onYubiKeyNeo(yubiKeyNeo);
                    } catch (PasswordRequiredException e) {
                        totpListener.onPasswordMissing(keyManager, e.getId(), e.isMissing());
                    } catch (IOException e) {
                        Toast.makeText(MainActivity.this, R.string.tag_error, Toast.LENGTH_SHORT).show();
                        Log.e("yubioath", "IOException in handler", e);
                    } catch (AppletMissingException e) {
                        Toast.makeText(MainActivity.this, R.string.applet_missing, Toast.LENGTH_SHORT).show();
                        Log.e("yubioath", "AppletMissingException in handler", e);
                    } catch (UnsupportedAppletException e) {
                        Toast.makeText(MainActivity.this, R.string.unsupported_applet_version, Toast.LENGTH_SHORT).show();
                        Log.e("yubioath", "UnsupportedAppletException in handler", e);
                    } catch (AppletSelectException e) {
                        Toast.makeText(MainActivity.this, R.string.tag_error, Toast.LENGTH_SHORT).show();
                        Log.e("yubioath", "AppletSelectException in handler", e);
                    } finally {
                        if (yubiKeyNeo != null) {
                            try {
                                yubiKeyNeo.close();
                            } catch (IOException e) {
                                Toast.makeText(MainActivity.this, R.string.tag_error, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        tagDispatcher.interceptIntent(intent);
    }

    public interface OnYubiKeyNeoListener {
        void onYubiKeyNeo(YubiKeyNeo neo) throws IOException;

        void onPasswordMissing(KeyManager keyManager, byte[] id, boolean missing);
    }
}
