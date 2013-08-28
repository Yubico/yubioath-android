package com.yubico.yubioath.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.yubico.yubioath.R;

public class TotpWidgetConfigure extends Activity {
	
	private static final String PREF_NAME = "com.yubico.yubioath.legacy.TotpWidgetConfigure.Preferences";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setResult(RESULT_CANCELED);
		
		Intent intent = getIntent();
        
        final int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        
        if(appWidgetId == -1) {
        	finish();
        }
        
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.which_slot);
        dialog.setItems(R.array.slots, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				setSelectedSlot(TotpWidgetConfigure.this, appWidgetId, which + 1);
				
				Context context = TotpWidgetConfigure.this;
				
	            // Push widget update to surface with newly set prefix
	            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
	            TotpWidgetProvider.updateAppWidget(context, appWidgetManager,
	                    appWidgetId, null);

	            // Make sure we pass back the original appWidgetId
	            Intent resultValue = new Intent();
	            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
	            setResult(RESULT_OK, resultValue);
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
	
	@Override
	protected void onPause() {
		super.onPause();
		finish();
	}

	public static void setSelectedSlot(Context context, int appWidgetId, int slot) {
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREF_NAME, 0).edit();
		prefs.putInt(appWidgetId + "_slot", slot);
		prefs.commit();
	}
	
	public static int getSelectedSlot(Context context, int appWidgetId) {
		SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, 0);
		return prefs.getInt(appWidgetId + "_slot", 0);
	}
	
	public static void deletePrefs(Context context, int appWidgetId) {
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREF_NAME, 0).edit();
		prefs.remove(appWidgetId + "_slot");
		prefs.remove(appWidgetId + "_width");
		prefs.commit();
	}
	
	public static void setWidth(Context context, int appWidgetId, int width) {
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREF_NAME, 0).edit();
		prefs.putInt(appWidgetId + "_width", width);
		prefs.commit();
	}
	
	public static int getWidth(Context context, int appWidgetId) {
		SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, 0);
		return prefs.getInt(appWidgetId + "_width", -1);
	}
}
