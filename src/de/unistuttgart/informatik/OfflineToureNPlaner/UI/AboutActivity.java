package de.unistuttgart.informatik.OfflineToureNPlaner.UI;

import de.unistuttgart.informatik.OfflineToureNPlaner.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class AboutActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_about, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			Intent settings = new Intent(this, SettingsActivity.class);
			startActivity(settings);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
