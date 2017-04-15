package com.simoncherry.screenshotservice.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.simoncherry.screenshotservice.MyApplication;
import com.simoncherry.screenshotservice.R;
import com.simoncherry.screenshotservice.service.CaptureService;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();
    private int result = 0;
    private Intent intent = null;
    private int REQUEST_MEDIA_PROJECTION = 1;
    private MediaProjectionManager mediaProjectionManager;


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mediaProjectionManager = (MediaProjectionManager)getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        //startIntent();

        Button btnAddView = (Button) findViewById(R.id.btn_add_view);
        Button btnRemove = (Button) findViewById(R.id.btn_remove_view);

        btnAddView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startIntent();
            }
        });

        btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), CaptureService.class);
                intent.putExtra("stop", true);
                startService(intent);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startIntent(){
        if(intent != null && result != 0){
            Log.i(TAG, "user agree the application to capture screen");
            ((MyApplication)getApplication()).setResult(result);
            ((MyApplication)getApplication()).setIntent(intent);
            Intent intent = new Intent(getApplicationContext(), CaptureService.class);
            startService(intent);
            Log.i(TAG, "start service CaptureService");
        }else{
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
            ((MyApplication)getApplication()).setMediaProjectionManager(mediaProjectionManager);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }else if(data != null){
                Log.i(TAG, "user agree the application to capture screen");
                result = resultCode;
                intent = data;
                ((MyApplication)getApplication()).setResult(resultCode);
                ((MyApplication)getApplication()).setIntent(data);
                Intent intent = new Intent(getApplicationContext(), CaptureService.class);
                startService(intent);
                Log.i(TAG, "start service CaptureService");

                finish();
            }
        }
    }
}
