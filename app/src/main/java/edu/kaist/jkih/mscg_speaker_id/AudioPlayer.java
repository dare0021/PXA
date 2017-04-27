package edu.kaist.jkih.mscg_speaker_id;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;

/**
 * Created by jkih on 2017-04-24.
 *
 * testing class to see if the recording function works
 */

public class AudioPlayer
{
    public void play(Activity caller, String path)
    {
        MediaPlayer m = new MediaPlayer();

        m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
            }

        });

        try {

            AssetFileDescriptor descriptor = caller.getApplicationContext().getAssets().openFd(path);
            m.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(),
                    descriptor.getLength());

            descriptor.close();

            m.prepare();
            m.setVolume(100f, 100f);
            m.setLooping(false);
            m.start();

        } catch (Exception e) {
            //Your catch code here.
        }
    }
}