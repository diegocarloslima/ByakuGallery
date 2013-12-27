package com.diegocarloslima.byakugallery.sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class MainActivity extends ListActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setListAdapter(new SimpleAdapter(this, createData(), android.R.layout.simple_list_item_2, new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2}));
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Class<?> intentClass = null;
		
		switch(position) {
		case 0:
			intentClass = TouchImageViewSampleActivity.class;
			break;
		case 1:
			intentClass = GalleryViewPagerSampleActivity.class;
			break;
		}
		
		startActivity(new Intent(this, intentClass));
	}
	
	private List<Map<String, String>> createData() {
		List<Map<String, String>> data = new ArrayList<Map<String,String>>();
		
		data.add(createItem("TouchImageView Sample", "This sample uses TouchImageView and TileBitmapDrawable to show a large image."));
		data.add(createItem("GalleryViewPager Sample", "This sample uses GalleryViewPager, TouchImageView and TileBitmapDrawable to show a gallery of images."));
		
		return data;
	}
	
	private Map<String, String> createItem(String title, String subtitle) {
		Map<String, String> item = new HashMap<String, String>();
		
		item.put("title", title);
		item.put("subtitle", subtitle);
		
		return item;
	}
}
