package www.dgl.com.flashlight;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by linzhiyong on 0020 06.20 .
 */
@SuppressLint("NewApi")
public class FlashLightActivity extends Activity implements View.OnClickListener {

    private final String TAG = "FlashLightActivity";
    private ToggleButton toggleButton;
    /** Camera相机硬件操作类 */
    private Camera m_Camera = null;

    private String cameraId = null;
    private boolean isOpen = false;
    /**
     * Camera2相机硬件操作类
     */
    private CameraManager manager = null;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession = null;
    private CaptureRequest request = null;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private boolean isSupportFlashCamera2 = false;
    private final CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {

        public void onConfigured(CameraCaptureSession arg0) {
            captureSession = arg0;
            CaptureRequest.Builder builder;
            try {
                builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                builder.addTarget(surface);
                request = builder.build();
                captureSession.capture(request, null, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        ;

        public void onConfigureFailed(CameraCaptureSession arg0) {
        }

        ;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleButton = (ToggleButton) this.findViewById(R.id.toggleButton1);
        toggleButton.setOnClickListener(this);
        //保持界面常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 初始化Camera硬件
        this.manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (isLOLLIPOP()) {
            initCamera2();
        } else {
            m_Camera = Camera.open();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    customScan();
                } else {
                    Toast.makeText(this, "You denied the permission", Toast.LENGTH_LONG).show();
                }
                break;
            default:
        }
    }

    @Override
    public void onClick(View v) {
        if (!isOpen) {
            if (isLOLLIPOP()) {
                try {
                    openCamera2Flash();
                } catch (CameraAccessException e) {
                    Log.e(TAG, e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                turnLightOnCamera(m_Camera);
            }
            isOpen=true;
        } else {
            if (isLOLLIPOP()) {
                if (cameraDevice != null) {
                    cameraDevice.close();
                }
            } else {
                turnLightOffCamera(m_Camera);
            }
            isOpen=false;
        }
    }


    /**
     * 调用Camera2开启闪光灯
     *
     * @throws CameraAccessException
     */
    private void openCamera2Flash() throws CameraAccessException {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,"您没有授权照相机的权限",Toast.LENGTH_LONG).show();
            return;
        }
        manager.openCamera(cameraId, new CameraDevice.StateCallback() {

            @Override
            public void onOpened(CameraDevice camera) {
                cameraDevice = camera;
                createCaptureSession();
            }

            @Override
            public void onError(CameraDevice camera, int error) {
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
            }
        }, null);
    }
    /**
     * 初始化Camera2
     */
    private void initCamera2() {
        try {
            for (String cameraId : this.manager.getCameraIdList()) {
                CameraCharacteristics characteristics = this.manager.getCameraCharacteristics(cameraId);
                // 过滤掉前置摄像头
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                this.cameraId = cameraId;
                // 判断设备是否支持闪光灯
                this.isSupportFlashCamera2 = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * 判断Android系统版本是否 >= LOLLIPOP(API21)
     *
     * @return boolean
     */
    private boolean isLOLLIPOP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 通过设置Camera打开闪光灯
     *
     * @param mCamera
     */
    public void turnLightOnCamera(Camera mCamera) {
        Camera.Parameters parameters = m_Camera.getParameters();
        List<String> flashModes = parameters.getSupportedFlashModes();
        String flashMode = parameters.getFlashMode();
        if (!Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
            // 开启闪光灯
            if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                mCamera.setParameters(parameters);
            }
        }
    }


    /**
     * createCaptureSession
     */
        @SuppressWarnings({ "rawtypes", "unchecked" })
    private void createCaptureSession(){
        this.surfaceTexture = new SurfaceTexture(0, false);
        this.surfaceTexture.setDefaultBufferSize(1280, 720);
        this.surface = new Surface(this.surfaceTexture);
        ArrayList localArrayList = new ArrayList(1);
        localArrayList.add(this.surface);
        try {
            this.cameraDevice.createCaptureSession(localArrayList, this.stateCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
    }
    /**
     * 通过设置Camera关闭闪光灯
     *
     * @param mCamera
     */
    public void turnLightOffCamera(Camera mCamera) {
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> flashModes = parameters.getSupportedFlashModes();
        String flashMode = parameters.getFlashMode();
        if (!Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
            // 关闭闪光灯
            if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mCamera.setParameters(parameters);
            }
        }
    }
}