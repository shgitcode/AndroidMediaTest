package com.shgit.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaFormat;
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
import com.shgit.mediasdk.decoder.CMCVidDec;
import com.shgit.mediasdk.encoder.CMCVidEnc;
import com.shgit.mediasdk.util.CFileManage;
import com.shgit.mediasdk.util.CMediaMuxer;
import com.shgit.mediasdk.util.CRawFrame;
import com.shgit.mediasdk.util.CVidInfo;
import com.shgit.mediasdk.util.CaptureParam;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/*
 * demo:
 *  采用相机采集视频数据，并进行预览；
 *  同时，将采集数据放入队列，并进行Mediacodec编码，将编码数据放入数据队列；
 *  同时，进行MediaCodec解码渲染显示。
 * */
public class EncToDecActivity extends AppCompatActivity {
    private final String TAG = "EncToDec";
    private final String STATE_CAMERA_ID = "currentCamId"; // 保存当前cameraID
    private final int     MAX_NET_QUEUE_SIZE = 10;

    private final int MAX_CAP_WIDTH = 1920;
    private final int MAX_CAP_HEIGHT = 1080;
    private final int MAX_CAP_YUV_LENGTH = MAX_CAP_WIDTH * MAX_CAP_HEIGHT * 3 / 2;

    private boolean m_bStopEnc = false;

    // 采集器
    private CVideoCapture m_cCapture;
    // 相机预览View
    private SurfaceView m_cCameraView;
    private SurfaceHolder m_cCameraHolder;
    // 解码渲染View
    private SurfaceView   m_cRenderView;

    // 编解码器
    private CMCVidEnc m_cEncoder = null;
    private CMCVidDec m_cDecoder = null;

    // 编码后的输出格式，用于解码输入格式
    private MediaFormat m_cEncDecFormat = null;

    // event
    private final int EVENT_CREATE_CAMERA = 1;
    private final int EVENT_SWITCH_CAMERA = 2;

    // 当前相机Id
    private int m_nCameraCurrentId = 0;
    // 相机数目
    private int m_nCameraNum = 0;

    // 停止编解码
    private boolean m_bStopEncDec = false;

    // 编码线程
    private Thread m_cEncThread = null;
    private boolean m_bEncoding = false;

    // 解码线程
    private Thread m_cDecThread = null;
    private boolean m_bDecoding = false;

    // Handler
    private Handler m_cEventHandler = null;
    private HandlerThread m_cHandlerThread = null;
    
    // 获取编码输出格式采用Object同步
    private Object  m_cOutputFormatSync = null;
    // 获取采集分辨率采用Condition同步
    private Lock m_cCameraResolutionLock = null;
    private Condition m_cCameraResolutionSync = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        /*
        *  Error: Error inflating class android.support.constraint.ConstraintLayout
        *  Need: y依据build.gradle===将android.support.constraint.ConstraintLayout更改为 androidx.constraintlayout.widget.ConstraintLayout 之后，
        * */
        setContentView(R.layout.activity_enc_to_dec);

        m_cCameraView  = findViewById(R.id.cameraSurface);
        m_cCameraHolder = m_cCameraView.getHolder();

        // 点击事件 --- 摄像头切换
        m_cCameraView.setOnClickListener(m_cClickHandler);

        m_cRenderView = findViewById(R.id.decSurface);

        // 从已保存状态中获取当前相机ID
        if (savedInstanceState != null) {
            m_nCameraCurrentId = savedInstanceState.getInt(STATE_CAMERA_ID);
        }

        // handler
        createHandler();

        // 获取采集分辨率同步
        m_cCameraResolutionLock = new ReentrantLock();
        m_cCameraResolutionSync = m_cCameraResolutionLock.newCondition();

        // 开启相机
        sendEventMessage(EVENT_CREATE_CAMERA);

        // 开启编解码线程
        startEncCamera();
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
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        // 停止采集
        m_cCapture.stop();

        m_bStopEncDec = true;

        // 停止编解码
        releaseEncCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        destroy();
    }

    private void destroy() {
        if(m_cCapture != null) {
            m_cCapture.destroy();
            m_cCapture = null;
        }

        if (m_cEncoder != null) {
            m_cEncoder.destroyMCEncoder();
            m_cEncoder = null;
        }

        if (m_cDecoder != null) {
           // m_cDecoder.;
            m_cDecoder = null;
        }

    }

    private void createHandler() {
        m_cHandlerThread = new HandlerThread("handlerThread");
        m_cHandlerThread.start();

        m_cEventHandler = new Handler(m_cHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "handleMessage : "+msg.what);
                switch(msg.what){
                    case EVENT_CREATE_CAMERA:

                        createCamera();

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

    private void sendEventMessage(int event) {
        Message msg = Message.obtain();
        msg.what = event; //消息的标识
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

    private int createCamera() {
        // 创建采集器
        m_cCapture = CVideoCapture.getInstance();
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
            if(m_cCapture.create(m_nCameraCurrentId, true)!= 0){
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
        tCapParam.cSurfaceHolder = m_cCameraHolder;
        tCapParam.nWidth  = 640;
        tCapParam.nHeight = 480;
        tCapParam.nMaxFPS = 30;

        if(m_cCapture.start(tCapParam) != 0){
            Log.e(TAG, "video Capture start failed");
            return -1;
        }

        // 通知
        m_cCameraResolutionLock.lock();
        m_cCameraResolutionSync.signalAll();

        return 0;
    }

    private void startEncCamera() {
        m_cOutputFormatSync = new Object();

        m_bStopEncDec = false;

        m_cEncThread = new Thread(encCameraData);
        m_cEncThread.start();
        m_bEncoding = true;

        m_cDecThread = new Thread(decEncData);
        m_cDecThread.start();
        m_bDecoding = true;
    }

    private void releaseEncCamera() {

        if (m_cEncThread != null) {
            try {
                m_cEncThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cEncThread = null;
        }

        if (m_cEncoder != null) {
            m_cEncoder.stopMCEncoder();
        }

        if (m_cDecThread != null) {
            try {
                m_cDecThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_cDecThread = null;
        }

        if (m_cDecoder != null) {
            m_cDecoder.stop();
        }
    }

    // 编码线程
    Runnable encCameraData = new Thread(new Runnable() {
        @Override
        public void run() {
            boolean bIsEos = false;
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

            // 等待采集分辨率
            /*
            m_cCameraResolutionLock.lock();
            try {
                m_cCameraResolutionSync.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
             */

            while (m_cCapture.getCapInfo() == null) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // 获取采集分辨率
            nWidth = m_cCapture.getCapInfo().getCapWidth();
            nHeight = m_cCapture.getCapInfo().getCapHeight();
            nFrameRate = m_cCapture.getCapInfo().getCapFrameRate();

            Log.d(TAG, "encCameraData width: "+nWidth+" height: "+nHeight);

            m_cEncoder = new CMCVidEnc(true);

            CVidInfo vidInfo = new CVidInfo(nWidth, nHeight, nFrameRate, 0);
            m_cEncoder.setMCParamter(vidInfo);
            m_cEncoder.createMCEncoder();
            m_cEncoder.startMCEncoder();

            while (m_bEncoding) {

                if (m_bStopEncDec) {
                    // 停止编码
                    if (m_cEncoder != null) {
                        cRawData = new CRawFrame();
                        cRawData.m_bIsEos = true;
                        m_cEncoder.setRawData(cRawData);
                    }

                    Log.d(TAG, "encCameraData stop encode EOS!");
                    break;
                }
                // 取得编码后输出格式
                if (m_cEncDecFormat == null) {
                    m_cEncDecFormat = m_cEncoder.getOutputMediaFormat();
                    if (m_cEncDecFormat != null) {
                        synchronized (m_cOutputFormatSync) {
                            m_cOutputFormatSync.notify();
                        }
                    }
                }

                cRawData = m_cCapture.getCapFrame();
                if(cRawData != null){

                    if (cRawData.m_bIsEos) {
                        bIsEos = true;
                    }

                    frameNum++;

                    delay = 1000/nFrameRate;

                    Log.d(TAG, "encCameraData encode frameNum: "+frameNum+" delay: " + delay);

                    if (m_cEncoder != null) {
                        m_cEncoder.setRawData(cRawData);
                    }
                }

                if (bIsEos) {
                    Log.d(TAG, "encCameraData encode EOS!");
                    m_bEncoding = false;
                    break;
                }

                try {
                    Thread.sleep(delay);
                    Log.d(TAG, "encCameraData sleep: " + delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    });

    // 解码编码数据线程
    Runnable decEncData = new Thread(new Runnable() {
        @Override
        public void run() {
            boolean bIsEos = false;
            int frameNum = 0;
            int delay = 10;

            CRawFrame cRawData = null;

            Log.d(TAG, "decEncData!");

            m_cDecoder = new CMCVidDec();
            m_cDecoder.create(m_cRenderView.getHolder().getSurface(), MediaFormat.MIMETYPE_VIDEO_AVC);

            // 等待获取编码输出格式
            synchronized (m_cOutputFormatSync) {
                try {
                    m_cOutputFormatSync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            m_cDecoder.setMediaFormat(m_cEncDecFormat);
            m_cDecoder.start();

            while (m_bDecoding) {

                cRawData = m_cEncoder.getEncodedData();
                if(cRawData != null){
                    if (cRawData.m_bIsEos) {
                        bIsEos = true;
                        Log.d(TAG, "decEncData get EOS!");
                    }

                    frameNum++;
                    Log.d(TAG, "saveEncData frameNum: "+frameNum);

                    if (m_cDecoder != null) {
                        m_cDecoder.setData(cRawData);
                    }
                }

                if (bIsEos) {
                    if (m_cDecoder != null) {
                        while (!m_cDecoder.getOutputEos()) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        Log.d(TAG, "decEncData stop EOS!");
                        m_bDecoding = true;
                        break;
                    }
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    });

    View.OnClickListener m_cClickHandler = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            switch(view.getId()) {
                case R.id.cameraPreview:
                    sendEventMessage(EVENT_SWITCH_CAMERA);
                    break;

                default:
                    break;
            }
        }
    };







}
