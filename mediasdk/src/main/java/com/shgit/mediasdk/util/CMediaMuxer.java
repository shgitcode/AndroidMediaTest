package com.shgit.mediasdk.util;

import androidx.annotation.NonNull;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Vector;

import androidx.annotation.RequiresApi;

/*
* 混合音视频，比如生成MP4
* */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CMediaMuxer {
    private final String TAG = "CMediaMuxer";
    private final int QUEUE_LENGTH = 10;

    // 混合器
    private MediaMuxer m_cMuxer = null;

    private String m_sFilePath = null;

    // 音视频轨
    private Vector  m_cTrackInfo = null;


    public void create(String fileName) {
        m_sFilePath = Environment.getExternalStorageDirectory().getAbsolutePath()  +"/test/"+fileName;

        Log.d(TAG,"create file path: "+m_sFilePath);

        m_cTrackInfo = new Vector();
        m_cTrackInfo.clear();

        //删除已存在的文件
        File file = new File(m_sFilePath);
        if (file.exists()) file.delete();

        try {
            m_cMuxer = new MediaMuxer(m_sFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void release() {
        if (m_cMuxer != null) {
            m_cMuxer.stop();
            m_cMuxer.release();
            m_cMuxer = null;
        }
    }

    // 必须在start前否则无效
    public int addTrack(MediaFormat cFormat) {
        int trackIndex = -1;
        if (m_cMuxer != null) {
            trackIndex = m_cMuxer.addTrack(cFormat);
            if (m_cTrackInfo != null) {
                m_cTrackInfo.addElement(new TrackInfo(trackIndex, cFormat));
            }
        }

        Log.d(TAG,"addTrack: "+trackIndex);

        return trackIndex;
    }

    public void start(){
        if (m_cMuxer != null) {
            m_cMuxer.start();
        }
    }

    public void writeData(int trackIndex, @NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo){
        if (!checkTrackIndex(trackIndex)) {
            return;
        }

        if (m_cMuxer != null) {
            m_cMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
        }
    }

    private boolean checkTrackIndex(int trackIndex) {
        TrackInfo info = null;

        if (m_cTrackInfo != null) {
            for (int i = 0; i < m_cTrackInfo.size(); i++) {
                info = (TrackInfo)m_cTrackInfo.get(i);
                if (info.m_nTrackIndex == trackIndex) {
                    return true;
                }
            }
        }

        return false;
    }

    private class TrackInfo{
        private int m_nTrackIndex;
        private MediaFormat  m_cFormat;

        TrackInfo(int trackIndex, MediaFormat cFormat) {
            m_nTrackIndex = trackIndex;
            m_cFormat = cFormat;
        }
    }
}
