package de.fun2code.android.piratebox;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.paw.server.PawServer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import de.fun2code.android.piratebox.util.NetworkUtil;

/**
 * Activity that displays the info preference screen
 * 
 * @author joschi
 *
 */
public class InfoPreferencesActivity extends PreferenceActivity {
	private Activity activity;
	private SharedPreferences preferences;
	
	private int uploads = 0;
	private int messages = 0;
	
	/**
	 * Broadcast receiver that listens for upload/shoud broadcasts
	 */
	private final BroadcastReceiver infoReceiver = new BroadcastReceiver() {
		
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        
	        // Calculate uploads
	        if(action.equals(Constants.BROADCAST_INTENT_UPLOAD)) {
	        	calculateUploads();
	        }
	        // Calculate shouts
	        else if(action.equals(Constants.BROADCAST_INTENT_SHOUT)) {
	        	calculateMessages();
	        }
 	    }
	};

	@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.info_preferences);
			
			activity = this;
			preferences = PreferenceManager.getDefaultSharedPreferences(this);
		}
	
	@Override
	public void onResume() {
		super.onResume();
		
		try {
			// Set version
			setStringSummary(Constants.PREF_DEV_INFO_PIRATEBOX_VERSION, getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (NameNotFoundException e) {
			 Log.e(Constants.TAG, "PirateBox version not found");
		}
		
		// Set PAW version
		setStringSummary(Constants.PREF_DEV_INFO_PAW_VERSION, PawServer.getServerProperty("version"));
		
		// Show IP address an port
		if(PirateBoxService.isRunning()) {
			//setStringSummary(Constants.PREF_DEV_INFO_AP_IP_ADDRESS, NetworkUtil.getApIp(activity.getApplicationContext()));
			setStringSummary(Constants.PREF_DEV_INFO_IP_ADDRESS, NetworkUtil.getLocalIpAddress());
			setStringSummary(Constants.PREF_DEV_INFO_LOCAL_PORT, PirateBoxService.getServerPort());
		}
		
		// Calculate uploads/shouts
		calculateUploads();
		calculateMessages();
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(Constants.BROADCAST_INTENT_UPLOAD);
		filter.addAction(Constants.BROADCAST_INTENT_SHOUT);
		registerReceiver(infoReceiver, filter);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(infoReceiver);
	}

	/**
	 * Sets the summary string on given preference name
	 * 
	 * @param preference	preference name
	 * @param value			value to set
	 */
	private void setStringSummary(String preference, String value) {
        try {
            findPreference(preference).setSummary(value);
        } catch (RuntimeException e) {
            Log.e(Constants.TAG, "Preference " + preference + " not found");
        }
    }
	
	/**
	 * Updates the upload preference summary
	 */
	private void updateUploads() {
		activity.runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				setStringSummary(Constants.PREF_DEV_INFO_UPLOADS, String.valueOf(uploads));
				
			}
		});
	}
	
	/**
	 * Updates the shout message count
	 */
	private void updateMessages() {
		activity.runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				setStringSummary(Constants.PREF_DEV_INFO_MESSAGES, String.valueOf(messages));
				
			}
		});
	}
	
	/**
	 * Calculates the number of uploads by counting the files inside the
	 * uploads directory
	 */
	private synchronized void calculateUploads() {
		new Thread() {
			@Override
			public void run() {
				String storageDir = preferences.getString(Constants.PREF_STORAGE_DIR, activity.getResources().getString(R.string.pref_storage_dir_default));
				File storageDirFile = new File(storageDir + "/uploads");
				
				uploads = 0;
				
				if(storageDirFile.isDirectory()) {
					for(File file : storageDirFile.listFiles()) {
						if(file.isFile()) {
							uploads++;
						}
					}
				}
				
				
				updateUploads();
			}
		}.start();
	}
	
	/**
	 * Calculates the number of shout messages by counting the lines inside
	 * the data.bso file
	 */
	private synchronized void calculateMessages() {
		new Thread() {
			@Override
			public void run() {
				String storageDir = preferences.getString(Constants.PREF_STORAGE_DIR, activity.getResources().getString(R.string.pref_storage_dir_default));
				File chatFile = new File(storageDir + "/chat/data.bso");
				
				messages = 0;
				
				if(chatFile.exists()) {
					BufferedReader br = null;
					
					try {
						DataInputStream in = new DataInputStream(new FileInputStream(chatFile));
						br = new BufferedReader(new InputStreamReader(in));
						String line;
						
						while ((line = br.readLine()) != null)   {
						  messages++;
						}
					}
					catch(IOException e) {
						Log.e(Constants.TAG, "Unable to count messages");
					}
					finally {
						if(br != null) {
							try {
								br.close();
							} catch (IOException e) {
								// Game over, ignore
							}
						}
					}
				}
				
				updateMessages();
			}
		}.start();
	}
}
