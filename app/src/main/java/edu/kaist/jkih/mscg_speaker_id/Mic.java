package edu.kaist.jkih.mscg_speaker_id;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by jkih on 2017-04-24.
 * should be rebuilt as a service at some point
 */

public class Mic
{
    private static final int SAMPLING_RATE = 16000;
    // requires manual update of WAV header since AudioFormat enums do not coincide with representative numbers. e.g. single channel is enum 16
    private static final int CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SOURCE = MediaRecorder.AudioSource.MIC;
    // 16 bits is 1 channel * 2 bytes * sampling rate * 1 second per update max acceptable latency
    private static final int BUFFER_SIZE = 2 * SAMPLING_RATE;
    // seconds to collect for querying. Querying done very second regardless.
    private static final int UPDATE_INTERVAL = 3;
    // in sec. Note ms response time is 2~5 sec
    // note an interval greater than UPDATE_INTERVAL will lead to discarded audio data
    // MS SDK only supports one transaction at a time
    private static final int UPLOAD_EVERY = 1;

    public boolean previewFileAvailable = false;

    public enum RecordingMode
    {
        // default mode
        CONTINUOUS,
        // unit testing mode
        ONE_OFF,
        // debug mode (save files in storage instead of using volatile cache)
        PERSISTENT
    }
    private RecordingMode recmode = RecordingMode.CONTINUOUS;

    private RecordingThread thread = null;
    private boolean recording = false;
    private byte[][] rec_buff = new byte[UPDATE_INTERVAL][BUFFER_SIZE * UPDATE_INTERVAL];
    private byte[] preview_buff = new byte[BUFFER_SIZE];
    private int rec_buff_head = 0;
    private MainActivity caller;
    private int timeSinceUpload = UPDATE_INTERVAL - 1;
    private String latestFile = "";

    public Mic (MainActivity caller)
    {
        PermissionRequest.request(caller, Manifest.permission.RECORD_AUDIO);
        PermissionRequest.request(caller, Manifest.permission.READ_EXTERNAL_STORAGE);
        PermissionRequest.request(caller, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        PermissionRequest.request(caller, Manifest.permission.WAKE_LOCK);
        PermissionRequest.request(caller, Manifest.permission.INTERNET);
        this.caller = caller;
    }

    public void record()
    {
        recording = true;

        thread = new RecordingThread();
        thread.start();
    }

    public boolean isRecording()
    {
        return recording;
    }

    public void stop()
    {
        recording = false;
        rec_buff_head = 0;
        previewFileAvailable = false;
    }

    /**
     * @return whether successful. e.g. no internet means fail
     */
    private boolean saveFile(String dir, String fileName)
    {
        Log.d("OUT", "saving");
        boolean retval = false;
        if (timeSinceUpload > 0)
        {
            timeSinceUpload--;
            Log.d("OUT", "Upload request denied by upload interval check.");
            Log.d("OUT", "timeSinceUpload at " + timeSinceUpload + " after decrement");
            Log.d("OUT", "UPLOAD_EVERY is " + UPLOAD_EVERY);
            rec_buff_head = (rec_buff_head + 1) % UPDATE_INTERVAL;
            return retval;
        }
        else
        {
            timeSinceUpload = UPLOAD_EVERY;
        }

        byte[] header = new byte[44];
        int totalAudioLen = BUFFER_SIZE * UPDATE_INTERVAL;
        int totalDataLen = totalAudioLen + 36;
        int byteRate = SAMPLING_RATE * 16 / 8;

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) 1; // # of channels
        header[23] = 0;
        header[24] = (byte) (SAMPLING_RATE & 0xff);
        header[25] = (byte) ((SAMPLING_RATE>> 8) & 0xff);
        header[26] = (byte) ((SAMPLING_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLING_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) 2;  // block align = NumChannels * BitsPerSample / 8
        header[33] = 0;
        header[34] = 16;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);


        if (PermissionRequest.internetConnectionAvailable())
        {
            retval = true;
            FileOutputStream fos;
            try
            {
                // what if overwrite is literally that and doesn't delete the previous file first?
                // the file size will be the same anyway since this is uncompressed
                // also, what if network is slow?

                String path = dir + "/" + fileName;
                fos = new FileOutputStream(path, false);
                Log.d("OUT", "File output to " + path);
                fos.write(header, 0, 44);
                Log.d("OUT", "write from cell " + rec_buff_head);
                fos.write(rec_buff[rec_buff_head], 0, totalAudioLen);
                fos.close();
                latestFile = path;
            }
            catch (IOException e)
            {
                e.printStackTrace();
                retval = false;
            }
            // upload head
        }
        // pop head and push empty (conceptually)
        rec_buff_head = (rec_buff_head + 1) % UPDATE_INTERVAL;
        return retval;
    }

    public byte[] getPreview_buff()
    {
        byte[] retval = new byte[BUFFER_SIZE];
        System.arraycopy(preview_buff, 0, retval, 0, BUFFER_SIZE);
        return retval;
    }

    /**
     *
     * @return the latest file to upload as the file's path
     */
    public String getLatestFile()
    {
        String retval = latestFile;
        latestFile = "";
        return retval;
    }

    public RecordingMode getMode()
    {
        return recmode;
    }

    private class RecordingThread extends Thread
    {
        private AudioRecord rec;
        private int read_buff_pointer = 0;

        public RecordingThread()
        {
            this.rec = new AudioRecord(SOURCE, SAMPLING_RATE, CHANNELS, ENCODING, BUFFER_SIZE);
        }

        @Override
        public void run()
        {
            Log.d("OUT", "thread started with mode " + recmode);
            String dir = caller.getCacheDir().toString();
            String fileName = "";
            Log.d("OUT", "initial path @ " + dir + " / " + fileName);
            rec.startRecording();
            switch (recmode)
            {
                case PERSISTENT:
                    // stores files in /storage/emulated/0/
                    // which is the phone's root as far as Windows is concerned
                    dir = Environment.getExternalStorageDirectory().toString();
                    // fall through
                case CONTINUOUS:
                    // check if the other bits work first
                    while (recording)
                    {
                        read_buff_pointer += rec.read(preview_buff, read_buff_pointer, BUFFER_SIZE - read_buff_pointer);
                        if (read_buff_pointer > BUFFER_SIZE) throw new AssertionError("buffer overflow");

                        if (read_buff_pointer >= BUFFER_SIZE)
                        {
                            int xloc;
                            for (byte i = 0; i < UPDATE_INTERVAL; i++)
                            {
                                xloc = (i + rec_buff_head) % UPDATE_INTERVAL;
                                int buff_offset = (UPDATE_INTERVAL - 1 - i) * BUFFER_SIZE;
                                System.arraycopy(preview_buff, 0, rec_buff[xloc], buff_offset, BUFFER_SIZE);
                            }
                            read_buff_pointer = 0;
                            previewFileAvailable = true;
                            // there should be a better way to do this really
                            if (recmode == RecordingMode.CONTINUOUS)
                            {
                                fileName = caller.getApplicationContext().getString(R.string.rectemp_prefix) + rec_buff_head + ".wav";
                            }
                            else if (recmode == RecordingMode.PERSISTENT)
                            {
                                fileName = (new SimpleDateFormat("dd_HH.mm.ss", Locale.getDefault())).format(Calendar.getInstance().getTime()) + ".wav";
                            }
                            saveFile(dir, fileName);
                        }
                    }
                    break;
                case ONE_OFF:
                    while (recording)
                    {
                        read_buff_pointer += rec.read(preview_buff, read_buff_pointer, BUFFER_SIZE - read_buff_pointer);
                        Log.d("OUT", "mic buffer read " + read_buff_pointer);
                        if (read_buff_pointer >= BUFFER_SIZE)
                        {
                            System.arraycopy(preview_buff, 0, rec_buff[0], rec_buff_head * BUFFER_SIZE, BUFFER_SIZE);
                            read_buff_pointer = 0;
                            rec_buff_head++;
                            if (rec_buff_head >= UPDATE_INTERVAL)
                            {
                                // dont read the wrong cell
                                rec_buff_head = 0;
                                saveFile(Environment.getExternalStorageDirectory().toString(), "temp.wav");
                                Mic.this.stop();
                            }
                        }
                    }
                    break;
                default:
                    throw new AssertionError("Not Implemented Yet");
            }
            rec.stop();
        }
    }
}
