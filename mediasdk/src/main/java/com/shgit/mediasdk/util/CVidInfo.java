package com.shgit.mediasdk.util;


public class CVidInfo {
    private int               m_nWidth = 1920;
    private int               m_nHeight = 1080;
    private int               m_nFrameRate = 30;
    private int               m_nFormat = 0;

    public CVidInfo(int nWidth, int nHeight, int nFrameRate, int nFormat) {
        m_nWidth = nWidth;
        m_nHeight = nHeight;
        m_nFrameRate = nFrameRate;
        m_nFormat = nFormat;
    }

    public int getWidth() {
        return m_nWidth;
    }
    public int getHeight() {
        return m_nHeight;
    }
    public int getFrameRate() {
        return m_nFrameRate;
    }
    public int getFormat() {
        return m_nFormat;
    }

    public void setWidth(int nWidth) {
        m_nWidth = nWidth;
    }
    public void setHeight(int nHeight) {
        m_nHeight = nHeight;
    }
    public void setFrameRate(int nFrameRate) {
        m_nFrameRate = nFrameRate;
    }
    public void setFormat(int nFormat) {
        m_nFormat = nFormat;
    }
}
