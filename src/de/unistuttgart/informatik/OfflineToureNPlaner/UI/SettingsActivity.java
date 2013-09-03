package de.unistuttgart.informatik.OfflineToureNPlaner.UI;

import de.unistuttgart.informatik.OfflineToureNPlaner.R;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.preference.Preference;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private static final boolean DEBUG = false;

	private EditTextPreference pref_mapfile;
	private EditTextPreference pref_graphfile;
	private EditTextPreference pref_osmfinddirectory;

	static String getPreferenceString(Preference pref, String defaultValue) {
		if (DEBUG) android.util.Log.d("getPreferenceString", pref.getKey() + " =>  " + pref.getSharedPreferences().getString(pref.getKey(), defaultValue));
		return pref.getSharedPreferences().getString(pref.getKey(), defaultValue);
	}

	static void setPreferenceString(Preference pref, String value) {
		if (DEBUG) android.util.Log.d("setPreferenceString", pref.getKey() + " =>  " + value);
		pref.getSharedPreferences().edit().putString(pref.getKey(), value).apply();
	}

	private class TryIntent implements Preference.OnPreferenceClickListener {
		private final int activityResultID;
		private final String intentName;
		private final String title;
		
		public TryIntent(int activityResultID, String intentName, String title) {
			this.activityResultID = activityResultID;
			this.intentName = intentName;
			this.title = title;
		}

		@Override
		public boolean onPreferenceClick(Preference pref) {
			Intent intent = new Intent(intentName);
			final String value = getPreferenceString(pref, Environment.getExternalStorageDirectory().getAbsolutePath());
			intent.setData(Uri.parse("file://" + value));
			intent.putExtra("org.openintents.extra.TITLE", title);
			if (null != intent.resolveActivity(getPackageManager())) {
				try {
					startActivityForResult(intent, activityResultID);
					return true;
				} catch (ActivityNotFoundException e) {
					Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				}
			}
			return false;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Resources r = getResources();
		addPreferencesFromResource(R.xml.preferences);

		pref_mapfile = (EditTextPreference) findPreference("mapfile");
		pref_graphfile = (EditTextPreference) findPreference("graphfile");
		pref_osmfinddirectory = (EditTextPreference) findPreference("osmfinddirectory");

		pref_mapfile.setOnPreferenceClickListener(new TryIntent(1, "org.openintents.action.PICK_FILE", r.getString(R.string.select_file)));
		pref_graphfile.setOnPreferenceClickListener(new TryIntent(2, "org.openintents.action.PICK_FILE", r.getString(R.string.select_file)));
		pref_osmfinddirectory.setOnPreferenceClickListener(new TryIntent(3, "org.openintents.action.PICK_DIRECTORY", r.getString(R.string.select_directory)));

		updateSummaries();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case 1:
			pref_mapfile.getDialog().cancel();
			if (resultCode == RESULT_OK) setPreferenceString(pref_mapfile, data.getData().getPath());
			break;
		case 2:
			pref_graphfile.getDialog().cancel();
			if (resultCode == RESULT_OK) setPreferenceString(pref_graphfile, data.getData().getPath());
			break;
		case 3:
			pref_osmfinddirectory.getDialog().cancel();
			if (resultCode == RESULT_OK) setPreferenceString(pref_osmfinddirectory, data.getData().getPath());
			break;
		}
	}

	private void updateSummaries() {
		if (DEBUG) android.util.Log.d("Preferences", "update summaries");
		final Resources r = getResources();
		pref_mapfile.setSummary(getPreferenceString(pref_mapfile, r.getString(R.string.pref_notset)));
		pref_graphfile.setSummary(getPreferenceString(pref_graphfile, r.getString(R.string.pref_notset)));
		pref_osmfinddirectory.setSummary(getPreferenceString(pref_osmfinddirectory, r.getString(R.string.pref_notset)));
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updateSummaries();
	}

	@Override
	public void onResume() {
		super.onResume();
		updateSummaries();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_settings, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_about:
			Intent about = new Intent(this, AboutActivity.class);
			startActivity(about);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
