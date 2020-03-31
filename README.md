# AndroidMediaTest
use Android Media library:Mediacodec/Camera/AudioTrack and so on to create Media demo
# four examples
  1. CameraPreviewActivity
use camera to preview video and can set button to encode video  captured by camera to h264
预览相机Camera采集的数据
通过按钮事件触发采集数据编码(H264)
  2.EncToDecActivity
实时编码相机采集数据（存入队列）；
实时从队列解码视频数据并显示。
  3.LocalVideoDecActivity
本地解码实现画中画（PIP）
  4.AudioRecordAndTrackActivity
音频采集生成PCM文件；
音频采集编码生成AAC文件；
音频直接PCM播放；
音频AAC解码播放。