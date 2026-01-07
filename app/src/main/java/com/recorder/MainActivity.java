package com.recorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.FileDescriptor;
import java.util.*;

public class MainActivity extends Activity {
    private MediaProjectionManager projectionManager;
    private Spinner fpsSpinner, bitrateSpinner;
    private ToggleButton recordBtn;
    private static final int PERMISSION_CODE = 101;
    private static final int SCREEN_RECORD_REQUEST_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // --- UI Setup ---
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("Pro Screen Recorder");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        layout.addView(createLabel("Frame Rate (FPS):"));
        fpsSpinner = new Spinner(this);
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"30", "60"});
        fpsSpinner.setAdapter(fpsAdapter);
        layout.addView(fpsSpinner);

        layout.addView(createLabel("Quality:"));
        bitrateSpinner = new Spinner(this);
        ArrayAdapter<String> bitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"High", "Medium", "Low"});
        bitrateSpinner.setAdapter(bitAdapter);
        layout.addView(bitrateSpinner);

        recordBtn = new ToggleButton(this);
        recordBtn.setText("Start Recording");
        recordBtn.setTextOn("Stop Recording");
        recordBtn.setTextOff("Start Recording");
        layout.addView(recordBtn);

        setContentView(layout);

        // --- Logic ---
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        checkPermissions();

        recordBtn.setOnClickListener(v -> {
            if (recordBtn.isChecked()) {
                startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_RECORD_REQUEST_CODE);
            } else {
                stopService(new Intent(this, RecorderService.class));
                Toast.makeText(this, "Recording Saved to Movies!", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkPermissions() {
        List<String> list = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!list.isEmpty()) {
            requestPermissions(list.toArray(new String[0]), PERMISSION_CODE);
        }
    }

    private TextView createLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(0, 40, 0, 10);
        return tv;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCREEN_RECORD_REQUEST_CODE && resultCode == RESULT_OK) {
            Intent i = new Intent(this, RecorderService.class);
            i.putExtra("code", resultCode);
            i.putExtra("data", data);
            i.putExtra("fps", Integer.parseInt(fpsSpinner.getSelectedItem().toString()));
            String qual = bitrateSpinner.getSelectedItem().toString();
            i.putExtra("bitrate", qual.equals("High") ? 8000000 : (qual.equals("Medium") ? 4000000 : 1500000));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } else {
            recordBtn.setChecked(false);
        }
    }

    // --- Background Service ---
    public static class RecorderService extends Service {
        private MediaProjection projection;
        private VirtualDisplay virtualDisplay;
        private MediaRecorder mediaRecorder;
        
        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            createNotificationChannel();
            startForeground(1, new Notification.Builder(this, "RecChannel")
                    .setContentTitle("Recording Screen")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .build());

            if (intent == null) return START_NOT_STICKY;

            int fps = intent.getIntExtra("fps", 30);
            int bitrate = intent.getIntExtra("bitrate", 4000000);

            try {
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                mediaRecorder.setVideoEncodingBitRate(bitrate);
                mediaRecorder.setVideoFrameRate(fps);
                mediaRecorder.setVideoSize(720, 1280); 

                // --- MediaStore Saving ---
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, "Rec_" + System.currentTimeMillis() + ".mp4");
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecorder");
                
                Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
                    mediaRecorder.setOutputFile(pfd.getFileDescriptor());
                }
                
                mediaRecorder.prepare();

                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = mpm.getMediaProjection(intent.getIntExtra("code", -1), intent.getParcelableExtra("data"));
                
                virtualDisplay = projection.createVirtualDisplay("ScreenRec", 720, 1280, 300, 
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, 
                    mediaRecorder.getSurface(), null, null);
                    
                mediaRecorder.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return START_STICKY;
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel("RecChannel", "Recording", NotificationManager.IMPORTANCE_LOW);
                getSystemService(NotificationManager.class).createNotificationChannel(channel);
            }
        }

        @Override
        public void onDestroy() {
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.stop();
                    mediaRecorder.reset();
                }
                if (projection != null) projection.stop();
            } catch(Exception e) {}
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) { return null; }
    }
}
