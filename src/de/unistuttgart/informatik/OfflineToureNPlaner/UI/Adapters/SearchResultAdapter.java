package de.unistuttgart.informatik.OfflineToureNPlaner.UI.Adapters;

import java.util.List;

import de.unistuttgart.informatik.OfflineToureNPlaner.R;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.OsmFindResult;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

public class SearchResultAdapter extends BaseAdapter implements ListAdapter {
	final Context context;
	List<OsmFindResult> objects;
	final LayoutInflater inflater;
	final Object lock = new Object();

	public SearchResultAdapter(Context context, List<OsmFindResult> objects) {
		this.context = context;
		this.objects = objects;
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	public SearchResultAdapter(Context context) {
		this.context = context;
		this.objects = null;
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	public void setData(List<OsmFindResult> objects) {
		synchronized (lock) {
			this.objects = objects;
			notifyDataSetChanged();
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		synchronized (lock) {
			View view;
			if (convertView == null) {
				view = inflater.inflate(R.layout.search_result_item, parent, false);
			} else {
				view = convertView;
			}

			TextView text = (TextView) view.findViewById(R.id.label);
			text.setText(objects.get(position).getLabel());
			return view;
		}
	}

	@Override
	public int getCount() {
		synchronized (lock) {
			return objects != null ? objects.size() : 0;
		}
	}

	@Override
	public Object getItem(int position) {
		synchronized (lock) {
			return objects.get(position);
		}
	}

	@Override
	public long getItemId(int position) {
		synchronized (lock) {
			return position;
		}
	}

	@Override
	public boolean isEmpty() {
		synchronized (lock) {
			return objects == null || objects.isEmpty();
		}
	}
}
