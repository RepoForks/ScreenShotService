package com.simoncherry.screenshotservice.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.simoncherry.screenshotservice.MyApplication;
import com.simoncherry.screenshotservice.R;
import com.simoncherry.screenshotservice.util.BitmapUtil;
import com.simoncherry.screenshotservice.util.MediaScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class CaptureService extends Service {

    private static final String TAG = CaptureService.class.getSimpleName();

    //
    private ImageReader mImageReader;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    //
    public static int mResultCode = 0;
    public static Intent mResultData;
    public static MediaProjectionManager mMediaProjectionManager;
    //
    private WindowManager.LayoutParams wmParams;
    private WindowManager windowManager;
    private LayoutInflater inflater;
    // 悬浮按钮控件
    private RelativeLayout layoutFloatBtn;
    private ImageButton floatBtn;
    // 截图预览控件
    private RelativeLayout layoutPreview;
    private ImageView ivPreview;
    // 保存截图时文件命名用的
    private SimpleDateFormat dateFormat;
    private String strDate = null;
    private String pathImage = null;
    private String nameImage = null;
    // VirtualDisplay的尺寸、密度
    private int windowWidth = 0;
    private int windowHeight = 0;
    private int mScreenDensity = 0;
    // 保存上一次点击悬浮按钮的坐标
    private float lastX = 0;
    private float lastY = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createFloatView();
        createVirtualEnvironment();
    }

    @Override
    public void onDestroy() {
        // to remove layoutFloatBtn from windowManager
        super.onDestroy();
        stopVirtual();  // TODO add by simon
        if(layoutFloatBtn != null) {
            windowManager.removeView(layoutFloatBtn);
        }
        tearDownMediaProjection();
        Log.i(TAG, "application destroy");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getBooleanExtra("stop", false)) {
            Log.i(TAG, "stopSelf");
            stopSelf();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建悬浮按钮
     */
    private void createFloatView() {
        windowManager = (WindowManager)getApplication().getSystemService(Context.WINDOW_SERVICE);
        inflater = LayoutInflater.from(getApplication());
        wmParams = getFloatLayoutParams();

        layoutFloatBtn = (RelativeLayout) inflater.inflate(R.layout.layout_float_btn, null);
        floatBtn = (ImageButton) layoutFloatBtn.findViewById(R.id.float_btn);
        windowManager.addView(layoutFloatBtn, wmParams);

        layoutFloatBtn.measure(View.MeasureSpec.makeMeasureSpec(0,
                View.MeasureSpec.UNSPECIFIED), View.MeasureSpec
                .makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        floatBtn.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getX();
                        lastY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        moveFloatButton(event);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(event.getX() - lastX) < 10f && Math.abs(event.getY() - lastY) < 10f) {
                            handleCapture();
                        }
                        break;
                }
                return false;
            }
        });

        Log.i(TAG, "created the float sphere view");
    }

    /**
     * 返回悬浮按钮所在布局的LayoutParams
     *
     * @return
     */
    private LayoutParams getFloatLayoutParams() {
        LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = LayoutParams.TYPE_PHONE;  // 电话窗口。它用于电话交互（特别是呼入）。它置于所有应用程序之上，状态栏之下。
        //layoutParams.type = LayoutParams.TYPE_SYSTEM_ALERT;  // 系统提示。它总是出现在应用程序窗口之上。
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.flags = LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.gravity = Gravity.START | Gravity.TOP;
        layoutParams.x = 0;
        layoutParams.y = 0;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        return layoutParams;
    }

    /**
     * 返回预览布局的LayoutParams
     *
     * @return
     */
    private LayoutParams getPreviewLayoutParams() {
        LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = LayoutParams.TYPE_PHONE;  // 电话窗口。它用于电话交互（特别是呼入）。它置于所有应用程序之上，状态栏之下。
        //layoutParams.type = LayoutParams.TYPE_SYSTEM_ALERT;  // 系统提示。它总是出现在应用程序窗口之上。
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.flags = LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.x = 0;
        layoutParams.y = 0;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        return layoutParams;
    }

    /**
     * 截图后，将预览布局添加到屏幕上
     *  PS.之前试过截图前将预览布局隐藏，截图后才显示，但截出来的画面依然能看到预览布局，似乎可见性无效
     */
    private void addPreviewLayout() {
        LayoutParams previewLayoutParams = getPreviewLayoutParams();
        layoutPreview = (RelativeLayout) inflater.inflate(R.layout.layout_preview, null);
        ivPreview = (ImageView) layoutPreview.findViewById(R.id.iv_preview);
        Button btnCancel = (Button) layoutPreview.findViewById(R.id.btn_cancel);
        Button btnOK = (Button) layoutPreview.findViewById(R.id.btn_ok);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removePreviewLayout();
                floatBtn.setVisibility(View.VISIBLE);
            }
        });

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap bitmap = BitmapUtil.getBitmapFromView(ivPreview);
                if (bitmap != null) {
                    saveBitmapToFile(bitmap);
                }

                removePreviewLayout();
                floatBtn.setVisibility(View.VISIBLE);
            }
        });

        windowManager.addView(layoutPreview, previewLayoutParams);
    }

    /**
     * 完成截图后，移除预览布局
     */
    private void removePreviewLayout() {
        windowManager.removeView(layoutPreview);
    }

    /**
     * 拖动悬浮按钮
     *
     * @param event
     */
    private void moveFloatButton(MotionEvent event) {
        wmParams.x = (int) event.getRawX() - floatBtn.getMeasuredWidth() / 2;
        wmParams.y = (int) event.getRawY() - floatBtn.getMeasuredHeight() / 2 - 25;
        windowManager.updateViewLayout(layoutFloatBtn, wmParams);
    }

    /**
     * 截图流程
     */
    private void handleCapture() {
        // hide the button
        floatBtn.setVisibility(View.INVISIBLE);

        Handler handler1 = new Handler();
        handler1.postDelayed(new Runnable() {
            public void run() {
                //start virtual
                startVirtual();
            }
        }, 1500);

        Handler handler2 = new Handler();
        handler2.postDelayed(new Runnable() {
            public void run() {
                //capture the screen
                startCapture();
            }
        }, 1500);

//        Handler handler3 = new Handler();
//        handler3.postDelayed(new Runnable() {
//            public void run() {
//                //floatBtn.setVisibility(View.VISIBLE);
//                //stopVirtual();
//            }
//        }, 1000);
    }

    private void createVirtualEnvironment(){
        dateFormat = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.CHINA);
        strDate = dateFormat.format(new java.util.Date());
        pathImage = Environment.getExternalStorageDirectory().getPath()+"/Pictures/";
        nameImage = pathImage + strDate + ".png";
        mMediaProjectionManager = (MediaProjectionManager)getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        WindowManager mWindowManager1 = (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
        windowWidth = mWindowManager1.getDefaultDisplay().getWidth();
        windowHeight = mWindowManager1.getDefaultDisplay().getHeight();
        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager1.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        //mImageReader = ImageReader.newInstance(windowWidth, windowHeight, 0x1, 2); //ImageFormat.RGB_565
        mImageReader = ImageReader.newInstance(windowWidth, windowHeight, PixelFormat.RGBA_8888, 2); //ImageFormat.RGB_565

        Log.i(TAG, "prepared the virtual environment");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void startVirtual(){
        if (mMediaProjection != null) {
            Log.i(TAG, "want to display virtual");
            virtualDisplay();
        } else {
            Log.i(TAG, "start screen capture intent");
            Log.i(TAG, "want to build mediaprojection and display virtual");
            setUpMediaProjection();
            //virtualDisplay();

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    virtualDisplay();
                }
            }, 1000);

        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setUpMediaProjection(){
        mResultData = ((MyApplication)getApplication()).getIntent();
        mResultCode = ((MyApplication)getApplication()).getResult();
        mMediaProjectionManager = ((MyApplication)getApplication()).getMediaProjectionManager();
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
        Log.i(TAG, "mMediaProjection defined");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void virtualDisplay(){
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                windowWidth, windowHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
        Log.i(TAG, "virtual displayed");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startCapture(){
        strDate = dateFormat.format(new java.util.Date());
        nameImage = pathImage + strDate + ".png";

        Image image = mImageReader.acquireLatestImage();
        if (image != null) {
            Log.i(TAG, "image data captured");
            Bitmap bitmap = convertImageToBitmap(image);
            if (bitmap != null) {
                //saveBitmapToFile(bitmap);
                addPreviewLayout();
                ivPreview.setImageBitmap(bitmap);
            } else {
                floatBtn.setVisibility(View.VISIBLE);
            }
        } else {
            floatBtn.setVisibility(View.VISIBLE);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(TAG,"mMediaProjection undefined");
    }

    private void stopVirtual() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        Log.i(TAG,"virtual display stopped");
    }

    /**
     * 将从ImageReader获取到的Image转换成Bitmap
     *
     * @param image
     * @return
     */
    private Bitmap convertImageToBitmap(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        image.close();
        return bitmap;
    }

    /**
     * 将Bitmap保存到文件
     *
     * @param bitmap
     */
    private void saveBitmapToFile(Bitmap bitmap) {
        try {
            File fileImage = new File(nameImage);
            if (!fileImage.exists()) {
                fileImage.createNewFile();
                Log.i(TAG, "image file created");
            }
            FileOutputStream out = new FileOutputStream(fileImage);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            Log.i(TAG, "screen image saved");

//            Intent media = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//            Uri contentUri = Uri.fromFile(fileImage);
//            media.setData(contentUri);
//            this.sendBroadcast(media);
            callMediaScanner(nameImage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 通知图库刷新
     *
     * @param path
     */
    private void callMediaScanner(String path) {
        MediaScanner mediaScanner = new MediaScanner(getApplicationContext());
        String[] filePaths = new String[]{path};
        String[] mimeTypes = new String[]{MimeTypeMap.getSingleton().getMimeTypeFromExtension("png")};
        mediaScanner.scanFiles(filePaths, mimeTypes);
    }
}