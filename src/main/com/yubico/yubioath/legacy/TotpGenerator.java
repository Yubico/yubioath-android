package com.yubico.yubioath.legacy;

import java.io.IOException;
import java.util.ArrayList;

import com.yubico.yubioath.R;
import org.apache.commons.codec.binary.Base32;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

//import com.yubico.base.Configurator;

public class TotpGenerator extends Activity {

	private static final String logTag = "TotpGenerator";

	private static final byte[] selectStandardCommand = {0x00, (byte) 0xA4, 0x04, 0x00, 0x07, (byte) 0xA0, 0x00, 0x00, 0x05, 0x27, 0x20, 0x01, 0x00};
	private static final byte[] totpCommand = {0x00, 0x01, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00};
	
	private static final byte[] selectExtendedCommand = {0x00, (byte)0xa4, 0x04, 0x00, 0x07, (byte)0xa0, 0x00, 0x00, 0x05, 0x27, 0x21, 0x01};
	private static final byte[] listCommand = {0x00, (byte) 0xa1, 0x00, 0x00};
	private static final byte[] calcCommand = {0x00, (byte) 0xa2, 0x00, 0x00, 0x00, 0x7a };
	private static final byte[] putCommand = {0x00, 0x01, 0x00, 0x00, 0x00, 0x7a };

	private static final int totp_step = 30;

	private static final byte SLOT_CONFIG = 0x01;
	private static final byte SLOT_CONFIG2 = 0x03;

	private static final byte SLOT_CHAL_HMAC1 = 0x30;
	private static final byte SLOT_CHAL_HMAC2 = 0x38;

	private AlertDialog swipeDialog;

	private static final int STATE_PROGRAMMING = 1;
	private static final int STATE_CHALLENGE = 2;
	private static final int STATE_LOAD = 3;

	private PendingIntent tagIntent;
	
	private int mode = 0;

	@Override
	protected void onResume() {
		super.onResume();
		
		Log.d(logTag, "Resuming TotpGenerator");

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		
		setResult(RESULT_CANCELED);
				
		if(extras != null) {
			int state = extras.getInt("state");
			if(state != 0) {
				return;
			}

			mode = extras.getInt("mode", 0);
			int slot = extras.getInt("slot");
			String secret = extras.getString("secret");
			boolean load = extras.getBoolean("load", false);
			String credName = extras.getString("cred");

			if(load) {
				loadCreds();
			} else if(secret != null && slot != 0) {
				programYubiKey(String.valueOf(slot), secret);
			} else if(secret != null && credName != null && !credName.isEmpty()) {
				programYubiKey(credName, secret);
			} else if(slot != 0) {
				challengeYubiKey(String.valueOf(slot));
			} else if(credName != null && !credName.isEmpty()) {
				challengeYubiKey(credName);
			} else {
				finish();
			}
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		if(swipeDialog != null) {
			swipeDialog.dismiss();
			swipeDialog = null;
		}
		disableDispatch();
	}
	
	private void loadCreds() {
		AlertDialog.Builder loadDialog = new AlertDialog.Builder(this);
		loadDialog.setTitle(R.string.loading);
		loadDialog.setMessage(R.string.swipe_and_hold);
		loadDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		if(swipeDialog != null) {
			disableDispatch();
			swipeDialog.dismiss();
		}
		swipeDialog = loadDialog.show();
		enableDispatch(STATE_LOAD, null, null);
	}

	private void challengeYubiKey(String cred) {
		AlertDialog.Builder challengeDialog = new AlertDialog.Builder(this);
		challengeDialog.setTitle(R.string.challenging);
		challengeDialog.setMessage(R.string.swipe);
		challengeDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		if(swipeDialog != null) {
			disableDispatch();
			swipeDialog.dismiss();
		}
		swipeDialog = challengeDialog.show();
		enableDispatch(STATE_CHALLENGE, cred, null);
	}

	private void programYubiKey(String credName, String secret) {
		AlertDialog.Builder programDialog = new AlertDialog.Builder(this);
		programDialog.setTitle(R.string.programming);
		programDialog.setMessage(R.string.swipe_and_hold);
		programDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		if(swipeDialog != null) {
			disableDispatch();
			swipeDialog.dismiss();
		}
		swipeDialog = programDialog.show();
		enableDispatch(STATE_PROGRAMMING, credName, secret);
	}


	private void enableDispatch(int state, String cred, String secret) {
		Intent intent = getIntent();
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.putExtra("state", state);
		intent.putExtra("cred", cred);
		if(secret != null) {
			intent.putExtra("secret", secret);
		} else {
			intent.removeExtra("secret");
		}
		tagIntent = PendingIntent.getActivity(
				this, 0, intent, 0);

		IntentFilter iso = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);

		NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
		if(adapter == null) {
			Toast.makeText(this, R.string.no_nfc, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		if(adapter.isEnabled()) {
			// register for foreground dispatch so we'll receive tags according to our intent filters
			NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(
					this, tagIntent, new IntentFilter[] {iso},
					new String[][] { new String[] { IsoDep.class.getName() } }
					);
		} else {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setTitle(R.string.nfc_off);
			dialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					Intent settings = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
					TotpGenerator.this.startActivity(settings);
					dialog.dismiss();
					finish();
				}
			});
			dialog.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					finish();
				}
			});
			dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					finish();
				}
			});
			dialog.show();
		}
	}

	private void disableDispatch() {
		if(tagIntent != null) {
			tagIntent.cancel();
			tagIntent = null;
		}
		NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
		if(adapter != null) {
			adapter.disableForegroundDispatch(this);
		}
	}

	public void onNewIntent(Intent intent) {
		int state = intent.getIntExtra("state", -1);
		String cred = intent.getStringExtra("cred");
		if(state >= 0) {
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			if(tag != null) {
				IsoDep isoTag = IsoDep.get(tag);
				try {
					isoTag.connect();
					byte[] selectCommand = selectStandardCommand;
					if(mode == 1) {
						selectCommand = selectExtendedCommand;
					}
					byte[] resp = isoTag.transceive(selectCommand);
					int length = resp.length;
					if(resp[length - 2] == (byte)0x90 && resp[length - 1] == 0x00) {
						switch(state) {
						case STATE_PROGRAMMING:
							doProgramYubiKey(isoTag, cred, intent.getStringExtra("secret"), mode);
							break;
						case STATE_CHALLENGE:
							doChallengeYubiKey(isoTag, cred, mode);
							break;
						case STATE_LOAD:
							doLoad(isoTag);
							break;
						default:
						}
					} else {
						Toast.makeText(this, R.string.tag_error, Toast.LENGTH_LONG).show();
					}
					isoTag.close();
				} catch (TagLostException e) {
					Toast.makeText(this, R.string.lost_tag, Toast.LENGTH_LONG).show();
				} catch (IOException e) {
					Toast.makeText(this, getText(R.string.tag_error) + e.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
			finish();
		}
	}

	private void doLoad(IsoDep isoTag) throws IOException {
		byte[] list = isoTag.transceive(listCommand);
		if(list[list.length - 2] == (byte)0x90 && list[list.length - 1] == 0x00 && list[0] == (byte)0xa1) {
			ArrayList<String> credList = new ArrayList<String>();
			int offs = 1;
			int len = list[offs++];
			while(offs < len) {
				String name = "";
				offs++;
				int length = list[offs++];
				for(int i = 0; i < length; i++) {
					name += (char)list[offs + i];
				}
				credList.add(name);
				offs += length;
			}
			Intent data = getIntent();
			data.putStringArrayListExtra("credentialList", credList);
			setResult(RESULT_OK, data);
		} else {
			Toast.makeText(this, R.string.load_failed, Toast.LENGTH_LONG).show();
		}
	}

	private void doChallengeYubiKey(IsoDep isoTag, String cred, int mode) throws IOException {
		long time = System.currentTimeMillis() / 1000 / totp_step;
		byte apdu[];
		int offs = 0;
		if(mode == 0) {
			apdu = new byte[totpCommand.length + 4];
			int slot = Integer.parseInt(cred);
			System.arraycopy(totpCommand, 0, apdu, 0, totpCommand.length);

			switch(slot) {
			case 1:
				apdu[2] = SLOT_CHAL_HMAC1;
				break;
			case 2:
				apdu[2] = SLOT_CHAL_HMAC2;
				break;
			}
			offs = totpCommand.length;
		} else if(mode == 1) {
			byte[] rawCred = cred.getBytes();
			int length = calcCommand.length + rawCred.length + 11;
			apdu = new byte[length];
			System.arraycopy(calcCommand, 0, apdu, 0, calcCommand.length);
			apdu[4] = (byte) (length - 5);
			apdu[6] = (byte) rawCred.length;
			System.arraycopy(rawCred, 0, apdu, 7, rawCred.length);
			offs = 7 + cred.length();
			apdu[offs++] = 0x7d;
			apdu[offs++] = 0x08;
			offs += 4;
		} else {
			Toast.makeText(this, R.string.totp_failed, Toast.LENGTH_LONG).show();
			return;
		}

		apdu[offs] = (byte) (time >> 24);
		apdu[offs + 1] = (byte) (time >> 16);
		apdu[offs + 2] = (byte) (time >> 8);
		apdu[offs + 3] = (byte) time;

		byte[] totpApdu = isoTag.transceive(apdu);
		if(totpApdu[totpApdu.length - 2] == (byte)0x90 && totpApdu[totpApdu.length - 1] == 0x00) {
			int offset = totpApdu[totpApdu.length - 3] & 0xf;
			if(mode == 1) { // in mode 1 we get status and length first
				offset += 2;
			}
			int code = ((totpApdu[offset++] & 0x7f) << 24) |
					((totpApdu[offset++] & 0xff) << 16) |
					((totpApdu[offset++] & 0xff) << 8) |
					((totpApdu[offset++] & 0xff));
			String totp = String.format("%06d", code % 1000000);
			Intent data = getIntent();
			data.putExtra("totp", totp);
			setResult(RESULT_OK, data);
		} else {
			Toast.makeText(this, R.string.totp_failed, Toast.LENGTH_LONG).show();
		}
	}

	private void doProgramYubiKey(IsoDep isoTag, String cred, String secret, int mode) throws IOException {
		Base32 base32 = new Base32();
		byte[] decoded = base32.decode(secret.toUpperCase());
		byte[] apdu;
		if(mode == 0) {
			byte[] key = new byte[20];
			System.arraycopy(decoded, 0, key, 0, decoded.length);
			int slot = Integer.parseInt(cred);

			apdu = new byte[64];
			apdu[1] = 0x01;
			apdu[2] = slot == 1 ? SLOT_CONFIG : SLOT_CONFIG2;

			/*Configurator cfg = new Configurator();
			cfg.setKey(Configurator.HMAC_SHA1_MODE, key);
			cfg.setCfgFlags((byte) (Configurator.CFGFLAG_CHAL_HMAC | Configurator.CFGFLAG_HMAC_LT64));
			cfg.setTktFlags(Configurator.TKTFLAG_CHAL_RESP);
			cfg.setExtFlags((byte) (Configurator.EXTFLAG_SERIAL_API_VISIBLE | Configurator.EXTFLAG_ALLOW_UPDATE));
			byte[] structure = cfg.getConfigStructure();
			apdu[4] = (byte) structure.length;
			System.arraycopy(structure, 0, apdu, 5, structure.length);*/
		} else if(mode == 1) {
			byte[] rawCred = cred.getBytes();
			int length = putCommand.length + decoded.length + rawCred.length + 4;
			apdu = new byte[length];
			System.arraycopy(putCommand, 0, apdu, 0, putCommand.length);
			apdu[4] = (byte) (length - 5);
			int offs = putCommand.length;
			apdu[offs++] = (byte) rawCred.length;
			System.arraycopy(rawCred, 0, apdu, offs, rawCred.length);
			offs += rawCred.length;
			apdu[offs++] = 0x7b; // key
			apdu[offs++] = 0x01; // hmac-sha1
			apdu[offs++] = (byte) decoded.length;
			System.arraycopy(decoded, 0, apdu, offs, decoded.length);
		} else {
			Toast.makeText(this, R.string.prog_fail, Toast.LENGTH_LONG).show();
			setResult(RESULT_CANCELED);
			return;
		}
		byte[] resp = isoTag.transceive(apdu);
		if(resp[resp.length - 2] == (byte)0x90 && resp[resp.length - 1] == 0x00) {
			setResult(RESULT_OK, getIntent());
		} else {
			Toast.makeText(this, R.string.prog_fail, Toast.LENGTH_LONG).show();
			setResult(RESULT_CANCELED);
		}
	}
}
