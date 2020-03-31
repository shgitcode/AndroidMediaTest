package com.shgit.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.shgit.mediasdk.capture.CVideoCapture;
import com.shgit.mediasdk.encoder.CMCVidEnc;
import com.shgit.mediasdk.util.CFileManage;
import com.shgit.mediasdk.util.CRawFrame;
import com.shgit.mediasdk.util.CVidInfo;
import com.shgit.mediasdk.util.CaptureParam;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/*
*  预览采集视频
*  支持前后摄像头切换
*  可以编码YUV数据，并保存为H264
*  camera + handlerThread : https://www.jianshu.com/p/a6b51b7b2af9
* */
public class CameraPreviewActivity extends AppCompatActivity {
    private final String TAG = "CameraPreview";
    // 保存当前cameraID
    private final String STATE_CAMERA_ID = "currentCamId";
    // event
    private final int EVENT_OPEN_CAMERA = 1;
    private final int EVENT_SWITCH_CAMERA = 2;
    // 采集器
    private CVideoCapture m_cCapture = null;
    // 编码器
    private CMCVidEnc m_cEncoder = null;
    // 预览View
    private SurfaceView m_cSurfaceView = null;
    private SurfaceHolder m_cSurfaceHolder = null;
    // 当前相机Id
    private int m_nCameraCurrentId = 0;
    // 相机数目
    private int m_nCameraNum = 0;
    // 编码按钮
    private Button m_cBtnEnc = null;
    private boolean m_bStopEnc = false;
    // 编码线程
    private Thread m_cEncThread = null;
    private boolean m_bEncoding = false;
    // Handler&HandlerThread
    // https://www.jianshu.com/p/9c10beaa1c95
    private Handler m_cEventHandler = null;
    private HandlerThread m_cHandlerThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_camera_preview);

        // 从已保存状态中获取当前相机ID
        if (savedInstanceState != null) {
            m_nCameraCurrentId = savedInstanceState.getInt(STATE_CAMERA_ID);
        }

        // 预览
        m_cSurfaceView = findViewById(R.id.cameraPreview);
        m_cSurfaceView.setOnClickListener(m_cClickHandler);

        // 编码
        m_cBtnEnc = findViewById(R.id.cameraEnc);
        m_cBtnEnc.setOnClickListener(m_cClickHandler);

        // 创建采集器
        m_cCapture = CVideoCapture.getInstance();
        m_cSurfaceHolder = m_cSurfaceView.getHolder();
        m_cSurfaceHolder.addCallback(m_cCapture.getCameraWrapper());

        // handler
        createHandler();

        m_bStopEnc = false;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // 保存当前相机的ID
        // 当相机切换到前置时，由于当手机旋转Activity重启，导致摄像头切换的后置摄像头
        // 从而需保存此当前相机的ID，确保手机旋转后，还保留在前置
        if(m_cCapture != null) {
            savedInstanceState.putInt(STATE_CAMERA_ID, m_cCapture.getCameraId());
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected  void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        sendEventMessage(EVENT_OPEN_CAMERA);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        m_cCapture.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        destroyHandler();
        stopEncCamera();
        m_cCapture.destroy();
    }

    private void createHandler() {
        m_cHandlerThread = new HandlerThread("handlerThread");
        m_cHandlerThread.start();

        m_cEventHandler = new Handler(m_cHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "handleMessage: "+msg.what);
                switch(msg.what){
                    case EVENT_OPEN_CAMERA:

                        openCamera((SurfaceView)msg.obj);

                        break;

                    case EVENT_SWITCH_CAMERA:

                        switchCamera();

                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void destroyHandler() {
        if (m_cHandlerThread != null) {
            m_cHandlerThread.quit();
            m_cHandlerThread = null;
            m_cEventHandler = null;
        }
    }

    private void sendEventMessage(int event) {
        Message msg = Message.obtain();
        msg.what = event; //消息的标识

        switch(msg.what){
            case EVENT_OPEN_CAMERA:
                msg.obj = m_cSurfaceView;
                break;

            case EVENT_SWITCH_CAMERA:

                break;
            default:
                break;
        }

        m_cEventHandler.sendMessage(msg);
    }

    private int switchCamera(){
        Log.d(TAG, "switchCamera");

        if(m_cCapture.switchCamera() != 0){
            Log.e(TAG, "switchCamera failed");
            return -1;
        }

        return 0;
    }

    private int openCamera(SurfaceView surView) {
        // 采集器
        if(m_cCapture == null){
            Log.e(TAG, "Get video Capture Instance error!");
            return -1;
        }

        // 设置上下文
        {
            m_cCapture.setContext(this);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            m_cCapture.setDispRotation(rotation);
            Log.d(TAG, "Window Display Rotation: " + rotation);
        }

        // 相机权限检测
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            // 打开摄像头
            if(m_cCapture.create(m_nCameraCurrentId, false)!= 0){
                Log.e(TAG, "video Capture create failed");
                return -1;
            }

            // 相机数
            m_nCameraNum = m_cCapture.getCameraCount();
            Log.d(TAG, "Camera numbers: " + m_nCameraNum);

        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 1);//1 can be another integer
        }

        // 设置采集参数
        CaptureParam tCapParam = new CaptureParam();
        tCapParam.cSurfaceHolder = m_cSurfaceHolder;
        tCapParam.nWidth  = 640;
        tCapParam.nHeight = 480;
        tCapParam.nMaxFPS = 30;

        if(m_cCapture.start(tCapParam) != 0){
            Log.e(TAG, "video Capture start failed");
            return -1;
        }

        return 0;
    }

    private void startEncCamera() {
        m_cEncThread = new Thread(encCameraData);
        m_cEncThread.start();
        m_bEncoding = true;
    }

    private void stopEncCamera() {
        m_bEncoding = false;

        if (m_cEncThread != null) {
            try {
                m_cEncThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cEncThread = null;
        }

        if (m_cEncoder != null) {
            m_cEncoder.stop();
            m_cEncoder = null;
        }
    }

    private void encodeCamera() {
        if (m_bStopEnc) {
            stopEncCamera();
            m_bStopEnc = false;
            m_cBtnEnc.setText("startEnc");

        } else {
            m_bStopEnc = true;
            startEncCamera();
            m_cBtnEnc.setText("stopEnc");
        }
    }

    // 编码线程
    Runnable encCameraData = new Thread(new Runnable() {
        @Override
        public void run() {
            int isEos = 0;
            int delay = 10;
            int frameNum = 0;

            int nWidth = 640;
            int nHeight = 480;
            int nFrameRate = 30;

            CRawFrame cRawData = null;

            Log.d(TAG, "encCameraData!");

            if (m_cCapture == null) {
                return;
            }

            // 采集分辨率
            nWidth = m_cCapture.getCapInfo().getCapWidth();
            nHeight = m_cCapture.getCapInfo().getCapHeight();
            //nFrameRate = m_cCapture.getCapInfo().getCapFrameRate();

            m_cEncoder = new CMCVidEnc(false);

            CVidInfo vidInfo = new CVidInfo(nWidth, nHeight, nFrameRate, 0);
            m_cEncoder.setMCParamter(vidInfo);
            m_cEncoder.createMCEncoder();
            m_cEncoder.start();

            // 开启队列
            m_cCapture.setCapDataQueue(true);

            while (true) {

                if(m_bEncoding){
                    cRawData = m_cCapture.getCapFrame();
                    if(cRawData != null){
                        if (cRawData.m_bIsEos) {
                            isEos = 1;
                        }

                        frameNum++;

                        delay = 1000/nFrameRate;

                        Log.d(TAG, "encCameraData encode frameNum: "+frameNum+" delay: " + delay);

                        if (m_cEncoder != null) {
                            m_cEncoder.setRawData(cRawData);
                        }
                    }

                    if (isEos == 1) {// 数据全部取出
                        Log.d(TAG, "encCameraData encoder EOS! ");
                        break;
                    }

                    try {
                        Thread.sleep(delay);
                        Log.d(TAG, "encCameraData sleep: " + delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else{
                    if (m_cCapture != null) {
                        m_cCapture.setCapDataQueue(false);

                        // 全部取出采集数据
                        while (!m_cCapture.haveCapFrame()) {
                            cRawData = m_cCapture.getCapFrame();
                            if (m_cEncoder != null) {
                                m_cEncoder.setRawData(cRawData);
                            }
                        }
                    }

                    // 编码器队列写入EOS
                    if (m_cEncoder != null) {
                        cRawData = new CRawFrame();
                        cRawData.m_bIsEos = true;
                        m_cEncoder.setRawData(cRawData);
                        Log.d(TAG, " encCameraData set encoder EOS! ");

                        // 等待编码结束
                        while(!m_cEncoder.getOutputEos()) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    Log.d(TAG, "stop encCameraData encoder EOS! ");
                    break;
                }

            }
        }
    });

    View.OnClickListener m_cClickHandler = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            switch(view.getId()) {
                case R.id.cameraEnc:
                    encodeCamera();
                    break;

                case R.id.cameraPreview:
                    sendEventMessage(EVENT_SWITCH_CAMERA);
                    break;

                default:
                    break;
            }
        }
    };
}
