package com.simplemobiletools.camera;

import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    @Bind(R.id.viewHolder) RelativeLayout viewHolder;
    @Bind(R.id.toggle_camera) View toggleCameraBtn;

    private Preview preview;
    private int currCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        currCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        preview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView));
        preview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        viewHolder.addView(preview);
    }

    @OnClick(R.id.toggle_camera)
    public void toggleCamera() {

    }

    @OnClick(R.id.shutter)
    public void takePicture() {
        preview.takePicture();
    }

    @Override
    protected void onResume() {
        super.onResume();

        final int cnt = Camera.getNumberOfCameras();
        if (cnt == 1) {
            toggleCameraBtn.setVisibility(View.INVISIBLE);
        }
        Camera camera = Camera.open(currCamera);
        preview.setCamera(camera);
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview.releaseCamera();
    }
}
