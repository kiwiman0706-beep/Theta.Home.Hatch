package com.theta.homehatch;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

/**
 * 実際の画面を JVM 上で描いて PNG に残す（app/build/screenshots/）。
 * 権限・フォルダ・データが無い状態の見た目。実機の代わりにはならない。
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class ScreenshotTest {
    private void shot(String name) throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        View root = a.getWindow().getDecorView();
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File out = new File("build/screenshots/" + name + ".png");
        out.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(out)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    public void cover() throws Exception { shot("fold5-cover"); }

    @Test @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    public void main() throws Exception { shot("fold5-main"); }

    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    public void tablet() throws Exception { shot("tablet"); }

    @Test @Config(qualifiers = "w480dp-h480dp-xhdpi")
    public void titan() throws Exception { shot("titan"); }
}
