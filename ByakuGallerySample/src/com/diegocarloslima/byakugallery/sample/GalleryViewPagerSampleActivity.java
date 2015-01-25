package com.diegocarloslima.byakugallery.sample;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.diegocarloslima.byakugallery.R;
import com.diegocarloslima.byakugallery.lib.GalleryViewPager;
import com.diegocarloslima.byakugallery.lib.TileBitmapDrawable;
import com.diegocarloslima.byakugallery.lib.TouchImageView;

import java.io.InputStream;

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
		
		private int[] images = {R.raw.photo1, R.raw.photo2, R.raw.photo3, R.raw.android1, R.raw.android2, R.raw.android3};
		
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
	}
	
	public static final class GalleryFragment extends Fragment {
		
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
			
			TileBitmapDrawable.attachTileBitmapDrawable(image, is, null, new TileBitmapDrawable.OnInitializeListener() {
				
				@Override
				public void onStartInitialization() {
					progress.setVisibility(View.VISIBLE);
				}
				
				@Override
				public void onEndInitialization() {
					progress.setVisibility(View.GONE);
				}

                @Override
                public void onError(Exception ex) {

                }
            });
			
			return v;
		}
		
	}
}
