package com.ayaseya.padnotification;
import static com.ayaseya.padnotification.CommonUtilities.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class PadNotificationActivity extends Activity {

	public static final String EXTRA_MESSAGE = "message";
	public static final String PROPERTY_REG_ID = "registration_id";
	private static final String PROPERTY_APP_VERSION = "appVersion";
	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	/**
	 * Substitute you own sender ID here. This is the project number you got
	 * from the API Console, as described in "Getting Started."
	 */
	// String SENDER_ID = "977505068557";

	/**
	 * Tag used on log messages.
	 */


	TextView mDisplay;
	GoogleCloudMessaging gcm;
	AtomicInteger msgId = new AtomicInteger();
	Context context;

	String regid;
	private AsyncTask<Void, Void, Void> mRegisterTask;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_pad_notification);

		Log.v(TAG, "++++++++++++++++++++++++++++++++++++++++++++++++++");

		mDisplay = (TextView) findViewById(R.id.display);
		context = getApplicationContext();

		// registerReceiverの登録を行う
		// 第一引数 BroadcastReceiver,第二引数 IntentFilter
		// mHandleMessageReceiverは内部クラスとして最下部で定義しています。
		registerReceiver(mHandleMessageReceiver, 
				new IntentFilter(DISPLAY_MESSAGE_ACTION));

		// NotificationがクリックされActivityが呼び出された時に
		// Notificationを非表示にする処理
		NotificationManager mNotificationManager = (NotificationManager) this
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(GcmIntentService.NOTIFICATION_ID);
		


		// Check device for Play Services APK. If check succeeds, proceed with
		// GCM registration.
		if (checkPlayServices()) {
			gcm = GoogleCloudMessaging.getInstance(this);

			regid = getRegistrationId(context);

			// レジストレーションIDがShared Preferencesに保存されているか確認します。
			if (regid.isEmpty()) {
				registerInBackground();// レジストレーションIDの登録とサーバーへの送信処理を実行します。
				mDisplay.append("レジストレーションIDの初回登録を実行します..." + "\n");
			} else {
				mDisplay.append("レジストレーションIDは登録済みです。" + "\n");
			}
		} else {
			Log.i(TAG, "No valid Google Play Services APK found.");
		}

//		findViewById(R.id.registration).setOnClickListener(
//				new OnClickListener() {
//
//					@Override
//					public void onClick(View v) {
//						// Log.v(TAG, "登録");
//						sendRegistrationIdToBackend();
//					}
//				});
//
//		findViewById(R.id.unregister).setOnClickListener(new OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
//				// Log.v(TAG, "解除");
//				releaseRegistrationIdToBackend();
//			}
//		});
		
//		findViewById(R.id.notification).setOnClickListener(new OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
//				 Log.v(TAG, "Notification");
//				sendNotification("テスト表示です");
//			}
//		});

	}

	
	
	





	@Override
	protected void onResume() {
		super.onResume();
		// Check device for Play Services APK.
		checkPlayServices();
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it
	 * doesn't, display a dialog that allows users to download the APK from the
	 * Google Play Store or enable it in the device's system settings.
	 */
	private boolean checkPlayServices() {
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this,
						PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else {
				Log.i(TAG, "This device is not supported.");
				finish();
			}
			return false;
		}
		return true;
	}

	/**
	 * Stores the registration ID and the app versionCode in the application's
	 * {@code SharedPreferences}.
	 * 
	 * @param context
	 *            application's context.
	 * @param regId
	 *            registration ID
	 * 
	 *            Shared PreferencesにレジストレーションIDとアプリのversion情報を保存します。
	 */
	private void storeRegistrationId(Context context, String regId) {
		final SharedPreferences prefs = getGcmPreferences(context);
		int appVersion = getAppVersion(context);
		Log.i(TAG, "Saving regId on app version " + appVersion);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_REG_ID, regId);
		editor.putInt(PROPERTY_APP_VERSION, appVersion);
		editor.commit();
		// Log.v(TAG, "storeRegistrationId()");
	}

	/**
	 * Gets the current registration ID for application on GCM service, if there
	 * is one.
	 * <p>
	 * If result is empty, the app needs to register.
	 * 
	 * @return registration ID, or empty string if there is no existing
	 *         registration ID.
	 * 
	 *         Shared Preferencesに保存したレジストレーションIDを呼び出します。
	 * 
	 */
	private String getRegistrationId(Context context) {
		final SharedPreferences prefs = getGcmPreferences(context);
		String registrationId = prefs.getString(PROPERTY_REG_ID, "");
		if (registrationId.isEmpty()) {
			Log.i(TAG, "Registration not found.");
			return "";
		}
		// Check if app was updated; if so, it must clear the registration ID
		// since the existing regID is not guaranteed to work with the new
		// app version.
		int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION,
				Integer.MIN_VALUE);
		int currentVersion = getAppVersion(context);
		if (registeredVersion != currentVersion) {
			Log.i(TAG, "App version changed.");
			return "";
		}
		return registrationId;
	}

	/**
	 * Registers the application with GCM servers asynchronously.
	 * <p>
	 * Stores the registration ID and the app versionCode in the application's
	 * shared preferences.
	 * 
	 * GCMサーバーにアプリを登録してレジストレーションIDを取得します。 また、レジストレーションIDを管理する自前のサーバーに
	 * レジストレーションIDを送信して登録します。
	 */
	private void registerInBackground() {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				String msg = "";
				try {
					if (gcm == null) {
						gcm = GoogleCloudMessaging.getInstance(context);
					}
					regid = gcm.register(SENDER_ID);
//					msg = "Device registered, registration ID = " + regid;
		
					// You should send the registration ID to your server over
					// HTTP, so it
					// can use GCM/HTTP or CCS to send messages to your app.
					sendRegistrationIdToBackend();// 自前のサーバーにレジストレーションIDを送信し登録します。

					// For this demo: we don't need to send it because the
					// device will send
					// upstream messages to a server that echo back the message
					// using the
					// 'from' address in the message.

		
				} catch (IOException ex) {
					msg = "Error :" + ex.getMessage();
					// If there is an error, don't just keep trying to register.
					// Require the user to click a button again, or perform
					// exponential back-off.
				}
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				mDisplay.append(msg + "\n");

			}
		}.execute(null, null, null);
	}

	// Send an upstream message.
	public void onClick(final View view) {

//		if (view == findViewById(R.id.send)) {
//			new AsyncTask<Void, Void, String>() {
//				@Override
//				protected String doInBackground(Void... params) {
//					String msg = "";
//					try {
//						Bundle data = new Bundle();
//						data.putString("my_message", "Hello World");
//						data.putString("my_action",
//								"com.google.android.gcm.demo.app.ECHO_NOW");
//						String id = Integer.toString(msgId.incrementAndGet());
//						gcm.send(SENDER_ID + "@gcm.googleapis.com", id, data);
//						msg = "Sent message";
//					} catch (IOException ex) {
//						msg = "Error :" + ex.getMessage();
//					}
//					return msg;
//				}
//
//				@Override
//				protected void onPostExecute(String msg) {
//					mDisplay.append(msg + "\n");
//				}
//			}.execute(null, null, null);
//		} else if (view == findViewById(R.id.clear)) {
//			mDisplay.setText("");
//		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mHandleMessageReceiver);// レシーバーの解除
	}

	/**
	 * @return Application's version code from the {@code PackageManager}.
	 * 
	 *         version情報を取得します。
	 */
	private static int getAppVersion(Context context) {
		try {
			PackageInfo packageInfo = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0);
			return packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			// should never happen
			throw new RuntimeException("Could not get package name: " + e);
		}
	}

	/**
	 * @return Application's {@code SharedPreferences}.
	 * 
	 *         Shared Preferencesのインスタンスを取得します。
	 */
	private SharedPreferences getGcmPreferences(Context context) {
		// This sample app persists the registration ID in shared preferences,
		// but
		// how you store the regID in your app is up to you.
		return getSharedPreferences(
				PadNotificationActivity.class.getSimpleName(),
				Context.MODE_PRIVATE);
	}

	/**
	 * Sends the registration ID to your server over HTTP, so it can use
	 * GCM/HTTP or CCS to send messages to your app. Not needed for this demo
	 * since the device sends upstream messages to a server that echoes back the
	 * message using the 'from' address in the message.
	 * 
	 * レジストレーションIDを自前のサーバーに送信して登録します。
	 */
	private void sendRegistrationIdToBackend() {
		// Your implementation here.
		// Log.v(TAG, "sendRegistrationIdToBackend()");

		final Context context = this;
		// 非同期処理で別スレッドに登録処理を任せます。（GUIスレッドではHTTP通信はできないため）
		mRegisterTask = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				ServerUtilities.register(context, regid);
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {

				
				mRegisterTask = null;
			}

		};
		mRegisterTask.execute(null, null, null);
	}

	/**
	 * Sends the registration ID to your server over HTTP, so it can use
	 * GCM/HTTP or CCS to send messages to your app. Not needed for this demo
	 * since the device sends upstream messages to a server that echoes back the
	 * message using the 'from' address in the message.
	 * 
	 * レジストレーションIDの登録を解除します。
	 */
	private void releaseRegistrationIdToBackend() {
		// Your implementation here.
		// Log.v(TAG, "sendRegistrationIdToBackend()");

		final Context context = this;
		// 非同期処理で別スレッドに登録処理を任せます。（GUIスレッドではHTTP通信はできないため）
		mRegisterTask = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				ServerUtilities.unregister(context, regid);
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				mDisplay.append("レジストレーションIDの登録を解除しました。" + "\n");
				mRegisterTask = null;
			}

		};
		mRegisterTask.execute(null, null, null);
	}

	// BroadcastReceiver、onReceive（）に受信時の挙動を記載しています。
	private final BroadcastReceiver mHandleMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Intentに添付された文字列情報を取り出しTextViewに追記します。
			String newMessage = intent.getExtras().getString(EXTRA_MESSAGE);
			if(newMessage.equals("\nレジストレーションIDの登録が正常に完了しました。")){
				// Persist the regID - no need to register again.
				storeRegistrationId(context, regid);// レジストレーションIDをSharedPreferencesに保存します。
				Intent defaultIntent = new Intent(PadNotificationActivity.this, SettingActivity.class);
				startActivity(defaultIntent);
			}
			mDisplay.append(newMessage + "\n");
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.pad_notification, menu);
		menu.findItem(R.id.action_settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		return true;
		
		
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	
		
		int id = item.getItemId();

		if (id == R.id.action_settings) {
			Intent intent = new Intent(this, SettingActivity.class);
			startActivity(intent);
			
		}
//		else if(id==R.id.action_notification){
//			sendNotification("パズドラ運営サイトからのお知らせが3件あるにゃ");
//		}
		return super.onOptionsItemSelected(item);
		
		
	}
	
	
	
	

    private void sendNotification(String msg) {
    	NotificationManager mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, Bastet888Activity.class), 0);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
        .setSmallIcon(R.drawable.bastet888)
        .setContentTitle("バスにゃんが見てる")
        .setStyle(new NotificationCompat.BigTextStyle()
        .bigText(msg))
        .setContentText(msg);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(1, mBuilder.build());
    }

}
