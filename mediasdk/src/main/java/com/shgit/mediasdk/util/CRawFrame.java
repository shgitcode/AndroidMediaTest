package com.shgit.mediasdk.util;

public class CRawFrame {
    public byte[] m_sFrame;
    // 结束标志
    public boolean m_bIsEos;
    public long presentationTimeUs;

    //  图像是否镜像
    public boolean m_bIsVFlip;
}
