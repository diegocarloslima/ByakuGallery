package com.diegocarloslima.byakugallery.lib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;

public class GalleryViewPager extends ViewPager {
	
	
	public GalleryViewPager(Context context) {
		this(context, null);
	}

	public GalleryViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	@SuppressLint("NewApi")
	@Override
	protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
		if (v instanceof TouchImageView) {
			return ((TouchImageView) v).canScrollHorizontally(dx);
		} else {
			return super.canScroll(v, checkV, dx, x, y);
		}
	}
}
