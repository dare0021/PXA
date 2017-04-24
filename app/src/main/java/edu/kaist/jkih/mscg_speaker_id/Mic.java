package edu.kaist.jkih.mscg_speaker_id;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by jkih on 2017-04-24.
 */

public class Mic
{
    private static final int SAMPLING_RATE = 16000;
    private static final int CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SOURCE = MediaRecorder.AudioSource.MIC;
    // 16 bits is 2 bytes * sampling rate * 3 seconds
    private static final int BUFFER_SIZE = 2 * SAMPLING_RATE * 3;

    public Mic (Activity caller)
    {
        PermissionRequest.request(caller, Manifest.permission.RECORD_AUDIO);
        PermissionRequest.request(caller, Manifest.permission.READ_EXTERNAL_STORAGE);
        PermissionRequest.request(caller, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        PermissionRequest.request(caller, Manifest.permission.WAKE_LOCK);
        PermissionRequest.request(caller, Manifest.permission.INTERNET);
    }

    public boolean record()
    {
        String path = Environment.getExternalStorageDirectory().getPath() + "/fakepath.pcm";
        // logic for an actual path instead of fakepath

        // if we leave as is OS might garbage collect this before the 3 secs is up
        Thread t = new RecordingThread(path, newRec());

        // some testing code here, return false if fail
        return true;
    }

    private AudioRecord newRec()
    {
        return new AudioRecord(SOURCE, SAMPLING_RATE, CHANNELS, ENCODING, BUFFER_SIZE);
    }

    private class RecordingThread extends Thread
    {
        private String path;
        private AudioRecord rec;

        public RecordingThread(String savePath, AudioRecord rec)
        {
            path = savePath;
            this.rec = rec;
        }

        @Override
        public void run()
        {
            FileOutputStream fos = null;
            try
            {
                // what if overwrite is literally that and doesn't delete the previous file first?
                // the file size will be the same anyway since this is uncompressed
                fos = new FileOutputStream(path, false);
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }

        }
    }
}
