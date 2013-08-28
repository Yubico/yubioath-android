/* Copyright (c) 2012-2013, Yubico AB.  All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   are met:

   * Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.

   * Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following
     disclaimer in the documentation and/or other materials provided
     with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
   CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
   INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
   MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
   DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
   BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
   TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
   ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
   TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
   THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
   SUCH DAMAGE.

*/

package com.yubico.yubioath.legacy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.yubico.yubioath.R;

public class TotpActivity extends PreferenceActivity {
	
	private static final int SCAN_BARCODE = 0;
	private static final int PROGRAM = 1;
	private static final int TOTP = 2;
	private static final int LOAD = 3;

	private static final String logTag = "yubioath";
	private static boolean waitingForResult = false;
	
	private static final String PREF_NAME = "com.yubico.yubioath.legacy.TotpWidgetConfigure.Preferences";
	private boolean recreating = false;
	private int mode = 0;
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences preferences = getSharedPreferences(PREF_NAME, 0);
		if(preferences.getBoolean("slotMode", true)) {
			mode = 0;
		} else {
			mode = 1;
		}
	}
    
    @Override
    public Intent getIntent() {
        final Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, PrefsFragment.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }
    
    public static class PrefsFragment extends PreferenceFragment {
		SharedPreferences preferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preference_fragment);

            final TotpActivity act = (TotpActivity) getActivity();
            preferences = act.getSharedPreferences(PREF_NAME, 0);
            
            loadCredentials();
            
            Preference prog = findPreference("do_program");
            prog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				public boolean onPreferenceClick(Preference preference) {
					act.doProgram();
					return true;
				}
			});
            
            
            Preference load = findPreference("load_creds");
            PreferenceCategory programming = (PreferenceCategory) findPreference("program");
            if(preferences.getBoolean("slotMode", true)) {
            	programming.removePreference(load);
            } else {
            	load.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            		public boolean onPreferenceClick(Preference preference) {
            			act.doLoad();
            			return true;
            		}
            	});
            }
        }
        
        private void loadCredentials() {
        	Set<String> credList = new HashSet<String>();
        	PreferenceCategory creds = (PreferenceCategory) findPreference("credentials");
        	final TotpActivity act = (TotpActivity) getActivity();

    		if(preferences.getBoolean("slotMode", true)) {
    			credList.add("Slot 1");
    			credList.add("Slot 2");
    		} else {
    			credList = preferences.getStringSet("credentialList", null);
    			if(credList == null) {
    				Preference nocreds = new Preference(act);
    				nocreds.setPersistent(false);
    				nocreds.setKey("nocreds");
    				nocreds.setTitle(R.string.nocreds);
    				nocreds.setEnabled(false);
    				creds.addPreference(nocreds);
    				return;
    			}
    		}
        	for(final String cred : credList) {
            	Preference pref = new Preference(act);
            	pref.setPersistent(false);
            	pref.setKey("cred_" + cred);
            	pref.setTitle(cred);
            	pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
    				public boolean onPreferenceClick(Preference preference) {
    					act.challengeYubiKey(cred);
    					return true;
    				}
    			});
            	creds.addPreference(pref);
        	}
        	}
    }

	@Override
	protected void onStop() {
		super.onStop();
		if(recreating ) {
			recreating = false;
		} else if(!waitingForResult) {
			finish();
		}
	}

	protected void doLoad() {
		Intent loadIntent = new Intent(this, TotpGenerator.class);
		loadIntent.putExtra("load", true);
		loadIntent.putExtra("mode", mode);
		this.startActivityForResult(loadIntent, LOAD);
		waitingForResult = true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_totp, menu);
		if(mode == 0) {
			menu.removeItem(R.id.menu_empty_list);
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_about:
			try {
				PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				AlertDialog.Builder aboutDialog = new AlertDialog.Builder(this);
				aboutDialog.setTitle(R.string.about);
				aboutDialog.setMessage(getText(R.string.version) + " " + packageInfo.versionName);
				aboutDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				aboutDialog.show();
			} catch (NameNotFoundException e) {
				// we should never end up here..
			}
			break;
		case R.id.menu_switch_mode: {
			SharedPreferences preferences = getSharedPreferences(PREF_NAME, 0);
			boolean slotMode = preferences.getBoolean("slotMode", true);
			Editor editor = preferences.edit();
			editor.putBoolean("slotMode", !slotMode);
			editor.commit();
			recreate();
			break;
		}
		case R.id.menu_empty_list: {
			SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, 0).edit();
			editor.putStringSet("credentialList", null);
			editor.commit();
			recreate();
			break;
		}
		}
		return true;
	}

	public void doProgram() {  
		Intent intent = new Intent(
				"com.google.zxing.client.android.SCAN");
		intent.setPackage("com.google.zxing.client.android");
		intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
		intent.putExtra("SAVE_HISTORY", false);
		
		try {
			startActivityForResult(intent, SCAN_BARCODE);
			waitingForResult = true;
		} catch (ActivityNotFoundException e) {
			barcodeScannerNotInstalled(
					getString(R.string.warning),
					getString(R.string.barcode_scanner_not_found),
					getString(R.string.yes),
					getString(R.string.no));
		}
		return;
	}
	
	@Override
	public void recreate() {
		recreating = true;
		super.recreate();
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		waitingForResult = false;
		if (requestCode == SCAN_BARCODE) {
			if (resultCode == RESULT_OK) {
				String content = intent.getStringExtra("SCAN_RESULT");
				Uri uri = Uri.parse(content);
				final String secret = uri.getQueryParameter("secret"); 
				if(secret == null || secret.isEmpty()) {
					Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_LONG).show();
					return;
				}
				AlertDialog.Builder dialog = new AlertDialog.Builder(this);
				if(mode == 0) {
					dialog.setTitle(R.string.program_slot);
					dialog.setItems(R.array.slots, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							programYubiKey(String.valueOf(which + 1), secret);
						}
					});
				} else if(mode == 1) {
					String path = uri.getPath(); // user name is stored in path...
					if(path.charAt(0) == '/') {
						path = path.substring(1);
					}
					dialog.setTitle(R.string.credential_name);
					final EditText input = new EditText(this);
					input.setText(path);
					dialog.setView(input);
					dialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							programYubiKey(input.getText().toString(), secret);
						}
					});
				}
				dialog.show();
			} else {
				Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_LONG).show();
				return;
			}
		} else if(requestCode == PROGRAM) {
			if (resultCode == RESULT_OK) {
				if(mode == 0) {
					int slot = intent.getIntExtra("slot", -1);
					Toast.makeText(this, String.format(this.getString(R.string.prog_success), slot), Toast.LENGTH_LONG).show();
				} else {
					String name = intent.getStringExtra("cred");
					if(!name.isEmpty()) {
						Toast.makeText(this, String.format(this.getString(R.string.prog_success2), name), Toast.LENGTH_LONG).show();
						SharedPreferences prefs = getSharedPreferences(PREF_NAME, 0);
						Set<String> creds = prefs.getStringSet("credentialList", null);
						if(creds == null) {
							creds = new HashSet<String>();
						}
						creds.add(name);
						SharedPreferences.Editor editor = prefs.edit();
						editor.putStringSet("credentialList", creds);
						recreate();
					}
				}
			}
		} else if(requestCode == TOTP) {
			if (resultCode == RESULT_OK) {
				String totp = intent.getStringExtra("totp");
				if(totp != null) {
					showOtpDialog(totp);
				}
			}
		} else if(requestCode == LOAD) {
			if(resultCode == RESULT_OK) {
				List<String> creds = intent.getStringArrayListExtra("credentialList");
				SharedPreferences.Editor prefEdit = getSharedPreferences(PREF_NAME, 0).edit();
				Set<String> credSet = new HashSet<String>(creds);
				prefEdit.putStringSet("credentialList", credSet);
				prefEdit.commit();
				recreate();
			}
		}
	}
	
	private void programYubiKey(String cred, String secret) {
		Log.i(logTag, "Programming slot " + cred);
		Intent programIntent = new Intent(this, TotpGenerator.class);
		programIntent.putExtra("secret", secret);
		if(mode == 0) {
			programIntent.putExtra("slot", Integer.parseInt(cred));
		} else if(mode == 1) {
			programIntent.putExtra("cred", cred);
		}
		programIntent.putExtra("mode", mode);
		this.startActivityForResult(programIntent, PROGRAM);
		waitingForResult = true;
	}
	
	private void barcodeScannerNotInstalled(String stringTitle,
			String stringMessage, String stringButtonYes, String stringButtonNo) {
		AlertDialog.Builder downloadDialog = new AlertDialog.Builder(this);
		downloadDialog.setTitle(stringTitle);
		downloadDialog.setMessage(stringMessage);
		downloadDialog.setPositiveButton(stringButtonYes,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialogInterface, int i) {
						Uri uri = Uri.parse("market://search?q=pname:"
								+ "com.google.zxing.client.android");
						Intent intent = new Intent(Intent.ACTION_VIEW, uri);
						TotpActivity.this.startActivity(intent);
					}
				});
		downloadDialog.setNegativeButton(stringButtonNo,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialogInterface, int i) {
					}
				});
		downloadDialog.show();
	}

	public void challengeYubiKey(String cred) {
		Log.i(logTag, "challenge for " + cred);
		Intent totpIntent = new Intent(this, TotpGenerator.class);
		int slot = 0;
		if(cred.equals("Slot 1")) {
			slot = 1;
		} else if(cred.equals("Slot 2")) {
			slot = 2;
		}
		if(slot == 1 || slot == 2) {
			totpIntent.putExtra("slot", slot);
		} else {
			totpIntent.putExtra("cred", cred);
		}
		totpIntent.putExtra("mode", mode);
		this.startActivityForResult(totpIntent, TOTP);
		waitingForResult = true;
	}
	
	private void showOtpDialog(final String totp) {
		AlertDialog.Builder otpDialog = new AlertDialog.Builder(this);
		TextView input = (TextView) TextView.inflate(this,
				R.layout.otp_display, null);
		input.setText(totp);
		otpDialog.setView(input);

		otpDialog.setTitle(R.string.totp);
		otpDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		otpDialog.setNegativeButton(R.string.copy, new DialogInterface.OnClickListener() {
			@SuppressWarnings("deprecation")
			@TargetApi(Build.VERSION_CODES.HONEYCOMB)
			public void onClick(DialogInterface dialog, int which) {
				if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
					// use the deprecated clipboard api below level 11
					android.text.ClipboardManager clipboard = (android.text.ClipboardManager) TotpActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
					clipboard.setText(totp);
				} else {
					ClipboardManager clipboard = (ClipboardManager) TotpActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
					clipboard.setPrimaryClip(ClipData.newPlainText(TotpActivity.this.getText(R.string.clip_label), totp));
				}
				Toast.makeText(TotpActivity.this, R.string.copied, Toast.LENGTH_SHORT).show();
				dialog.dismiss();
			}
		});
		otpDialog.show();
	}
}
