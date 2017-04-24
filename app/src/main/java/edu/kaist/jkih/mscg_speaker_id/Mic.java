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

/**
 * Created by jkih on 2017-04-24.
 */

public class Mic
{
    private static final int SAMPLING_RATE = 16000;
    private static final int CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SOURCE = MediaRecorder.AudioSource.MIC;
    // 16 bits is 1 channel * 2 bytes * sampling rate * 1 second per update max acceptable latency
    private static final int BUFFER_SIZE = 2 * SAMPLING_RATE;
    // seconds to collect for querying. Querying done very second regardless.
    private static final int UPDATE_INTERVAL = 3;

    private RecordingThread thread = null;
    private boolean recording = false;
    private byte[][] rec_buff = new byte[UPDATE_INTERVAL][BUFFER_SIZE * UPDATE_INTERVAL];
    private byte rec_buff_head = 0;
    private int rec_buff_pointer = 0;

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
        recording = true;

        String path = Environment.getExternalStorageDirectory().getPath() + "/fakepath.pcm";
        // logic for an actual path instead of fakepath

        // if we leave as is OS might garbage collect this before the 3 secs is up
        thread = new RecordingThread();

        // some testing code here, return false if fail
        return true;
    }

    public void stop()
    {
        recording = false;
    }

    private void saveFile()
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
        // pop head and push empty
        // upload head
    }

    private class RecordingThread extends Thread
    {
        private AudioRecord rec;
        private byte[] read_buff = new byte[BUFFER_SIZE];
        private int read_buff_pointer = 0;

        public RecordingThread()
        {
            this.rec = new AudioRecord(SOURCE, SAMPLING_RATE, CHANNELS, ENCODING, BUFFER_SIZE);
        }

        @Override
        public void run()
        {
            while(recording)
            {
                read_buff_pointer += rec.read(read_buff, read_buff_pointer, BUFFER_SIZE - read_buff_pointer);
                assert read_buff_pointer <= BUFFER_SIZE;

                if (read_buff_pointer >= BUFFER_SIZE)
                {
                    for (byte i = 0; i < UPDATE_INTERVAL; i++)
                    {
                        int buff_offset = (UPDATE_INTERVAL - 1 - i) * BUFFER_SIZE;
                        System.arraycopy(read_buff, 0, rec_buff[i], buff_offset, BUFFER_SIZE);
                    }
                    read_buff_pointer = 0;
                    saveFile();
                }
            }
        }
    }
}
