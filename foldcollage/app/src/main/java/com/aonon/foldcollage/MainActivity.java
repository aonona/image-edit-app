package com.aonon.foldcollage;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements CollageView.Listener {
    private static final int REQUEST_IMAGE = 1201;
    private static final int BG = Color.rgb(11, 13, 17);
    private static final int PANEL = Color.rgb(21, 25, 34);
    private static final int TEXT = Color.rgb(236, 240, 247);
    private static final int MUTED = Color.rgb(157, 168, 186);
    private static final int ACCENT = Color.rgb(110, 168, 254);

    private CollageView collageView;
    private AspectBox aspectBox;
    private TextView statusText;
    private int pendingCell = -1;
    private float aspectRatio = 1.0f;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        collageView = new CollageView(this);
        collageView.setListener(this);
        buildUi();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void buildUi() {
        if (collageView.getParent() instanceof ViewGroup) {
            ((ViewGroup) collageView.getParent()).removeView(collageView);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView title = new TextView(this);
        title.setText("FoldCollage");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        boolean innerScreen = getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout work = new LinearLayout(this);
        work.setOrientation(innerScreen ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(work, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        aspectBox = new AspectBox(this);
        aspectBox.setBackgroundColor(Color.rgb(6, 8, 11));
        aspectBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        aspectBox.setAspectRatio(aspectRatio);
        aspectBox.addView(collageView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View controls = buildControls(innerScreen);
        if (innerScreen) {
            work.addView(aspectBox, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            work.addView(controls, new LinearLayout.LayoutParams(dp(330),
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            work.addView(aspectBox, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            work.addView(controls, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
        }

        setContentView(root);
    }

    private View buildControls(boolean innerScreen) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PANEL);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(14));
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setText("枠をタップして写真を選択。写真はドラッグ・ピンチ、分割線はそのままドラッグできます。");
        statusText.setTextColor(MUTED);
        statusText.setTextSize(13);
        statusText.setPadding(0, 0, 0, dp(8));
        panel.addView(statusText);

        panel.addView(sectionLabel("レイアウト"));
        panel.addView(horizontalButtonsForLayouts());
        panel.addView(sectionLabel("出力比率"));
        panel.addView(horizontalButtonsForRatios());

        LinearLayout editRow = new LinearLayout(this);
        editRow.setOrientation(LinearLayout.HORIZONTAL);
        Button change = button("写真を変更", false);
        change.setOnClickListener(v -> collageView.requestImageForSelected());
        Button reset = button("写真位置リセット", false);
        reset.setOnClickListener(v -> collageView.resetSelectedTransform());
        editRow.addView(change, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        resetLp.setMarginStart(dp(8));
        editRow.addView(reset, resetLp);
        panel.addView(editRow);

        LinearLayout resetRow = new LinearLayout(this);
        resetRow.setOrientation(LinearLayout.HORIZONTAL);

        Button resetDividers = button("分割線リセット", false);
        resetDividers.setOnClickListener(v -> {
            collageView.resetDividers();
            statusText.setText("分割線を初期位置に戻しました。写真の位置・ズームはそのままです。");
        });
        resetRow.addView(resetDividers, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button resetAll = button("全体位置リセット", false);
        resetAll.setOnClickListener(v -> {
            collageView.resetAllTransformsAndDividers();
            statusText.setText("写真の位置・ズームと分割線を初期位置に戻しました。写真そのものは残しています。");
        });
        LinearLayout.LayoutParams resetAllLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        resetAllLp.setMarginStart(dp(8));
        resetRow.addView(resetAll, resetAllLp);

        LinearLayout.LayoutParams resetRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        resetRowLp.topMargin = dp(8);
        panel.addView(resetRow, resetRowLp);

        Button save = button("ギャラリーへ保存（高画質）", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        saveLp.topMargin = dp(10);
        panel.addView(save, saveLp);
        save.setOnClickListener(v -> saveImage());

        TextView hint = new TextView(this);
        hint.setText(innerScreen
                ? "Fold8 内画面：編集領域を広く、操作は右側に固定しています。"
                : "Fold8 カバー画面：片手で届きやすい下部に操作をまとめています。");
        hint.setTextColor(MUTED);
        hint.setTextSize(12);
        hint.setPadding(0, dp(8), 0, 0);
        panel.addView(hint);
        return scroll;
    }

    private HorizontalScrollView horizontalButtonsForLayouts() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (CollageView.Preset p : CollageView.Preset.values()) {
            Button b = button(p.label, p == collageView.getPreset());
            b.setOnClickListener(v -> {
                collageView.setPreset(p);
                buildUi();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
            lp.setMarginEnd(dp(6));
            row.addView(b, lp);
        }
        hsv.addView(row);
        return hsv;
    }

    private HorizontalScrollView horizontalButtonsForRatios() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addRatioButton(row, "1:1", 1f);
        addRatioButton(row, "4:5", 4f / 5f);
        addRatioButton(row, "3:4", 3f / 4f);
        addRatioButton(row, "9:16", 9f / 16f);
        addRatioButton(row, "16:9", 16f / 9f);
        hsv.addView(row);
        return hsv;
    }

    private void addRatioButton(LinearLayout row, String label, float ratio) {
        Button b = button(label, Math.abs(aspectRatio - ratio) < 0.001f);
        b.setOnClickListener(v -> {
            aspectRatio = ratio;
            if (aspectBox != null) aspectBox.setAspectRatio(aspectRatio);
            buildUi();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(74), dp(48));
        lp.setMarginEnd(dp(6));
        row.addView(b, lp);
    }

    private TextView sectionLabel(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(TEXT);
        v.setTextSize(13);
        v.setPadding(0, dp(6), 0, dp(5));
        return v;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? ACCENT : Color.rgb(48, 56, 71)));
        return b;
    }

    @Override
    public void onRequestImage(int cellIndex) {
        pendingCell = cellIndex;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final int cell = pendingCell;
        final Uri uri = data.getData();
        pendingCell = -1;

        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }

        try {
            Bitmap bitmap = decodeForEditing(uri);
            collageView.setImageForCell(cell, uri, bitmap);
            statusText.setText("写真 " + (cell + 1) + " を読み込みました。ドラッグ・ピンチで調整できます。");
        } catch (IOException e) {
            Toast.makeText(this, "写真を読み込めませんでした", Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap decodeForEditing(Uri uri) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            int longest = Math.max(w, h);
            if (longest > 3072) {
                float scale = 3072f / longest;
                decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
            }
        });
    }

    private void saveImage() {
        if (!collageView.isComplete()) {
            Toast.makeText(this, "すべての枠に写真を入れてください", Toast.LENGTH_SHORT).show();
            return;
        }
        statusText.setText("高画質画像を書き出しています…");
        final Bitmap output;
        try {
            output = collageView.renderForExport(aspectRatio, 3000);
        } catch (OutOfMemoryError e) {
            Toast.makeText(this, "画像の作成に失敗しました。端末の空きメモリを確認してください。", Toast.LENGTH_LONG).show();
            return;
        }

        io.execute(() -> {
            Uri saved = null;
            try {
                saved = writeToGallery(output);
            } catch (Exception ignored) { }
            output.recycle();
            Uri finalSaved = saved;
            runOnUiThread(() -> {
                if (finalSaved != null) {
                    statusText.setText("保存しました：Pictures/FoldCollage");
                    Toast.makeText(this, "ギャラリーに保存しました", Toast.LENGTH_SHORT).show();
                } else {
                    statusText.setText("保存に失敗しました");
                    Toast.makeText(this, "保存に失敗しました", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private Uri writeToGallery(Bitmap bitmap) throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "FoldCollage_" + stamp + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FoldCollage");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = getContentResolver().insert(collection, values);
        if (uri == null) return null;
        boolean ok = false;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out != null) ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out);
        }
        if (!ok) {
            getContentResolver().delete(uri, null, null);
            return null;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        getContentResolver().update(uri, done, null, null);
        return uri;
    }

    @Override
    public void onSelectionChanged(int cellIndex) {
        if (statusText != null) {
            statusText.setText("写真 " + (cellIndex + 1) + " を選択中。空ならタップ、入替は「写真を変更」。");
        }
    }

    @Override
    public void onStateChanged() { }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
