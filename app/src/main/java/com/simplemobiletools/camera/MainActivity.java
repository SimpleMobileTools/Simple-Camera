package com.simplemobiletools.camera;

import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    @Bind(R.id.viewHolder) RelativeLayout viewHolder;

    private static final String TAG = Preview.class.getSimpleName();
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private Preview preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        preview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView));
        preview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        viewHolder.addView(preview);
    }

    @OnClick(R.id.shutter)
    public void takePicture() {
        preview.takePicture();
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            Camera camera = Camera.open(0);
            preview.setCamera(camera);
        } catch (Exception e) {
            Log.e(TAG, "onResume IOException " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview.releaseCamera();
    }
}
