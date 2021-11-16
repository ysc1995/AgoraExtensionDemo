/* Copyright (C) Voicemod S.L. - All Rights Reserved
 * Unauthorized copying of this file, via any mean is strictly prohibited
 * This file is proprietary and confidential, and is subject to the legal notices
 * in its containing repository and at https://voicemod.net/copyright
 * For further information please email: copyright@voicemod.net
 */

package net.voicemod.agorapluginexample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.voicemod.agoraplugin.ExtensionManager;
import net.voicemod.agoraplugin.ResourceHelper;
import net.voicemod.agoraplugin.UtilsAsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import agoramarketplace.bytedance.labcv.R;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public class MainActivity extends AppCompatActivity implements UtilsAsyncTask.OnUtilsAsyncTaskEvents, io.agora.rtc2.IMediaExtensionObserver {

    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };

    private static final String appId = AppConf.appId;
    private static final String token = AppConf.token;
    private final static String TAG = AppConf.TAG;
    private final static String channelName = AppConf.channelName;
    private final static String apiKey = AppConf.apiKey;
    private final static String apiSecret = AppConf.apiSecret;
    private static final int PERMISSION_REQ_ID = 22;
    private SurfaceView mRemoteView;
    private FrameLayout localVideoContainer;
    private FrameLayout remoteVideoContainer;
    private RtcEngine mRtcEngine;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
        checkPermission();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        mRtcEngine.leaveChannel();
        mRtcEngine.destroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermission() {
        Log.d(TAG, "checkPermission");
        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) && checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID)) {
            initAgoraEngine();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_ID && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED){
            initAgoraEngine();
        }
    }

    private void initUI() {
        localVideoContainer = findViewById(R.id.view_container);
        remoteVideoContainer = findViewById(R.id.remote_video_view_container);

        configureVoiceButtons();
    }


    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }
        return true;
    }

    private void initAgoraEngine() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = this;
            config.mAppId = appId;

            //Name of dynamic link library is provided by plug-in vendor,
            //and one or more plug-ins can be added

            config.addExtension(ExtensionManager.EXTENSION_NAME);
            config.mExtensionObserver = this;
            config.mEventHandler = new IRtcEngineEventHandler() {
                @Override
                public void onJoinChannelSuccess(String s, int i, int i1) {
                    Log.d(TAG, "onJoinChannelSuccess");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRtcEngine.startPreview();
                        }
                    });
                }

                @Override
                public void onFirstRemoteVideoDecoded(final int i, int i1, int i2, int i3) {
                    super.onFirstRemoteVideoDecoded(i, i1, i2, i3);
                    Log.d(TAG, "onFirstRemoteVideoDecoded  uid = " + i);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setupRemoteVideo(i);
                        }
                    });
                }

                @Override
                public void onUserJoined(int i, int i1) {
                    super.onUserJoined(i, i1);
                    Log.d(TAG, "onUserJoined  uid = " + i);
                }

                @Override
                public void onUserOffline(final int i, int i1) {
                    super.onUserOffline(i, i1);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onRemoteUserLeft();
                        }
                    });
                }
            };
            mRtcEngine = RtcEngine.create(config);
            //extension is enabled by default
            mRtcEngine.enableExtension(ExtensionManager.EXTENSION_VENDOR_NAME, ExtensionManager.EXTENSION_AUDIO_FILTER_NAME, true);
            setupLocalVideo();
            VideoEncoderConfiguration configuration = new VideoEncoderConfiguration(640, 360,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE);
            mRtcEngine.setVideoEncoderConfiguration(configuration);
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
            mRtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
            mRtcEngine.enableLocalVideo(true);
            mRtcEngine.enableVideo();
            mRtcEngine.enableAudio();
            String uData = prepareUserData();
            mRtcEngine.setExtensionProperty(ExtensionManager.EXTENSION_VENDOR_NAME, ExtensionManager.EXTENSION_AUDIO_FILTER_NAME, "vcmd_user_data", uData);
            Log.d(TAG, "api call join channel");
            mRtcEngine.joinChannel(token, channelName, "", 0);
            mRtcEngine.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String prepareUserData() {
        JSONObject json = new JSONObject();
        try {
            json.put("apiKey", apiKey);
            json.put("apiSecret", apiSecret);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String result = json.toString();
        return result;
    }




    @Override
    public void onPreExecute() {

    }

    @Override
    public void onPostExecute() {
        ResourceHelper.setResourceReady(this, true, 1);
        Toast.makeText(this, "copy resource Ready", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onEvent(String vendor, String extension, String key, String value) {
    }

    private void configureVoiceButtons() {

        Button caveButton = findViewById(R.id.cave_button);
        caveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRtcEngine.setExtensionProperty(ExtensionManager.EXTENSION_VENDOR_NAME, ExtensionManager.EXTENSION_AUDIO_FILTER_NAME, "vcmd_voice", "\"cave\"");
            }
        });


        Button robotButton = findViewById(R.id.robot_button);
        robotButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                mRtcEngine.setExtensionProperty(ExtensionManager.EXTENSION_VENDOR_NAME, ExtensionManager.EXTENSION_AUDIO_FILTER_NAME, "vcmd_voice", "\"robot\"");
            }
        });

    }


    private void setupLocalVideo() {
        SurfaceView view = RtcEngine.CreateRendererView(this);
        view.setZOrderMediaOverlay(true);
        localVideoContainer.addView(view);
        mRtcEngine.setupLocalVideo(new VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0));
        mRtcEngine.setLocalRenderMode(Constants.RENDER_MODE_HIDDEN);
    }

    private void setupRemoteVideo(int uid) {
        // Only one remote video view is available for this
        // tutorial. Here we check if there exists a surface
        // view tagged as this uid.
        int count = remoteVideoContainer.getChildCount();
        View view = null;
        for (int i = 0; i < count; i++) {
            View v = remoteVideoContainer.getChildAt(i);
            if (v.getTag() instanceof Integer && ((int) v.getTag()) == uid) {
                view = v;
            }
        }

        if (view != null) {
            return;
        }

        Log.d(TAG, " setupRemoteVideo uid = " + uid);
        mRemoteView = RtcEngine.CreateRendererView(getBaseContext());
        remoteVideoContainer.addView(mRemoteView);
        mRtcEngine.setupRemoteVideo(new VideoCanvas(mRemoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
        mRtcEngine.setRemoteRenderMode(uid, Constants.RENDER_MODE_HIDDEN);
        mRemoteView.setTag(uid);
    }

    private void onRemoteUserLeft() {
        removeRemoteVideo();
    }

    private void removeRemoteVideo() {
        if (mRemoteView != null) {
            remoteVideoContainer.removeView(mRemoteView);
        }
        mRemoteView = null;
    }
}
