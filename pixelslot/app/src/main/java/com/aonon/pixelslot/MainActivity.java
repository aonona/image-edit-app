package com.aonon.pixelslot;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(33, 18, 52));
        window.setNavigationBarColor(Color.rgb(22, 12, 38));
        setContentView(new PixelSlotV9View(this));
    }
}
