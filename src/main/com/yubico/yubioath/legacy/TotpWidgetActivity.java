package com.yubico.yubioath.legacy;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.util.Log;

public class TotpWidgetActivity extends Activity {
	private static final String logTag = "TotpWidgetActivity";
	
	private static final int TOTP = 0;	

	@Override
	protected void onResume() {
		super.onResume();

		Intent intent = getIntent();
		int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		
		if(appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
			int slot = TotpWidgetConfigure.getSelectedSlot(this, appWidgetId);
			if(slot == 1 || slot == 2) {
				Log.i(logTag, "challenge for slot " + slot);
				Intent totpIntent = new Intent(this, TotpGenerator.class);
				totpIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
				totpIntent.putExtra("slot", slot);
				this.startActivityForResult(totpIntent, TOTP);
			} else {
				TotpWidgetProvider.updateAppWidget(this, AppWidgetManager.getInstance(this), appWidgetId, null);
				finish();
			}
		} else {
			finish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		Log.i(logTag, "Received result");
		
		if(requestCode == TOTP && resultCode == RESULT_OK) {
			String totp = data.getStringExtra("totp");
			int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
			if(totp != null && appWidgetId != -1) {
				TotpWidgetProvider.updateAppWidget(this, AppWidgetManager.getInstance(this), appWidgetId, totp);
			}
		}
		finish();
	}
}
