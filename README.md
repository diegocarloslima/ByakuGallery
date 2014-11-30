# ByakuGallery
ByakuGallery is an open source Android library that allows the visualization of large images with gesture capabilities. This lib is based on AOSP [Camera2](https://android.googlesource.com/platform/packages/apps/Camera2/).

![Sample Screenshot](https://github.com/diegocarloslima/ByakuGallery/raw/master/sample.png)&nbsp;![Sample Animation](https://github.com/diegocarloslima/ByakuGallery/raw/master/sample_animation.gif)

The name was inspired by the [Byakugan](http://naruto.wikia.com/wiki/Byakugan) from Naruto series :)

## Features
- The image is split in small tiles and only the visible portion is allocated on memory, avoiding `OutOfmemoryError`.
- Full gesture capabilities: Click, LongClick, DoubleTap, Pan, Fling and Pinch Zoom.
- Smooth animations.
- Can easily be placed inside a parent View with scrolling (e.g. `GalleryViewPager`).

## Sample Application
[![Get it on Google Play](http://www.android.com/images/brand/get_it_on_play_logo_small.png)](https://play.google.com/store/apps/details?id=com.diegocarloslima.byakugallery)

The sample app project code is also included on this repository.

## Usage
The ByakuGallery was designed to be the simplest possible to use. Also, the two main classes `TileBitmapDrawable` and `TouchImageView` were designed to be independent. That means you can use them separately if you want to :)

Here are the few steps needed to setup:

1. Add the `TouchImageView` to your xml file:

    ```xml
    <com.diegocarloslima.byakugallery.lib.TouchImageView
    android:id="@+id/my_image"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
    ```
2. Them add these lines to your java code (usually on your `Activity.onCreate()` or `Fragment.onCreateView()` method):

    ```java
    TouchImageView myImage = (TouchImageView) findViewById(R.id.my_image);
    InputStream is = getResources.openRawResource(R.raw.image);
    TileBitmapDrawable.attachTileBitmapDrawable(myImage, is, null, null);
    ```
3. And that's all! For a complete implementation, you can take a look at the sample project.

## Gradle
Add the following dependency to your `build.gradle` file:

```groovy
dependencies {
    compile 'com.diegocarloslima:byakugallery:0.1.+@aar'
}
```

## Used by

Let me know if you are using this lib in your app. I'll be glad to put your app name here :).

## Contributing

Pull requests with bug fixes or new features are always welcome :), but please, send me a separate pull request for each bug fix or feature. Also, you can [contact](mailto:diego@diegocarloslima.com) me to discuss a new feature before implementing it.

## Developed By

Diego Carlos Lima: <diego@diegocarloslima.com>

## License

    Copyright 2013 Diego Carlos Lima

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
