package com.ayaseya.padnotification;

import static com.ayaseya.padnotification.CommonUtilities.*;

import java.util.ArrayList;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;

public class DialogActivity extends Activity {

	private ArrayList<String> title = new ArrayList<String>();
	private ArrayList<String> url = new ArrayList<String>();
	private ArrayList<String> icon = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// タイトルを非表示にします。
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.dialog_pad_notification);

		//ダイアログの縦横幅を最大にします。
		getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

		Intent intent = getIntent();
		if (intent != null) {
			Bundle extras = intent.getExtras();
//			Log.v(TAG, "extras ="+extras.toString());
			
			Intent update = null;
			if (extras != null) {
				update = extras.getParcelable("UPDATE");
//				Log.v(TAG, "update ="+update.toString());
			}

			if (update != null) {

				extras = update.getExtras();
//				Log.v(TAG, "extras ="+extras.toString());
				
				int index = Integer.parseInt((String) extras.get("INDEX"));

				Log.v(TAG, "index=" + index);

				if (index != 0) {
					for (int i = 0; i < index; i++) {
						title.add((String) extras.get("TITLE" + (i + 1)));
						url.add((String) extras.get("URL" + (i + 1)));
						icon.add((String) extras.get("ICON" + (i + 1)));

						Log.v(TAG, title.get(i));
						Log.v(TAG, url.get(i));
						Log.v(TAG, icon.get(i));
					}
				}
			}
		}

		//		title.add("1.xxxxx");
		//		url.add("http://www.gamecity.ne.jp/nol/");
		//		icon.add("f01");
		//
		//		title.add("2.xxxxx");
		//		url.add("http://www.gamecity.ne.jp/nol/");
		//		icon.add("f02");
		//
		//		title.add("3.xxxxx");
		//		url.add("http://www.gamecity.ne.jp/nol/");
		//		icon.add("f03");
		//
		//		title.add("4.xxxxx");
		//		url.add("http://www.gamecity.ne.jp/nol/");
		//		icon.add("f04");
		//
		//		title.add("5.xxxxx");
		//		url.add("http://www.gamecity.ne.jp/nol/");
		//		icon.add("f05");
		//
		//		title.add("6.xxxxx");
		//		url.add("http://www.gamecity.ne.jp/nol/");
		//		icon.add("f06");

		ListView updateListView = (ListView) findViewById(R.id.updateListView);

//		ArrayAdapter<String> adapter =
//				new ArrayAdapter<String>(this, R.layout.simple_list_item_layout, title);

		updateListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Log.v(TAG, "position=" + position);

				Uri uri = Uri.parse(url.get(position));
				Intent browser = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(browser);
			}
		});

		//		updateListView.setAdapter(adapter);
		updateListView.setAdapter(new NolAdapter(title));

		// NotificationがクリックされActivityが呼び出された時に
		// Notificationを非表示にする処理
		NotificationManager mNotificationManager = (NotificationManager) this
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(GcmIntentService.NOTIFICATION_ID);
	}

	private class NolAdapter extends ArrayAdapter<String> {

		public NolAdapter(ArrayList<String> titles) {
			super(DialogActivity.this,
					R.layout.list_item_layout,
					R.id.titleView,
					titles);
//			Log.v(TAG, "NolAdapter");
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = super.getView(position, convertView, parent);

			ImageView img = (ImageView) row.findViewById(R.id.iconView);

			if (icon.get(position).equals("f01")) {
				img.setImageResource(R.drawable.f01);
			} else if (icon.get(position).equals("f02")) {
				img.setImageResource(R.drawable.f02);
			} else if (icon.get(position).equals("f03")) {
				img.setImageResource(R.drawable.f03);
			} else if (icon.get(position).equals("f04")) {
				img.setImageResource(R.drawable.f04);
			} else if (icon.get(position).equals("f05")) {
				img.setImageResource(R.drawable.f05);
			} else if (icon.get(position).equals("f06")) {
				img.setImageResource(R.drawable.f06);
			}

//			Log.v(TAG, "getView");
			return row;
		}

	}

}
