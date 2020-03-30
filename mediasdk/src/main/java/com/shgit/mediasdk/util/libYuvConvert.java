package com.shgit.mediasdk.util;

/*
*  NV21 ==> NV12
*  NV21 ==> I420
*  支持左右镜像
* */
public class libYuvConvert {
    /**
     * YUV数据的基本的处理（nv21-->nv12）
     *
     * @param nv21    原始数据
     * @param width   原始的宽
     * @param height  原始的高
     * @param nv12    目标数据
     * @param isvFlip   是否镜像(前置摄像头)
     */
    public static native void yuvNV21ToNV12_vFlip(byte[] nv21, int width, int height, byte[] nv12, boolean isvFlip);

    /**
     * YUV数据的基本的处理（nv21-->i420）
     *
     * @param nv21    原始数据
     * @param width   原始的宽
     * @param height  原始的高
     * @param i420    目标数据
     * @param isvFlip   是否镜像(前置摄像头)
     */
    public static native void yuvNV21ToI420_vFlip(byte[] nv21, int width, int height, byte[] i420, boolean isvFlip);
}
