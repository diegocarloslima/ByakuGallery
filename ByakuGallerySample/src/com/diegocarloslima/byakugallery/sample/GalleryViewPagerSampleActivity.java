package com.diegocarloslima.byakugallery.sample;

import java.io.InputStream;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.diegocarloslima.byakugallery.lib.GalleryViewPager;
import com.diegocarloslima.byakugallery.lib.TileBitmapDrawable;
import com.diegocarloslima.byakugallery.lib.TouchImageView;

public class GalleryViewPagerSampleActivity extends FragmentActivity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.gallery_view_pager_sample);
		
		final GalleryViewPager gallery = (GalleryViewPager) findViewById(R.id.gallery_view_pager_sample_gallery);
		gallery.setAdapter(new GalleryAdapter());
		gallery.setOffscreenPageLimit(1);
	}
	
	private final class GalleryAdapter extends FragmentStatePagerAdapter {
		
		private int[] images = {R.raw.queen, R.raw.photo1, R.raw.photo2, R.raw.photo3, R.raw.android1, R.raw.android2, R.raw.android3};
		
		GalleryAdapter() {
			super(getSupportFragmentManager());
		}
		
		@Override
		public int getCount() {
			return this.images.length;
		}
		
		@Override
		public Fragment getItem(int position) {
			return GalleryFragment.getInstance(this.images[position]);
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			super.destroyItem(container, position, object);
			
			if (object instanceof GalleryFragment) {
				GalleryFragment fragment = (GalleryFragment) object;
				fragment.cancelTask();
			}
		}
		
	}
	
	public static final class GalleryFragment extends Fragment {
		
		private AsyncTask<Object, ?, TileBitmapDrawable> task;
		
		public static GalleryFragment getInstance(int imageId) {
			final GalleryFragment instance = new GalleryFragment();
			final Bundle params = new Bundle();
			params.putInt("imageId", imageId);
			instance.setArguments(params);
			
			return instance;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			final View v = inflater.inflate(R.layout.gallery_view_pager_sample_item, null);
			
			final TouchImageView image = (TouchImageView) v.findViewById(R.id.gallery_view_pager_sample_item_image);
			final int imageId = getArguments().getInt("imageId");
			final InputStream is = getResources().openRawResource(imageId);
			
			final ProgressBar progress = (ProgressBar) v.findViewById(R.id.gallery_view_pager_sample_item_progress);
			
			task = TileBitmapDrawable.createTask(image, null, new TileBitmapDrawable.OnInitializeListener() {
				@Override
				public void onStartInitialization() {
					progress.setVisibility(View.VISIBLE);
				}
				
				@Override
				public void onEndInitialization() {
					progress.setVisibility(View.GONE);
				}
			});
			task.execute(is);
			
			return v;
		}
		
		public void cancelTask() {
			if (task != null) {
				task.cancel(true);
			}
		}
	}
}
