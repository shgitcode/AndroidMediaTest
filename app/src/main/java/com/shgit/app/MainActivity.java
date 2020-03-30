package com.shgit.app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    static{
        System.loadLibrary("formatConvert");
    }

    private Button cBtnCapPreview;
    private Button cBtnEncDec;
    private Button cBtnAudCapPly;

    private Button cBtnLocVidDec;
    private final String TAG = "MAIN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);                   //全屏显示
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        cBtnCapPreview = findViewById(R.id.cameraPreview);
        cBtnCapPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraPreviewActivity.class);
                startActivity(intent);
            }
        });

        cBtnEncDec = findViewById(R.id.encToDec);
        cBtnEncDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, EncToDecActivity.class);
                startActivity(intent);
            }
        });


        cBtnAudCapPly = findViewById(R.id.audRecordTrack);
        cBtnAudCapPly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AudioRecordAndTrackActivity.class);
                startActivity(intent);
            }
        });

        cBtnLocVidDec = findViewById(R.id.localVidDec);
        cBtnLocVidDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LocalVideoDecActivity.class);
                startActivity(intent);
            }
        });
    }
}
