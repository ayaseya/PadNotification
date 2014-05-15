/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ayaseya.padnotification;

import static com.ayaseya.padnotification.CommonUtilities.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import android.content.Context;
import android.util.Log;

/**
 * Helper class used to communicate with the demo server.
 */

// GCMから端末が登録されたり、解除されるタイミングで、
// サーバアプリにレジストレーションIDを送信するクラスです。
public final class ServerUtilities {

	private static final int MAX_ATTEMPTS = 5;// リトライ回数の上限を設定します。
	private static final int BACKOFF_MILLI_SECONDS = 2000;// リトライ時間の下限を設定します。
	private static final Random random = new Random();
	

	/**
	 * Register this account/device pair within the server.
	 * 
	 */
	static void register(final Context context, final String regId) {
		Log.i(TAG, "registering device (regId = " + regId + ")");
		
		// サーバーの登録URLを設定します。
		String serverUrl = SERVER_URL + "/register";

		// 端末の登録IDをパラメータとして付加します。
		Map<String, String> params = new HashMap<String, String>();
		params.put("regId", regId);

		// 空き時間間隔（exponential backoff：繰り返すごとに空き時間を大きくして、
		// 送信タイミングを分散する）を持って、リトライします。
		long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);

		// Once GCM returns a registration id, we need to register it in the
		// demo server. As the server might be down, we will retry it a couple
		// times.

		for (int i = 1; i <= MAX_ATTEMPTS; i++) {
			Log.d(TAG, "Attempt #" + i + " to register");
			try {
				displayMessage(context, context.getString(
						R.string.server_registering, i, MAX_ATTEMPTS));

				// サーバーにURLとパラメータ（ここでは登録ID）をポストします。
				post(serverUrl, params);

				// サーバーに端末を登録したことをGCMに登録します。
				// GCMRegistrar.setRegisteredOnServer(context, true);

//				String message = context.getString(R.string.server_registered);
//				CommonUtilities.displayMessage(context, message);
				CommonUtilities.displayMessage(context, "\nレジストレーションIDの登録が正常に完了しました。");
				// Persist the regID - no need to register again.
	
				return;

			} catch (IOException e) {
				// 通信エラーなどによりリトライする場合の処理

				// Here we are simplifying and retrying on any error; in a real
				// application, it should retry only on unrecoverable errors
				// (like HTTP error code 503).
				Log.e(TAG, "Failed to register on attempt " + i + ":" + e);
				if (i == MAX_ATTEMPTS) {
				
		
					break;
				}
				try {
					Log.d(TAG, "Sleeping for " + backoff + " ms before retry");
					Thread.sleep(backoff);
				} catch (InterruptedException e1) {
					// Activity finished before we complete - exit.
					Log.d(TAG, "Thread interrupted: abort remaining retries!");
					Thread.currentThread().interrupt();
					return;
				}
				// increase backoff exponentially
				backoff *= 2;
			}
		}
		String message = context.getString(R.string.server_register_error,
				MAX_ATTEMPTS);
		CommonUtilities.displayMessage(context, message);

	}

	/**
	 * Unregister this account/device pair within the server.
	 */
	// 登録解除の処理を行うメソッドです。
	static void unregister(final Context context, final String regId) {
		Log.i(TAG, "unregistering device (regId = " + regId + ")");

		String serverUrl = SERVER_URL + "/unregister";
		Map<String, String> params = new HashMap<String, String>();
		params.put("regId", regId);
		try {
			post(serverUrl, params);
			// GCMRegistrar.setRegisteredOnServer(context, false);
			String message = context.getString(R.string.server_unregistered);
			CommonUtilities.displayMessage(context, message);
		} catch (IOException e) {
			// At this point the device is unregistered from GCM, but still
			// registered in the server.
			// We could try to unregister again, but it is not necessary:
			// if the server tries to send a message to the device, it will get
			// a "NotRegistered" error message and should unregister the device.
			String message = context.getString(
					R.string.server_unregister_error, e.getMessage());
			CommonUtilities.displayMessage(context, message);
		}
	}

	
	/**
	 * Issue a POST request to the server.
	 * 
	 * @param endpoint
	 *            POST address.
	 * @param params
	 *            request parameters.
	 * 
	 * @throws IOException
	 *             propagated from POST.
	 */
	// レジストレーションIDの登録や解除を行うため送信するメソッドです。
	private static void post(String endpoint, Map<String, String> params)
			throws IOException {
		URL url;
		try {
			url = new URL(endpoint);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("invalid url: " + endpoint);
		}
		StringBuilder bodyBuilder = new StringBuilder();// 可変長の文字列、処理速度がStringBufferより速い
		// Iteratorインタフェースはコレクション内の要素に順番にアクセスする手段を提供します。
		Iterator<Entry<String, String>> iterator = params.entrySet().iterator();
		// constructs the POST body using the parameters
		while (iterator.hasNext()) {
			// hasNextメソッドを使用し、次の要素がある間（trueを返す間）
			// whileループ処理を続けます。

			Entry<String, String> param = iterator.next();

			bodyBuilder.append(param.getKey()).append('=')
					.append(param.getValue());
			if (iterator.hasNext()) {
				bodyBuilder.append('&');
			}
		}
		String body = bodyBuilder.toString();

		Log.v(TAG, "Posting '" + body + "' to " + url);

		byte[] bytes = body.getBytes();// サーバーに送信する文字列をバイト配列に変換します。
		HttpURLConnection conn = null;
		try {
			// URLが参照するリモートオブジェクトへの接続を表すURLConnectionオブジェクトを返します。
			conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);// 引数にtrueをセットして呼び出し、POSTによるデータ送信を可能にします。
			conn.setUseCaches(false);// 引数にfalseをセットして呼び出し、キャッシュを利用しないようにします。
			// コンテンツ長が事前にわかっている場合に、内部バッファ処理を行わずに
			// HTTP要求本体のストリーミングを有効にするために使用します。
			conn.setFixedLengthStreamingMode(bytes.length);

			conn.setRequestMethod("POST");// URLの要求のメソッドをPOSTに設定します。
			conn.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded;charset=UTF-8");// 一般要求プロパティーを設定します。
			// post the request
			OutputStream out = conn.getOutputStream();
			out.write(bytes);
			out.close();
			// handle the response
			int status = conn.getResponseCode();// HTTPの応答メッセージから状態コードを取得します。
			if (status == 200) {
				// Log.i(TAG, ".success");
			}
			if (status != 200) {
				// HTTP/1.0 200 OK
				// HTTP/1.0 401 Unauthorized
				throw new IOException("Post failed with error code " + status);
			}
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}
}
