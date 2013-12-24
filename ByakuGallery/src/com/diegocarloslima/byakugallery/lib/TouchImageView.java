package com.diegocarloslima.byakugallery.lib;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import android.widget.ImageView;

public class TouchImageView extends ImageView {

	private static final int DOUBLE_TAP_ANIMATION_DURATION = 300;
	private static final int SCALE_END_ANIMATION_DURATION = 200;

	private Drawable mDrawable;
	private int mDrawableIntrinsicWidth;
	private int mDrawableIntrinsicHeight;

	private final TouchGestureDetector mTouchGestureDetector;

	private final Matrix mMatrix = new Matrix();
	private final float[] mMatrixValues = new float[9];

	private float mScale;
	private float mTranslationX;
	private float mTranslationY;

	private Float mLastFocusX;
	private Float mLastFocusY;

	private final FlingScroller mFlingScroller = new FlingScroller();
	private boolean mIsAnimatingBack;

	public TouchImageView(Context context) {
		this(context, null);
	}

	public TouchImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TouchImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		final TouchGestureDetector.OnTouchGestureListener listener = new TouchGestureDetector.OnTouchGestureListener() {

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				return performClick();
			}

			@Override
			public void onLongPress(MotionEvent e) {
				performLongClick();
			}

			@Override
			public boolean onDoubleTap(MotionEvent e) {
				loadMatrixValues();

				final float minScale = getMinScale();
				// If we have already zoomed in, we should return to our initial scale value (minScale). Otherwise, scale to full size
				final float targetScale = mScale > minScale ? minScale : 1;

				// First, we try to keep the focused point in the same position when the animation ends
				final float desiredTranslationX = e.getX() - (e.getX() - mTranslationX) * (targetScale / mScale);
				final float desiredTranslationY = e.getY() - (e.getY() - mTranslationY) * (targetScale / mScale);

				// Here, we apply a correction to avoid unwanted blank spaces
				final float targetTranslationX = desiredTranslationX + computeTranslation(getMeasuredWidth(), mDrawableIntrinsicWidth * targetScale, desiredTranslationX, 0);
				final float targetTranslationY = desiredTranslationY + computeTranslation(getMeasuredHeight(), mDrawableIntrinsicHeight * targetScale, desiredTranslationY, 0);

				clearAnimation();
				final Animation animation = new TouchAnimation(targetScale, targetTranslationX, targetTranslationY);
				animation.setDuration(DOUBLE_TAP_ANIMATION_DURATION);
				startAnimation(animation);

				return true;
			}

			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
				// Sometimes, this method is called just after an onScaleEnd event. In this case, we want to wait until we animate back our image
				if(mIsAnimatingBack) {
					return false;
				}
				loadMatrixValues();

				final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				final float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				final float dx = computeTranslation(getMeasuredWidth(), currentDrawableWidth, mTranslationX, -distanceX);
				final float dy = computeTranslation(getMeasuredHeight(), currentDrawableHeight, mTranslationY, -distanceY);

				if(Math.abs(dx) < 1 && Math.abs(dy) < 1) {
					return false;
				}
				mMatrix.postTranslate(dx, dy);

				clearAnimation();
				ViewCompat.postInvalidateOnAnimation(TouchImageView.this);

				return true;
			}

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				// Sometimes, this method is called just after an onScaleEnd event. In this case, we want to wait until we animate back our image
				if(mIsAnimatingBack) {
					return false;
				}
				loadMatrixValues();

				final float horizontalFreeSpace = (getMeasuredWidth() - mDrawableIntrinsicWidth * mScale) / 2F;
				final float minX = horizontalFreeSpace > 0 ? horizontalFreeSpace : getMeasuredWidth() - mDrawableIntrinsicWidth * mScale;
				final float maxX = horizontalFreeSpace > 0 ? horizontalFreeSpace : 0;

				final float verticalFreeSpace = (getMeasuredHeight() - mDrawableIntrinsicHeight * mScale) / 2F;
				final float minY = verticalFreeSpace > 0 ? verticalFreeSpace : getMeasuredHeight() - mDrawableIntrinsicHeight * mScale;
				final float maxY = verticalFreeSpace > 0 ? verticalFreeSpace : 0;

				// Using AOSP FlingScroller here. The results were better than the Scroller class 
				mFlingScroller.fling(Math.round(mTranslationX), Math.round(mTranslationY), Math.round(velocityX), Math.round(velocityY), Math.round(minX), Math.round(maxX), Math.round(minY), Math.round(maxY));

				final float dx = mFlingScroller.getFinalX() - mTranslationX;
				final float dy = mFlingScroller.getFinalY() - mTranslationY;

				if(Math.abs(dx) < 1 && Math.abs(dy) < 1) {
					return false;
				}

				clearAnimation();
				final Animation animation = new FlingAnimation();
				animation.setDuration(mFlingScroller.getDuration());
				animation.setInterpolator(new LinearInterpolator());
				startAnimation(animation);

				return true;
			}

			@Override
			public boolean onScaleBegin(ScaleGestureDetector detector) {
				mLastFocusX = null;
				mLastFocusY = null;

				return true;
			}

			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				return option3(detector);
			}

			private boolean option3(ScaleGestureDetector detector) {
				loadMatrixValues();

				float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				final float focusX = computeFocus(getMeasuredWidth(), currentDrawableWidth, mTranslationX, detector.getFocusX());
				final float focusY = computeFocus(getMeasuredHeight(), currentDrawableHeight, mTranslationY, detector.getFocusY());

				final float minTranslationX = getMeasuredWidth() > currentDrawableWidth ? 0 : getMeasuredWidth() - currentDrawableWidth;
				final float maxTranslationX = getMeasuredWidth() > currentDrawableWidth ? getMeasuredWidth() - currentDrawableWidth: 0;

				final float minTranslationY = getMeasuredHeight() > currentDrawableHeight ? 0 : getMeasuredHeight() - currentDrawableHeight;
				final float maxTranslationY = getMeasuredHeight() > currentDrawableHeight ? getMeasuredHeight() - currentDrawableHeight: 0;

				float dx = 0;
				float dy = 0;

				if(mLastFocusX != null) {
					dx = focusX - mLastFocusX;
					if(mTranslationX < minTranslationX && dx > 0) {
						if(mTranslationX + dx > maxTranslationX) {
							dx = maxTranslationX - mTranslationX;
						}
					} else if(mTranslationX > maxTranslationX && dx < 0) {
						if(mTranslationX + dx < minTranslationX) {
							dx = minTranslationX - mTranslationX;
						}
					} else if(mTranslationX > minTranslationX && mTranslationX < maxTranslationX) {
						if(mTranslationX + dx < minTranslationX) {
							dx = minTranslationX - mTranslationX;
						} else if(mTranslationX + dx > maxTranslationX) {
							dx = maxTranslationX - mTranslationX;
						}
					} else {
						dx = 0;
					}
				}


				if(mLastFocusY != null) {
					dy = focusY - mLastFocusY;
					if(mTranslationY < minTranslationY && dy > 0) {
						if(mTranslationY + dy > maxTranslationY) {
							dy = maxTranslationY - mTranslationY;
						}
					} else if(mTranslationY > maxTranslationY && dy < 0) {
						if(mTranslationY + dy < minTranslationY) {
							dy = minTranslationY - mTranslationY;
						}
					} else if(mTranslationY > minTranslationY && mTranslationY < maxTranslationY) {
						if(mTranslationY + dy < minTranslationY) {
							dy = minTranslationY - mTranslationY;
						} else if(mTranslationY + dy > maxTranslationY) {
							dy = maxTranslationY - mTranslationY;
						}
					} else {
						dy = 0;
					}
				}

				if(dx != 0 || dy != 0) {
					mMatrix.postTranslate(dx, dy);
				}


				final float scale = computeScale(getMinScale(), mScale, detector.getScaleFactor());
				mMatrix.postScale(scale, scale, focusX, focusY);

				clearAnimation();
				ViewCompat.postInvalidateOnAnimation(TouchImageView.this);

				mLastFocusX = focusX;
				mLastFocusY = focusY;

				return true;
			}

			private boolean option2(ScaleGestureDetector detector) {
				loadMatrixValues();

				float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				final float focusX = computeFocus(getMeasuredWidth(), currentDrawableWidth, mTranslationX, detector.getFocusX());
				final float focusY = computeFocus(getMeasuredHeight(), currentDrawableHeight, mTranslationY, detector.getFocusY());

				final boolean correctX = mTranslationX <= 0 && mTranslationX + currentDrawableWidth >= getMeasuredWidth();
				final boolean correctY = mTranslationY <= 0 && mTranslationY + currentDrawableHeight >= getMeasuredHeight();

				if(mLastFocusX != null && mLastFocusY != null) {
					final float dx = focusX - mLastFocusX;
					final float dy = focusY - mLastFocusY;
					mMatrix.postTranslate(dx, dy);
				}

				final float scale = computeScale(getMinScale(), mScale, detector.getScaleFactor());
				mMatrix.postScale(scale, scale, focusX, focusY);

				loadMatrixValues();

				currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				final float dx = correctX ? computeTranslation(getMeasuredWidth(), currentDrawableWidth, mTranslationX, 0) : 0;
				final float dy = correctY ? computeTranslation(getMeasuredHeight(), currentDrawableHeight, mTranslationY, 0) : 0;

				if(dx != 0 || dy != 0) {
					mMatrix.postTranslate(dx, dy);
				}

				clearAnimation();
				ViewCompat.postInvalidateOnAnimation(TouchImageView.this);

				mLastFocusX = focusX;
				mLastFocusY = focusY;

				return true;
			}

			private boolean option1(ScaleGestureDetector detector) {
				loadMatrixValues();

				final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				final float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				final float focusX = computeFocus(getMeasuredWidth(), currentDrawableWidth, mTranslationX, detector.getFocusX());
				final float focusY = computeFocus(getMeasuredHeight(), currentDrawableHeight, mTranslationY, detector.getFocusY());

				if(mLastFocusX != null && mLastFocusY != null) {
					final float dx = focusX - mLastFocusX;
					final float dy = focusY - mLastFocusY;
					mMatrix.postTranslate(dx, dy);
				}

				final float scale = computeScale(getMinScale(), mScale, detector.getScaleFactor());
				mMatrix.postScale(scale, scale, focusX, focusY);

				clearAnimation();
				ViewCompat.postInvalidateOnAnimation(TouchImageView.this);

				mLastFocusX = focusX;
				mLastFocusY = focusY;

				return true;
			}

			@Override
			public void onScaleEnd(ScaleGestureDetector detector) {
				loadMatrixValues();

				final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				final float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				final float dx = computeTranslation(getMeasuredWidth(), currentDrawableWidth, mTranslationX, 0);
				final float dy = computeTranslation(getMeasuredHeight(), currentDrawableHeight, mTranslationY, 0);

				if(Math.abs(dx) < 1 && Math.abs(dy) < 1) {
					return;
				}

				final float targetTranslationX = mTranslationX + dx;
				final float targetTranslationY = mTranslationY + dy;

				clearAnimation();
				final Animation animation = new TouchAnimation(mScale, targetTranslationX, targetTranslationY);
				animation.setDuration(SCALE_END_ANIMATION_DURATION);
				startAnimation(animation);

				mIsAnimatingBack = true;
			}
		};

		mTouchGestureDetector = new TouchGestureDetector(context, listener);

		super.setScaleType(ScaleType.MATRIX);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int oldMeasuredWidth = getMeasuredWidth();
		final int oldMeasuredHeight = getMeasuredHeight();

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if(oldMeasuredWidth != getMeasuredWidth() || oldMeasuredHeight != getMeasuredHeight()) {
			resetToInitialState();
		}
	}

	@Override
	public void setImageMatrix(Matrix matrix) {
	}

	@Override
	public Matrix getImageMatrix() {
		return mMatrix;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		mTouchGestureDetector.onTouchEvent(event);

		return true;
	}

	@Override
	public void clearAnimation() {
		super.clearAnimation();
		mIsAnimatingBack = false;
	}

	@Override
	public void setImageDrawable(Drawable drawable) {
		super.setImageDrawable(drawable);
		if(mDrawable != drawable) {
			mDrawable = drawable;
			if(drawable != null) {
				mDrawableIntrinsicWidth = drawable.getIntrinsicWidth();
				mDrawableIntrinsicHeight = drawable.getIntrinsicHeight();
				resetToInitialState();
			} else {
				mDrawableIntrinsicWidth = 0;
				mDrawableIntrinsicHeight = 0;
			}
		}
	}

	@Override
	public void setScaleType(ScaleType scaleType) {
		if(scaleType != ScaleType.MATRIX) {
			throw new IllegalArgumentException("Unsupported scaleType. Only ScaleType.MATRIX is allowed.");
		}
	}

	@Override
	public boolean canScrollHorizontally(int direction) {
		loadMatrixValues();

		if(direction > 0) {
			return Math.round(mTranslationX) < 0;
		} else if(direction < 0) {
			final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
			return Math.round(mTranslationX) > getMeasuredWidth() - Math.round(currentDrawableWidth);
		}
		return false;
	}

	private void resetToInitialState() {
		mMatrix.reset();
		final float minScale = getMinScale();
		mMatrix.postScale(minScale, minScale);

		final float[] values = new float[9];
		mMatrix.getValues(values);

		final float freeSpaceHorizontal = (getMeasuredWidth() - (mDrawableIntrinsicWidth * minScale)) / 2F;
		final float freeSpaceVertical = (getMeasuredHeight() - (mDrawableIntrinsicHeight * minScale)) / 2F;
		mMatrix.postTranslate(freeSpaceHorizontal, freeSpaceVertical);

		invalidate();
	}

	private void loadMatrixValues() {
		mMatrix.getValues(mMatrixValues);
		mScale = mMatrixValues[Matrix.MSCALE_X];
		mTranslationX = mMatrixValues[Matrix.MTRANS_X];
		mTranslationY = mMatrixValues[Matrix.MTRANS_Y];
	}

	private float getMinScale() {
		float minScale = Math.min(getMeasuredWidth() / (float) mDrawableIntrinsicWidth, getMeasuredHeight() / (float) mDrawableIntrinsicHeight);
		if(minScale > 1) {
			minScale = 1;
		}
		return minScale;
	}

	// The translation values must be in [0, viewSize - drawableSize], except if we have free space. In that case we will translate to half of the free space
	private static float computeTranslation(float viewSize, float drawableSize, float currentTranslation, float delta) {
		final float sideFreeSpace = (viewSize - drawableSize) / 2F;

		if(sideFreeSpace > 0) {
			return sideFreeSpace - currentTranslation;
		} else if(currentTranslation + delta > 0) {
			return -currentTranslation;
		} else if(currentTranslation + delta < viewSize - drawableSize) {
			return viewSize - drawableSize - currentTranslation;
		}

		return delta;
	}

	// If our focal point is outside the image, we will project it to our image bounds
	private static float computeFocus(float viewSize, float drawableSize, float currentTranslation, float focusCoordinate) {
		if(currentTranslation > 0 && focusCoordinate < currentTranslation) {
			return currentTranslation;
		} else if(currentTranslation < viewSize - drawableSize && focusCoordinate > currentTranslation + drawableSize) {
			return drawableSize + currentTranslation;
		}

		return focusCoordinate;
	}

	// The scale values must be in [minScale, 1]
	private static float computeScale(float minScale, float currentScale, float delta) {
		if(currentScale * delta < minScale) {
			return minScale / currentScale;
		} else if(currentScale * delta > 1) {
			return 1 / currentScale;
		}

		return delta;
	}

	private class FlingAnimation extends Animation {

		@Override
		protected void applyTransformation(float interpolatedTime, Transformation t) {
			mFlingScroller.computeScrollOffset(interpolatedTime);

			loadMatrixValues();

			final float dx = mFlingScroller.getCurrX() - mTranslationX;
			final float dy = mFlingScroller.getCurrY() - mTranslationY;
			mMatrix.postTranslate(dx, dy);

			ViewCompat.postInvalidateOnAnimation(TouchImageView.this);
		}
	}

	private class TouchAnimation extends Animation {

		private float initialScale;
		private float initialTranslationX;
		private float initialTranslationY;

		private float targetScale;
		private float targetTranslationX;
		private float targetTranslationY;

		TouchAnimation(float targetScale, float targetTranslationX, float targetTranslationY) {
			loadMatrixValues();

			this.initialScale =  mScale;
			this.initialTranslationX = mTranslationX;
			this.initialTranslationY = mTranslationY;

			this.targetScale = targetScale;
			this.targetTranslationX = targetTranslationX;
			this.targetTranslationY = targetTranslationY;
		}

		@Override
		protected void applyTransformation(float interpolatedTime, Transformation t) {
			loadMatrixValues();

			if(interpolatedTime >= 1) {
				mMatrix.getValues(mMatrixValues);
				mMatrixValues[Matrix.MSCALE_X] = this.targetScale;
				mMatrixValues[Matrix.MSCALE_Y] = this.targetScale;
				mMatrixValues[Matrix.MTRANS_X] = this.targetTranslationX;
				mMatrixValues[Matrix.MTRANS_Y] = this.targetTranslationY;
				mMatrix.setValues(mMatrixValues);

			} else {
				final float scaleFactor = (this.initialScale + interpolatedTime * (this.targetScale - this.initialScale)) / mScale;
				mMatrix.postScale(scaleFactor, scaleFactor);

				mMatrix.getValues(mMatrixValues);
				final float currentTranslationX = mMatrixValues[Matrix.MTRANS_X];
				final float currentTranslationY = mMatrixValues[Matrix.MTRANS_Y];

				final float dx = this.initialTranslationX + interpolatedTime * (this.targetTranslationX - this.initialTranslationX) - currentTranslationX;
				final float dy = this.initialTranslationY + interpolatedTime * (this.targetTranslationY - this.initialTranslationY) - currentTranslationY;
				mMatrix.postTranslate(dx, dy);
			}

			ViewCompat.postInvalidateOnAnimation(TouchImageView.this);
		}
	}
}
