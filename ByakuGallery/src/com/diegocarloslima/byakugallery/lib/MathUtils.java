/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.diegocarloslima.byakugallery.lib;

public class MathUtils {
	
	public static int ceilLog2(float value) {
		int i;
		for (i = 0; i < 31; i++) {
			if ((1 << i) >= value) break;
		}
		return i;
	}

	public static int floorLog2(float value) {
		int i;
		for (i = 0; i < 31; i++) {
			if ((1 << i) > value) break;
		}
		return i - 1;
	}

	// Returns the input value x clamped to the range [min, max].
	public static int clamp(int x, int min, int max) {
		if (x > max) return max;
		if (x < min) return min;
		return x;
	}

	// Returns the input value x clamped to the range [min, max].
	public static float clamp(float x, float min, float max) {
		if (x > max) return max;
		if (x < min) return min;
		return x;
	}

	// Returns the input value x clamped to the range [min, max].
	public static long clamp(long x, long min, long max) {
		if (x > max) return max;
		if (x < min) return min;
		return x;
	}
}
