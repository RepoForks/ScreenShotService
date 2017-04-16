package com.simoncherry.screenshotservice.activity;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.simoncherry.screenshotservice.R;
import com.simoncherry.screenshotservice.util.FileUtil;
import com.simoncherry.screenshotservice.util.MediaScanner;
import com.yalantis.ucrop.UCrop;

import java.io.File;


public class CropActivity extends AppCompatActivity {

    private final static String TAG = CropActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        getBundle();
    }

    private void getBundle() {
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            String srcPath = bundle.getString("srcPath");
            String dstPath = bundle.getString("dstPath");
            if (srcPath != null && dstPath != null) {
                Log.i(TAG, "srcPath: " + srcPath);
                Log.i(TAG, "dstPath: " + dstPath);
                startCropIntent(srcPath, dstPath);
            } else {
                finishIfNoPath();
            }
        } else {
            finishIfNoPath();
        }
    }

    private void finishIfNoPath() {
        Toast.makeText(this, "获取图片路径失败", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void startCropIntent(String srcPath, String dstPath) {
        Resources resources = this.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        UCrop.of(Uri.fromFile(new File(srcPath)), Uri.fromFile(new File(dstPath)))
                .withAspectRatio(1, 1)
                .withMaxResultSize(screenWidth, screenHeight)
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK) {
                final Uri resultUri = UCrop.getOutput(data);
                String path = FileUtil.getRealFilePath(getApplicationContext(), resultUri);
                MediaScanner.callMediaScanner(getApplicationContext(), path);
                Toast.makeText(getApplicationContext(), "图片保存到: " + path, Toast.LENGTH_SHORT).show();
            }
            finish();

        } else if (resultCode == UCrop.RESULT_ERROR) {
            finishIfNoPath();
        }
    }
}
