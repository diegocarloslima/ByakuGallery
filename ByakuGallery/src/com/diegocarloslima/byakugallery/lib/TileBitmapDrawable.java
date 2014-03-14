package com.diegocarloslima.byakugallery.lib;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;

public class TileBitmapDrawable extends Drawable {

	// The hardware must support textures at least 2048x2048 pixels
	// http://stackoverflow.com/questions/7428996/hw-accelerated-activity-how-to-get-opengl-texture-size-limit
	private static final int BITMAP_TEXTURE_SIZE = 2048;

	private static final int TILE_SIZE_DENSITY_HIGH = 256;
	private static final int TILE_SIZE_DEFAULT = 128;

	// A shared cache is used between instances to minimize OutOfMemoryError
	private static BitmapLruCache sBitmapCache;
	private static final Object sBitmapCacheLock = new Object();

	// Instance ids are used to identify a cache hit for a specific instance of TileBitmapDrawable on the shared BitmapLruCache
	private static final AtomicInteger sInstanceIds = new AtomicInteger(1);
	private final int mInstanceId = sInstanceIds.getAndIncrement();

	// The reference of the parent ImageView is needed in order to get the Matrix values and determine the visible area
	private final WeakReference<ImageView> mParentView;

	private final BitmapRegionDecoder mRegionDecoder;
	private final BlockingQueue<Tile> mDecodeQueue = new LinkedBlockingQueue<Tile>();
	private final DecoderWorker mDecoderWorker;

	private final int mIntrinsicWidth;
	private final int mIntrinsicHeight;
	private final int mTileSize;

	private final Bitmap mScreenNail;
	private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

	private Matrix mMatrix;
	private final float[] mMatrixValues = new float[9];
	private float[] mLastMatrixValues = new float[9];

	private final Rect mTileRect = new Rect();
	private final Rect mVisibleAreaRect = new Rect();
	private final Rect mScreenNailRect = new Rect();
	
	public static AsyncTask<Object, ?, TileBitmapDrawable> createTask(ImageView imageView, Drawable placeHolder, OnInitializeListener listener) {
		return new InitializationTask(imageView, placeHolder, listener);
	}

	public static void attachTileBitmapDrawable(ImageView imageView, String path, Drawable placeHolder, OnInitializeListener listener) {
		new InitializationTask(imageView, placeHolder, listener).execute(path);
	}

	public static void attachTileBitmapDrawable(ImageView imageView, FileDescriptor fd, Drawable placeHolder, OnInitializeListener listener) {
		new InitializationTask(imageView, placeHolder, listener).execute(fd);
	}

	public static void attachTileBitmapDrawable(ImageView imageView, InputStream is, Drawable placeHolder, OnInitializeListener listener) {
		new InitializationTask(imageView, placeHolder, listener).execute(is);
	}

	private TileBitmapDrawable(ImageView parentView, BitmapRegionDecoder decoder, Bitmap screenNail) {
		mParentView = new WeakReference<ImageView>(parentView);

		synchronized(decoder) {
			mRegionDecoder = decoder;
			mIntrinsicWidth = mRegionDecoder.getWidth();
			mIntrinsicHeight = mRegionDecoder.getHeight();
		}

		final DisplayMetrics metrics = new DisplayMetrics();
		getDisplayMetrics(parentView.getContext(), metrics);

		mTileSize = metrics.densityDpi >= DisplayMetrics.DENSITY_HIGH ? TILE_SIZE_DENSITY_HIGH : TILE_SIZE_DEFAULT;

		mScreenNail = screenNail;

		// The Tile can be reduced up to half of its size until the next level of tiles is displayed
		// It can also be displayed just a portion of the tile on each size, so we need to add 1
		final int maxHorizontalTiles = (int) Math.ceil(2 * metrics.widthPixels / (float) mTileSize) + 1;
		final int maxVerticalTiles = (int) Math.ceil(2 * metrics.heightPixels / (float) mTileSize) + 1;

		// The shared cache will have the minimum required size to display all visible tiles
		// Here, we multiply by 4 because in ARGB_8888 config, each pixel is stored on 4 bytes
		final int cacheSize = 4 * maxHorizontalTiles * maxVerticalTiles * mTileSize * mTileSize;

		synchronized(sBitmapCacheLock) {
			if(sBitmapCache == null) {
				sBitmapCache = new BitmapLruCache(cacheSize);
			}
		}

		mDecoderWorker = new DecoderWorker(this, mRegionDecoder, mDecodeQueue);
		mDecoderWorker.start();
	}

	@Override
	public void setAlpha(int alpha) {
		final int oldAlpha = mPaint.getAlpha();
		if(alpha != oldAlpha) {
			mPaint.setAlpha(alpha);
			invalidateSelf();
		}
	}

	@Override
	public int getAlpha() {
		return mPaint.getAlpha();
	}

	@Override
	public int getOpacity() {
		if (mScreenNail == null || mScreenNail.hasAlpha() || mPaint.getAlpha() < 255) {
			return PixelFormat.TRANSLUCENT;
		}
		return PixelFormat.OPAQUE;
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
		mPaint.setColorFilter(cf);
		invalidateSelf();
	}

	@Override
	public int getIntrinsicWidth() {
		return mIntrinsicWidth;
	}

	@Override
	public int getIntrinsicHeight() {
		return mIntrinsicHeight;
	}

	@Override
	public void draw(Canvas canvas) {
		final ImageView parentView = mParentView.get();
		if(parentView == null) {
			return;
		}

		final int parentViewWidth = parentView.getWidth();
		final int parentViewHeight = parentView.getHeight();
		mMatrix = parentView.getImageMatrix();

		mMatrix.getValues(mMatrixValues);
		final float translationX = mMatrixValues[Matrix.MTRANS_X];
		final float translationY = mMatrixValues[Matrix.MTRANS_Y];
		final float scale = mMatrixValues[Matrix.MSCALE_X];

		// If the matrix values have changed, the decode queue must be cleared in order to avoid decoding unused tiles
		if(translationX != mLastMatrixValues[Matrix.MTRANS_X] || translationY != mLastMatrixValues[Matrix.MTRANS_Y] || scale != mLastMatrixValues[Matrix.MSCALE_X]) {
			mDecodeQueue.clear();
		}

		mLastMatrixValues = Arrays.copyOf(mMatrixValues, mMatrixValues.length);

		// The scale required to display the whole Bitmap inside the ImageView. It will be the minimum allowed scale value
		final float minScale = Math.min(parentViewWidth / (float) mIntrinsicWidth, parentViewHeight / (float) mIntrinsicHeight);

		// The number of allowed levels for this Bitmap. Each subsequent level is half size of the previous one
		final int levelCount = Math.max(1, MathUtils.ceilLog2(mIntrinsicWidth / (mIntrinsicWidth * minScale)));

		// sampleSize = 2 ^ currentLevel
		final int currentLevel = MathUtils.clamp(MathUtils.floorLog2(1 / scale), 0, levelCount - 1);
		final int sampleSize = 1 << currentLevel;

		final int currentTileSize = mTileSize * sampleSize;
		final int horizontalTiles = (int) Math.ceil(mIntrinsicWidth / (float) currentTileSize);
		final int verticalTiles = (int) Math.ceil(mIntrinsicHeight / (float) currentTileSize);

		final int visibleAreaLeft = Math.max(0, (int) (-translationX / scale));
		final int visibleAreaTop = Math.max(0, (int) (-translationY / scale));
		final int visibleAreaRight = Math.min(mIntrinsicWidth, Math.round((-translationX + parentViewWidth) / scale));
		final int visibleAreaBottom = Math.min(mIntrinsicHeight, Math.round((-translationY + parentViewHeight) / scale));
		mVisibleAreaRect.set(visibleAreaLeft, visibleAreaTop, visibleAreaRight, visibleAreaBottom);

		boolean cacheMiss = false;
		
		for(int i = 0; i < horizontalTiles; i++) {
			for(int j = 0; j < verticalTiles; j++) {

				final int tileLeft = i * currentTileSize;
				final int tileTop = j * currentTileSize;
				final int tileRight = (i + 1) * currentTileSize <= mIntrinsicWidth ? (i + 1) * currentTileSize : mIntrinsicWidth;
				final int tileBottom = (j + 1) * currentTileSize <= mIntrinsicHeight ? (j + 1) * currentTileSize : mIntrinsicHeight;
				mTileRect.set(tileLeft, tileTop, tileRight, tileBottom);

				if(Rect.intersects(mVisibleAreaRect, mTileRect)) {

					final Tile tile = new Tile(mInstanceId, mTileRect, i, j, currentLevel);

					Bitmap cached = null;
					synchronized(sBitmapCacheLock) {
						cached = sBitmapCache.get(tile.getKey());
					}

					if(cached != null) {
						canvas.drawBitmap(cached, null, mTileRect, mPaint);
					} else {
						cacheMiss = true;

						synchronized (mDecodeQueue) {
							if(!mDecodeQueue.contains(tile)) {
								mDecodeQueue.add(tile);
							}
						}

						// The screenNail is used while the proper tile is being decoded
						final int screenNailLeft = Math.round(tileLeft * mScreenNail.getWidth() / (float) mIntrinsicWidth);
						final int screenNailTop = Math.round(tileTop * mScreenNail.getHeight() / (float) mIntrinsicHeight);
						final int screenNailRight = Math.round(tileRight * mScreenNail.getWidth() / (float) mIntrinsicWidth);
						final int screenNailBottom = Math.round(tileBottom * mScreenNail.getHeight() / (float) mIntrinsicHeight);
						mScreenNailRect.set(screenNailLeft, screenNailTop, screenNailRight, screenNailBottom);

						canvas.drawBitmap(mScreenNail, mScreenNailRect, mTileRect, mPaint);
					}
				}
			}
		}

		// If we had a cache miss, we will need to redraw until all needed tiles have been decoded by our worker thread
		if(cacheMiss) {
			invalidateSelf();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		mDecoderWorker.quit();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private static void getDisplayMetrics(Context context, DisplayMetrics outMetrics) {
		final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		final Display display = wm.getDefaultDisplay();

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			display.getRealMetrics(outMetrics);
		} else {
			display.getMetrics(outMetrics);
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				try {
					outMetrics.widthPixels = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
					outMetrics.heightPixels = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
					return;
				} catch (Exception e) {}
			}
		}
	}

	public interface OnInitializeListener {
		public void onStartInitialization();

		public void onEndInitialization();
	}

	private static final class Tile {

		private final int mInstanceId;
		private final Rect mTileRect;
		private final int mHorizontalPos;
		private final int mVerticalPos;
		private final int mLevel;

		private Tile(int instanceId, Rect tileRect, int horizontalPos, int verticalPos, int level) {
			mInstanceId = instanceId;
			mTileRect = new Rect();
			mTileRect.set(tileRect);
			mHorizontalPos = horizontalPos;
			mVerticalPos = verticalPos;
			mLevel = level;
		}

		public String getKey() {
			return "#" + mInstanceId + "#" + mHorizontalPos + "#" + mVerticalPos + "#" + mLevel;
		}

		@Override
		public int hashCode() {
			return getKey().hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) {
				return true;
			}
			if(o instanceof TileBitmapDrawable) {
				return getKey().equals(((Tile) o).getKey());
			}
			return false;
		}
	}

	private static final class BitmapLruCache extends LruCache<String, Bitmap> {

		private BitmapLruCache(int maxSize) {
			super(maxSize);
		}

		@Override
		protected int sizeOf(String key, Bitmap value) {
			return getBitmapSize(value);
		}

		@TargetApi(Build.VERSION_CODES.KITKAT)
		private static int getBitmapSize(Bitmap bitmap) {
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				return bitmap.getAllocationByteCount();
			}
			else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
				return bitmap.getByteCount();
			}
			return bitmap.getRowBytes() * bitmap.getHeight();
		}
	}

	private static final class InitializationTask extends AsyncTask<Object, Void, TileBitmapDrawable> {

		private final ImageView mImageView;
		private final OnInitializeListener mListener;

		private InitializationTask(ImageView imageView, Drawable placeHolder, OnInitializeListener listener) {
			mImageView = imageView;
			mListener = listener;

			if(mListener != null) {
				mListener.onStartInitialization();
			}
			if(placeHolder != null) {
				mImageView.setImageDrawable(placeHolder);
			}
		}

		@Override
		protected TileBitmapDrawable doInBackground(Object... params) {
			BitmapRegionDecoder decoder = null;

			try {
				if(params[0] instanceof String) {
					decoder = BitmapRegionDecoder.newInstance((String) params[0], false);
				} else if(params[0] instanceof FileDescriptor) {
					decoder = BitmapRegionDecoder.newInstance((FileDescriptor) params[0], false);
				} else {
					decoder = BitmapRegionDecoder.newInstance((InputStream) params[0], false);
				} 
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			final DisplayMetrics metrics = new DisplayMetrics();
			final WindowManager wm = (WindowManager) mImageView.getContext().getSystemService(Context.WINDOW_SERVICE);
			wm.getDefaultDisplay().getMetrics(metrics);

			final int maxSize = Math.min(BITMAP_TEXTURE_SIZE, Math.max(metrics.widthPixels, metrics.heightPixels));
			final float minScale = Math.min(maxSize / (float) decoder.getWidth(), maxSize / (float) decoder.getHeight());
			final int levelCount = Math.max(1, MathUtils.ceilLog2(decoder.getWidth() / (decoder.getWidth() * minScale)));

			final Rect screenNailRect = new Rect(0, 0, decoder.getWidth(), decoder.getHeight());

			final BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Config.ARGB_8888;
			options.inPreferQualityOverSpeed = true;
			options.inSampleSize = (1 << (levelCount - 1));

			Bitmap screenNail = null;
			boolean decodeSuccess = false;
			while (decodeSuccess == false) {
				try {
					screenNail = decoder.decodeRegion(screenNailRect, options);
					if (screenNail != null && screenNail.getWidth() > 0 && screenNail.getHeight() > 0) {
						screenNail = Bitmap.createScaledBitmap(screenNail, Math.round(decoder.getWidth() * minScale),
						        Math.round(decoder.getHeight() * minScale), true);
					}
					decodeSuccess = true;
				} catch (OutOfMemoryError e) {
					options.inSampleSize *= 2;
				}
			}

			TileBitmapDrawable drawable = new TileBitmapDrawable(mImageView, decoder, screenNail);

			return drawable;
		}
		
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@Override
		protected void onCancelled(TileBitmapDrawable result) {
			if (result != null && result.mDecoderWorker != null) {
				result.mDecoderWorker.quit();
			}
			
			super.onCancelled(result);
		}

		@Override
		protected void onPostExecute(TileBitmapDrawable result) {
			if(mListener != null) {
				mListener.onEndInitialization();
			}
			mImageView.setImageDrawable(result);
		}
	}

	private static final class DecoderWorker extends Thread {

		private final WeakReference<TileBitmapDrawable> mDrawableReference;
		private final BitmapRegionDecoder mDecoder;
		private final BlockingQueue<Tile> mDecodeQueue;

		private boolean mQuit;

		private DecoderWorker(TileBitmapDrawable drawable, BitmapRegionDecoder decoder, BlockingQueue<Tile> decodeQueue) {
			mDrawableReference = new WeakReference<TileBitmapDrawable>(drawable);
			mDecoder = decoder;
			mDecodeQueue = decodeQueue;
		}

		@Override
		public void run() {
			while(true) {
				if(mDrawableReference.get() == null) {
					return;
				}

				Tile tile;
				try {
					tile = mDecodeQueue.take();
				} catch (InterruptedException e) {
					if(mQuit) {
						return;
					}
					continue;
				}

				synchronized(sBitmapCacheLock) {
					if(sBitmapCache.get(tile.getKey()) != null) {
						continue;
					}
				}

				final BitmapFactory.Options options = new BitmapFactory.Options();
				options.inPreferredConfig = Config.ARGB_8888;
				options.inPreferQualityOverSpeed = true;
				options.inSampleSize =  (1 << tile.mLevel);

				Bitmap bitmap = null;
				boolean decodeSuccess = false;
				while (decodeSuccess == false) {
					synchronized (mDecoder) {
						try {
							bitmap = mDecoder.decodeRegion(tile.mTileRect, options);
							decodeSuccess = true;
						} catch (OutOfMemoryError e) {
							options.inSampleSize *= 2;
						}
					}
				}
				
				if (bitmap == null) {
					return;
				}
				
				if(bitmap == null) {
					continue;
				}
				
				synchronized(sBitmapCacheLock) {
					sBitmapCache.put(tile.getKey(), bitmap);
				}
			}
		}

		public void quit() {
			mQuit = true;
			interrupt();
		}
	}
}
