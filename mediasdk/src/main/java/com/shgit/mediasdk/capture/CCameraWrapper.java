package com.shgit.mediasdk.capture;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.shgit.mediasdk.util.CDataQueue;
import com.shgit.mediasdk.util.CFileManage;
import com.shgit.mediasdk.util.CRawFrame;
import com.shgit.mediasdk.util.CaptureParam;

import java.util.List;

import androidx.annotation.NonNull;


/*
*  预览采集数据
*  可以保存指定数目的YUV数据
*  可以设置需编码存储数据的队列
* */
public class CCameraWrapper implements SurfaceHolder.Callback {
    private static final String TAG = "CCameraWrapper";

    private static final int MAX_CAP_WIDTH = 1920;
    private static final int MAX_CAP_HEIGHT = 1080;
    private static final int EXPEXT_PREVIEW_FPS = 30;
    private final int MAX_CAP_YUV_LENGTH = MAX_CAP_WIDTH * MAX_CAP_HEIGHT * 3 / 2;
    private final int QUEUE_LENGTH = 10;

    // 文件存储采集数据
    private boolean     m_bNeedSaveFile = false;
    private int         m_nSaveYuvNum = 10;
    private String      m_sSaveileName = "/cap.yuv";
    private String      m_sSaveFilePath = null;
    private CFileManage m_cWriteFd = null;

    private Camera m_cCamera = null;
    //camera参数
    private Camera.Parameters m_cParams = null;
    //camera预览支持的分辨率
    private List<Camera.Size> m_cPreviewSizes = null;
    //camera预览holder
    private SurfaceHolder     m_cHolder = null;

    // 存储采集数据队列,用于编码
    private static final String DATA_QUEUE = "camDataQue";
    private CDataQueue<CRawFrame> m_cYuvDataQueue = null;

    // 如果采集的数据需编码，此标志要设为true
    private boolean              m_bNeedQueue = false;

    private boolean              m_bCreateFlag = false;
    private boolean              m_bStopCapture = false;

    // 采集信息
    private capInfo              m_cCapInfo = null;
    private int                  m_nCapWidth = MAX_CAP_WIDTH;
    private int                  m_nCapHeight = MAX_CAP_HEIGHT;
    private int                  m_nCapFrameRate = EXPEXT_PREVIEW_FPS;
    private int                  m_nCapFormat = ImageFormat.NV21;

    // 采集帧数
    private int               m_nCapFrameNum = 0;

    // 屏幕旋转的方向
    private int               m_nDispRotation = 0;

    // 默认使用后置摄像头
    private int               m_nCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    // 上下文
    private Context           m_cContext = null;
    
    // 下个帧采集的时间
    private long              m_lNextFrameTime = 0;


    public void setContext(@NonNull Context context) {
        m_cContext = context.getApplicationContext();
    }

    /*
    * 打开摄像头及设置参数
    * bNeedQueue: 如需编码设为true
    * */
    public int create(int camId, boolean bNeedQueue){
        Log.d(TAG, "create, need queue: " + bNeedQueue);

        if(m_bCreateFlag){
            Log.d(TAG, "Camera instance has created!");
            return 0;
        }

        m_nCameraId = camId;
        m_bNeedQueue = bNeedQueue;

        // 打开摄像头
        if(openCamera(m_nCameraId) != 0){
            Log.e(TAG, "Camera is null!");
            return -1;
        }

        // 存储采集数据队列
        if (m_bNeedQueue) {
            createQueue();
        }

        m_bCreateFlag = true;

        return 0;
    }

    // 配置采集分辨率及设置SurfaceHolder回调
    public int start(CaptureParam tCapParam){
        Log.d(TAG, "start!");

        if(m_cCamera == null){
            Log.e(TAG, "m_cCamera is null");
            return -1;
        }

        if(tCapParam.nWidth == 0 || tCapParam.nHeight == 0 ||
                tCapParam.nMaxFPS == 0 || tCapParam.cSurfaceHolder == null){
            Log.e(TAG, "Camera Param failed!");
            return -1;
        }

        // 分辨率匹配
        if (matchPreviewSize(tCapParam.nWidth, tCapParam.nHeight) != 0) {
            Log.e(TAG, "m_cCamera Resolution is not matched!");
            return -1;
        }

        m_cCapInfo = new capInfo(tCapParam.nWidth, tCapParam.nHeight, tCapParam.nMaxFPS, ImageFormat.NV21);
        m_nCapWidth  = tCapParam.nWidth;
        m_nCapHeight = tCapParam.nHeight;
        m_nCapFrameRate = tCapParam.nMaxFPS;
        m_nCapFormat = ImageFormat.NV21;

        // 为SurfaceHolder添加回调
        m_cHolder  = tCapParam.cSurfaceHolder;
        //m_cHolder.addCallback(this);

        m_bStopCapture = false;

        // 开启预览
        refreshCamera();

        return 0;
    }

    public void stop(){
        Log.d(TAG, "stop! ");

        m_bCreateFlag = false;
        m_bNeedQueue = false;

        if(m_cCamera != null){
            releaseCamera();
        }

        m_bStopCapture = true;
    }

    public boolean isStopCapture() {
        return m_bStopCapture;
    }

    // stop后，如果需要编码应全部取出数据，再destroy
    public void destroy(){
        Log.d(TAG, "destroy! ");
        clearQueue();
    }

    // 前后摄像头切换
    public int switchCamera(){
        Log.d(TAG, "switchCamera current ID: " + getCameraName());

        if(m_cHolder == null){
            Log.e(TAG, "surfaceHolder is not exist");
            return -1;
        }

        if(m_cCamera == null){
            Log.e(TAG, "m_cCamera is null");
            return -1;
        }

        // 前后置换
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(getCameraId(), info);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            m_nCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else {  // back-facing
            m_nCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }

        // 打开摄像头
        if(openCamera(m_nCameraId) != 0){
            Log.e(TAG, "m_cCamera is null");
            return -1;
        }

        // 开启预览
        refreshCamera();

        return 0;
    }

    public capInfo getCapInfo () {return m_cCapInfo;}

    public int getCameraCount(){
        return Camera.getNumberOfCameras();
    }

    public int getCameraId(){
        return m_nCameraId;
    }

    public void setDispRotation(int dispRotation) {
        m_nDispRotation = dispRotation;
    }

    // 文件
    public void setNeedSaveFile(String fileName, int yuvNum, boolean needSave) {
        m_bNeedSaveFile = needSave;
        m_nSaveYuvNum = yuvNum;
        m_sSaveileName = fileName;

        if (needSave) {
            m_cWriteFd = new CFileManage();
            m_cWriteFd.createSavedFile(fileName);
            m_sSaveFilePath = m_cWriteFd.getSavedFileName();
        }
    }

    public void writeSaveFile(byte[] data, int length) {
        if (m_nSaveYuvNum < 0) {
            return;
        }

        if (m_cWriteFd != null) {
            m_cWriteFd.writeSavedFile(data, length);
            m_nSaveYuvNum--;
        }
    }

    public void closeSaveFile() {
        if (m_cWriteFd != null) {
            m_cWriteFd.closeSavedFile();
            m_cWriteFd = null;
        }
        m_bNeedSaveFile = false;
    }

    // 从队列获取原始采集数据
    public void setCapDataQueue(boolean bNeed) {
        m_bNeedQueue = bNeed;
        if (bNeed) {
            createQueue();
        }
    }

    public void quitDataQueue(){
        if(m_cYuvDataQueue != null){
            m_cYuvDataQueue.quit();
        }
    }

    public CRawFrame getCapFrame() {
        boolean bIsEos = false;
        CRawFrame cRawData = null;

        // 已停止采集
        if (m_bStopCapture) {
            // 全部取出采集数据
            if(!haveCapFrame()) {
                bIsEos = true;
                cRawData = new CRawFrame();
                cRawData.m_bIsEos = bIsEos;
            }
        }

        if (!bIsEos) {
            if (m_cYuvDataQueue != null) {
                cRawData = m_cYuvDataQueue.getData();
            }
        }

        return cRawData;
    }

    // 如果编码，stop后需判断是否还有数据，否则会阻塞
    public boolean haveCapFrame() {
        if (m_cYuvDataQueue == null) {
            return false;
        }

        return m_cYuvDataQueue.isEmpty();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder){
        Log.d(TAG, "Camera surfaceCreated");

        if(m_cCamera == null){
            Log.e(TAG, "m_cCamera is null");
            return;
        }

        //m_cHolder = holder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height){
        Log.d(TAG, "Camera surfaceChanged");
        //m_cHolder = holder;
        //refreshCamera();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder){
        Log.d(TAG, "Camera surfaceDestroyed");
        holder.removeCallback(this);
        releaseCamera();
        m_cHolder = null;
    }

    private  void setCameraDisplayOrientation(int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int degrees = 0;

        switch (m_nDispRotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
        //camera.getParameters().setRotation(result);
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(@NonNull Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    private int openCamera(int cameraId) {
        int ret = -1;

        if (!checkCameraHardware(m_cContext)) {
            Log.e(TAG, "not found camera device!");
            return ret;
        }

        m_nCapFrameNum = 0;
        m_cParams = null;
        m_cPreviewSizes = null;

        // 释放相机资源
        releaseCamera();

        Log.d(TAG, "Camera ID: "+cameraId);

        // 打开相机
        try {
            m_cCamera = Camera.open(cameraId);
        }catch (Exception e){
            Log.e(TAG, "Open camera failed");
            e.printStackTrace();
        }

        ret = getCameraParam();
        if (ret != 0) {
            Log.e(TAG, "camera paramter error!");
            releaseCamera();
            return ret;
        }

        ret = getPreviewSizes();
        if (ret != 0) {
            Log.e(TAG, "camera preview size error!");
            releaseCamera();
            return ret;
        }

        return 0;
    }

    private int getCameraParam() {

        if (m_cCamera == null){
            Log.e(TAG, "m_cCamera instance null");
            return -1;
        }

        try {
            m_cParams = m_cCamera.getParameters();
        }catch (Exception e){
            e.printStackTrace();
            return -1;
        }

        return 0;
    }

    private int getPreviewSizes() {
        if (m_cCamera == null){
            Log.e(TAG, "m_cCamera is null");
            return -1;
        }

        if (m_cParams == null) {
            if (getCameraParam() != 0) {
                Log.e(TAG, "m_cCamera getParameters error!");
                return -1;
            }
        }

        try {
            m_cPreviewSizes = m_cParams.getSupportedPreviewSizes();
        }catch (Exception e){
            e.printStackTrace();
            return -1;
        }

        Camera.Size tSize;
        for (int i = 0; i < m_cPreviewSizes.size(); i++) {
            tSize = m_cPreviewSizes.get(i);
            Log.d(TAG,"cap PreviewSize "+tSize.width+" x "+tSize.height);
        }

        return 0;
    }

    private int matchPreviewSize(int capWidth, int capHeight)
    {
        Camera.Size tSize;
        int i = 0;

        if (m_cPreviewSizes == null) {
            if (getPreviewSizes() != 0) {
                Log.e(TAG, "Get previewSize error!");
                return -1;
            }
        }

        for (i = 0; i < m_cPreviewSizes.size(); i++) {
            tSize = m_cPreviewSizes.get(i);
            if ((tSize.width == capWidth) && (tSize.height == capHeight))  {
                Log.d(TAG,"camera PreviewSize: "+tSize.width+" x "+tSize.height);
                break;
            }
        }

        if (i == m_cPreviewSizes.size()) {
            Log.e(TAG, "camera Resolution "+capWidth+" x "+capHeight+" is not supported!");
            return -1;
        }

        return 0;
    }

    private int setCameraParam(int capWidth, int capHeight, int capFormat) {
        if (m_cCamera == null){
            Log.e(TAG, "m_cCamera is null");
            return -1;
        }

        if (m_cParams == null) {
            if (getCameraParam() != 0) {
                Log.e(TAG, "m_cCamera getParameters error!");
                return -1;
            }
        }

        m_cParams.setPreviewSize(capWidth, capHeight);
        m_cParams.setPreviewFormat(capFormat);
        // 采集帧率
        m_nCapFrameRate  = getFixedPreviewFps(m_cParams, m_nCapFrameRate * 1000)/1000;
        Log.d(TAG, "m_cCamera preview FPS: " + m_nCapFrameRate);

        m_cCamera.setParameters(m_cParams);

        return 0;
    }

    private void releaseCamera() {
        if (m_cCamera != null) {
            m_cCamera.setPreviewCallback(null);
            m_cCamera.stopPreview();// 停掉原来摄像头的预览
            m_cCamera.release();
        }

        m_cCamera = null;
    }

    private void createQueue() {
        clearQueue();

        if (m_bNeedQueue) {
            if (m_cYuvDataQueue == null) {
                m_cYuvDataQueue = new CDataQueue<>();
                m_cYuvDataQueue.create(QUEUE_LENGTH, DATA_QUEUE);
            }
        }
    }

    private void clearQueue() {
        if (m_cYuvDataQueue != null) {
            m_cYuvDataQueue.clear();
            m_cYuvDataQueue = null;
        }
    }

    private long getNextFrameTime() {
        return m_lNextFrameTime;
    }

    private void calcuNextFrameTime(long curTime) {
        m_lNextFrameTime = Math.max(m_lNextFrameTime + 1000000/m_nCapFrameRate, curTime);
    }

    // 设置采集帧率
    private int getFixedPreviewFps(Camera.Parameters parameters, int expFps) {
        List<int[]> supportedFps = parameters.getSupportedPreviewFpsRange();
        for (int[] entry : supportedFps) {
            if (entry[0] == entry[1] && entry[0] == expFps) {
                parameters.setPreviewFpsRange(entry[0], entry[1]);
                return entry[0];
            }
        }
        int[] temp = new int[2];
        int guess;
        parameters.getPreviewFpsRange(temp);
        if (temp[0] == temp[1]) {
            guess = temp[0];
        } else {
            guess = temp[1] / 2;
        }
        return guess;
    }

    private Camera.PreviewCallback capFramePreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            //得到的预览数据
            CRawFrame cYuvFrame = null;

            // 帧率控制--简单控制
            long nextFrameTime = getNextFrameTime();
            // 微秒
            long currentFrameTime = System.nanoTime()/1000;

            Log.d(TAG,"camera capture frame time: " + currentFrameTime +" next time: "+nextFrameTime);

            if (currentFrameTime < nextFrameTime) {
                return;
            }

            m_nCapFrameNum++;

            Log.d(TAG,"camera capture frame num: " + m_nCapFrameNum +", yuv length: " + data.length + ", max length: " + MAX_CAP_YUV_LENGTH);

            // 采集数据入队列
            if (m_bNeedQueue) {
                if(m_cYuvDataQueue != null){
                    cYuvFrame = new CRawFrame();
                    cYuvFrame.m_sFrame = new byte[data.length];

                    System.arraycopy(data, 0,  cYuvFrame.m_sFrame, 0, data.length);

                    cYuvFrame.m_bIsVFlip = false;
                    cYuvFrame.presentationTimeUs = currentFrameTime;

                    if (getCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        cYuvFrame.m_bIsVFlip = true;
                    }

                    m_cYuvDataQueue.setData(cYuvFrame);
                }
            }

            // 更新下一帧获取时间
            calcuNextFrameTime(currentFrameTime);
        }
    };


    private void refreshCamera(){
        if(m_cHolder == null){
            Log.e(TAG, "Preview surfaceView is not exist");
            return;
        }

        if(m_cCamera == null){
            Log.e(TAG, "m_cCamera is null");
            return;
        }

        Log.d(TAG, "Camera refreshCamera");

        try{
            m_lNextFrameTime = System.nanoTime()/1000;
            setCameraParam(m_nCapWidth, m_nCapHeight, m_nCapFormat);
            m_cCamera.setPreviewDisplay(m_cHolder);
            setCameraDisplayOrientation(getCameraId(), m_cCamera);
            m_cCamera.setPreviewCallback(capFramePreviewCallback);
            m_cCamera.startPreview();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private String getCameraName(){
        String camId = "back ";

        if (m_nCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            camId = "front";
        }
        return camId;
    }


    public class capInfo {
        private int               m_nCapWidth = MAX_CAP_WIDTH;
        private int               m_nCapHeight = MAX_CAP_HEIGHT;
        private int               m_nCapFrameRate = EXPEXT_PREVIEW_FPS;
        private int               m_nCapFormat = ImageFormat.NV21;

        capInfo(int capWidth, int capHeight, int capFrameRate, int capFormat) {
            m_nCapWidth = capWidth;
            m_nCapHeight = capHeight;
            m_nCapFrameRate = capFrameRate;
            m_nCapFormat = capFormat;
        }

        public int getCapWidth() {
            return m_nCapWidth;
        }
        public int getCapHeight() {
            return m_nCapHeight;
        }
        public int getCapFrameRate() {
            return m_nCapFrameRate;
        }
        public int getCapFormat() {
            return m_nCapFormat;
        }

        public void setCapWidth(int capWidth) {
            m_nCapWidth = capWidth;
        }
        public void setCapHeight(int capHeight) {
            m_nCapHeight = capHeight;
        }
        public void setCapFrameRate(int capFrameRate) {
            m_nCapFrameRate = capFrameRate;
        }
        public void setCapFormat(int capFormat) {
            m_nCapFormat = capFormat;
        }
    }
}
