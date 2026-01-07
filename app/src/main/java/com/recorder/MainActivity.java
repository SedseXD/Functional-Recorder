package com.recorder;

import android.app.*;
import android.content.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.Date;

public class MainActivity extends Activity {
    private MediaProjectionManager projectionManager;
    private Spinner fpsSpinner, bitrateSpinner;
    private ToggleButton recordBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // --- 1. Programmatic UI (No XML needed) ---
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("Pro Screen Recorder");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        // FPS Selector
        layout.addView(createLabel("Frame Rate (FPS):"));
        fpsSpinner = new Spinner(this);
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"30", "60", "90"});
        fpsSpinner.setAdapter(fpsAdapter);
        layout.addView(fpsSpinner);

        // Bitrate (Quality) Selector
        layout.addView(createLabel("Quality (Bitrate):"));
        bitrateSpinner = new Spinner(this);
        ArrayAdapter<String> bitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"High (8Mbps)", "Medium (4Mbps)", "Low (1Mbps)"});
        bitrateSpinner.setAdapter(bitAdapter);
        layout.addView(bitrateSpinner);

        // Record Button
        recordBtn = new ToggleButton(this);
        recordBtn.setText("Start Recording");
        recordBtn.setTextOn("Stop Recording");
        recordBtn.setTextOff("Start Recording");
        layout.addView(recordBtn);

        setContentView(layout);

        // --- 2. Logic ---
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        recordBtn.setOnClickListener(v -> {
            if (recordBtn.isChecked()) {
                // User clicked START -> Ask Permission
                startActivityForResult(projectionManager.createScreenCaptureIntent(), 101);
            } else {
                // User clicked STOP
                stopService(new Intent(this, RecorderService.class));
            }
        });
    }

    private TextView createLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(0, 40, 0, 10);
        return tv;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 101 && resultCode == RESULT_OK) {
            // Permission Granted -> Start the Service
            Intent i = new Intent(this, RecorderService.class);
            i.putExtra("code", resultCode);
            i.putExtra("data", data);
            i.putExtra("fps", Integer.parseInt(fpsSpinner.getSelectedItem().toString()));
            
            String quality = bitrateSpinner.getSelectedItem().toString();
            int bitrate = quality.contains("High") ? 8000000 : (quality.contains("Medium") ? 4000000 : 1000000);
            i.putExtra("bitrate", bitrate);
            
            startForegroundService(i);
        } else {
            recordBtn.setChecked(false); // Permission denied
        }
    }

    // --- 3. The Background Service (Keeps recording when app is closed) ---
    public static class RecorderService extends Service {
        private MediaProjection projection;
        private VirtualDisplay virtualDisplay;
        private MediaRecorder mediaRecorder;
        
        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            // Notification to keep Service alive
            String channelId = "RecChannel";
            NotificationChannel channel = new NotificationChannel(channelId, "Recording", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            startForeground(1, new Notification.Builder(this, channelId).setContentTitle("Recording Screen...").setSmallIcon(android.R.drawable.ic_media_play).build());

            int fps = intent.getIntExtra("fps", 30);
            int bitrate = intent.getIntExtra("bitrate", 4000000);
            int width = 720; // Simplified for compatibility
            int height = 1280; 

            try {
                // Setup Recorder
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                mediaRecorder.setVideoEncodingBitRate(bitrate);
                mediaRecorder.setVideoFrameRate(fps);
                mediaRecorder.setVideoSize(width, height);
                
                // Save to Downloads folder
                String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/Rec_" + System.currentTimeMillis() + ".mp4";
                mediaRecorder.setOutputFile(path);
                mediaRecorder.prepare();

                // Start Projection
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = mpm.getMediaProjection(intent.getIntExtra("code", -1), intent.getParcelableExtra("data"));
                
                virtualDisplay = projection.createVirtualDisplay("ScreenRec", width, height, 300, 
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, 
                    mediaRecorder.getSurface(), null, null);
                    
                mediaRecorder.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return START_STICKY;
        }

        @Override
        public void onDestroy() {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
            }
            if (projection != null) projection.stop();
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) { return null; }
    }
}
