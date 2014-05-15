/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ayaseya.padnotification;

import static com.ayaseya.padnotification.CommonUtilities.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Set;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
	public static final int NOTIFICATION_ID = 1;

	/** マナーモード等の状態取得Intent Filter */
	private static IntentFilter ringerModeIntentFilter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);

	/** 指令を飛ばすBroadCastReceiver */
	private static BroadcastReceiver ringerModeStateChangeReceiver = null;

	/** ヘッドセットプラグ状態取得Intent Filter */
	private static IntentFilter plugIntentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);

	/** 指令を飛ばすBroadCastReceiver */
	private static BroadcastReceiver plugStateChangeReceiver = null;

	private boolean ringerMode = false;

	private boolean isPlugged = false;

	private SoundPool soundPool;

	private int se;

	private NotificationManager mNotificationManager;
	NotificationCompat.Builder builder;

	private Bundle extras;

	private SharedPreferences sharedPreferences;

	private boolean checkbox_sound;

	private boolean checkbox_vibration;

	private Vibrator vibrator;

	private boolean silentMode;

	private boolean vibrateMode;

	private boolean notificationPermission;

	public GcmIntentService() {
		super("GcmIntentService");
	}

	@Override
	public void onCreate() {
		super.onCreate();

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		checkbox_sound = sharedPreferences.getBoolean("checkbox_sound_key", true);
		checkbox_vibration = sharedPreferences.getBoolean("checkbox_vibration_key", false);

		Set<String> multiValues = sharedPreferences.getStringSet("list_preference", null);
		ArrayList<String> timeZone = new ArrayList<String>();

		if (multiValues != null) {

			timeZone.addAll(multiValues);
			Collections.sort(timeZone);
			for (int i = 0; i < timeZone.size(); i++) {
				//				Log.v(TAG, "TimeZone=" + timeZone.get(i));
			}

			Calendar calendar = Calendar.getInstance();
			int hour = calendar.get(Calendar.HOUR_OF_DAY);

			if (timeZone.indexOf(String.valueOf(hour)) == -1) {
				notificationPermission = false;
				//				Log.v(TAG, "通知が許可された時間帯ではありません。");
			} else {
				notificationPermission = true;
				//				Log.v(TAG, "通知が許可された時間帯です。");
			}
		}

		vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

		ringerModeStateChangeReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				// 接続状態を取得
				if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
					if (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1) == AudioManager.RINGER_MODE_NORMAL) {
						// 通常モード
						ringerMode = true;
					} else {
						ringerMode = false;

					}

					if (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1) == AudioManager.RINGER_MODE_VIBRATE) {
						// マナーモード
						vibrateMode = true;

					} else {

						vibrateMode = false;

					}

					if (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1) == AudioManager.RINGER_MODE_SILENT) {
						// サイレントモード
						silentMode = true;

					} else {

						silentMode = false;

					}
				}
			}
		};

		plugStateChangeReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				// 接続状態を取得
				if (intent.getIntExtra("state", 0) > 0) {
					isPlugged = true;
					//					Log.v(TAG, "プラグIN");
				} else {
					isPlugged = false;
					//					Log.v(TAG, "プラグOUT");
				}
			}
		};

		// Broadcast Receiverを登録します。
		registerReceiver(plugStateChangeReceiver, plugIntentFilter);
		registerReceiver(ringerModeStateChangeReceiver, ringerModeIntentFilter);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Broadcast Receiver登録解除
		unregisterReceiver(plugStateChangeReceiver);
		unregisterReceiver(ringerModeStateChangeReceiver);
	}

	@Override
	protected void onHandleIntent(final Intent intent) {

		extras = intent.getExtras();

		GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
		// The getMessageType() intent parameter must be the intent you received
		// in your BroadcastReceiver.
		String messageType = gcm.getMessageType(intent);

		//		displayMessage(this, extras.toString());

		if (!extras.isEmpty()) { // has effect of unparcelling Bundle
			/*
			 * Filter messages based on message type. Since it is likely that GCM will be
			 * extended in the future with new message types, just ignore any message types you're
			 * not interested in, or that you don't recognize.
			 */
			if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
				//                sendNotification("Send error: " + extras.toString());
				Log.v(TAG, "Send error: " + extras.toString());
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
				//                sendNotification("Deleted messages on server: " + extras.toString());
				Log.v(TAG, "Deleted messages on server: " + extras.toString());
				// If it's a regular GCM message, do some work.
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
				// This loop represents the service doing some work.
				for (int i = 0; i < 5; i++) {
					Log.i(TAG, "Working... " + (i + 1)
							+ "/5 @ " + SystemClock.elapsedRealtime());
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
					}
				}
				Log.i(TAG, "Completed work @ " + SystemClock.elapsedRealtime());
				// Post notification of received message.

				// 音声の読み込み時間の間にIntentServiceが終了してしまうので
				// 非同期処理で別スレッドに処理を任せます。
				new AsyncTask<Void, Void, Void>() {

					@Override
					protected Void doInBackground(Void... params) {
						//						Log.v(TAG, "doInBackground()");

						// soundPoolのインスタンスを取得します。
						soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
						// soundPoolの音声ファイルのロード完了をListenerする匿名クラスを設定します。
						soundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {

							@Override
							public void onLoadComplete(final SoundPool soundPool, int sampleId, int status) {
								if (status == 0) {

									if (notificationPermission) {// 設定で許可された時間帯かの判定です。

										// Notificationの通知を実行します。
										sendNotification("公式サイトからのお知らせが" + extras.get("INDEX") + "件あります。", intent);

										//モードによって通知音、振動の有無の振り分けを行います。

										if (isPlugged) {// イヤホンが接続している場合です。
										//											Log.v(TAG, "イヤホンが接続されています。");
											if (ringerMode) {// 通常モード
												Log.v(TAG, "通常モード");
												if (checkbox_sound) {
													soundPool.play(se, 0.5F, 0.5F, 0, 0, 1.0F);
												}
												if (checkbox_vibration) {
													vibrator.vibrate(1500);
												}

											} else if (vibrateMode) {// マナーモード
												Log.v(TAG, "マナーモード");
												if (checkbox_vibration) {
													vibrator.vibrate(1500);
												}

											} else if (silentMode) {// サイレントモード
												Log.v(TAG, "サイレントモード");
												if (checkbox_sound) {
													soundPool.play(se, 0.5F, 0.5F, 0, 0, 1.0F);
												}
											}

										} else {// イヤホンが接続していない場合です。
										//											Log.v(TAG, "イヤホンが接続されていません。");
											if (ringerMode) {// 通常モード
												Log.v(TAG, "通常モード");
												if (checkbox_sound) {
													soundPool.play(se, 0.5F, 0.5F, 0, 0, 1.0F);
												}
												if (checkbox_vibration) {
													vibrator.vibrate(1500);
												}

											} else if (vibrateMode) {// マナーモード
												Log.v(TAG, "マナーモード");
												if (checkbox_vibration) {
													vibrator.vibrate(1500);
												}

											} else if (silentMode) {// サイレントモード
												Log.v(TAG, "サイレントモード");
												if (checkbox_sound) {
													soundPool.play(se, 0.5F, 0.5F, 0, 0, 1.0F);
												}
											}
										}

									}
									// 時間差でsoundPoolのインスタンスを開放します。
									new Thread(new Runnable() {

										@Override
										public void run() {

											try {
												Thread.sleep(30000);
											} catch (InterruptedException e) {
											}
											//											Log.v(TAG, "soundPool.release()");

											soundPool.release();

										}
									}).start();
								}
							}
						});

						// soundPoolに音声ファイルを読み込みます。
						se = soundPool.load(GcmIntentService.this, R.raw.notification_sound, 1);

						return null;
					}

				}.execute(null, null, null);

				//				sendNotification("公式サイトからのお知らせが" + extras.get("INDEX") + "件あります。", intent);
				//                sendNotification("Received: " + extras.toString());
				Log.i(TAG, "Received: " + extras.toString());
			}
		}
		// Release the wake lock provided by the WakefulBroadcastReceiver.
		GcmBroadcastReceiver.completeWakefulIntent(intent);
	}

	// Put the message into a notification and post it.
	// This is just one simple example of what you might choose to do with
	// a GCM message.
	//	private void sendNotification(String msg) {
	//
	//		mNotificationManager = (NotificationManager)
	//				this.getSystemService(Context.NOTIFICATION_SERVICE);
	//
	//		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
	//				new Intent(this, NolNotificationActivity.class), 0);
	//
	//		NotificationCompat.Builder mBuilder =
	//				new NotificationCompat.Builder(this)
	//						.setSmallIcon(R.drawable.ic_stat_gcm)
	//						.setContentTitle(getResources().getString(R.string.app_name))
	//						.setStyle(new NotificationCompat.BigTextStyle()
	//								.bigText(msg))
	//						.setContentText(msg);
	//
	//		mBuilder.setContentIntent(contentIntent);
	//		mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
	//	}

	private void sendNotification(String msg, Intent i) {

		mNotificationManager = (NotificationManager)
				this.getSystemService(Context.NOTIFICATION_SERVICE);

		Intent intent = new Intent(this, DialogActivity.class);
		intent.putExtra("UPDATE", i);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				intent, 0);

		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
						.setSmallIcon(R.drawable.ic_launcher)
						.setContentTitle(getResources().getString(R.string.app_name))
						.setStyle(new NotificationCompat.BigTextStyle()
								.bigText(msg))
						.setContentText(msg);

		mBuilder.setContentIntent(contentIntent);
		mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
	}

}
