package edu.kaist.jkih.mscg_speaker_id;

import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity
{
    private boolean playAudioFiles = true;

    Mic mic = null;
    AudioPlayer ap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mic = new Mic(this);
        ap = new AudioPlayer(this.getApplicationContext());
    }

    public void record(View view)
    {
        Button btn = (Button) findViewById(R.id.button);
        Mic mic = new Mic(this);
        Log.d("OUT", "Attempt recording");
        mic.record();
        btn.setEnabled(false);
        new RecordingProgress().execute(mic);
    }

    public void play(View view)
    {
        switch (mic.getMode())
        {
            case ONE_OFF:
                ap.play(Environment.getExternalStorageDirectory() + "/temp.wav");
                break;
            case PERSISTENT:
                //fall through
            case CONTINUOUS:
                playAudioFiles = !playAudioFiles;
                Toast.makeText(this, "playAudioFiles = " + playAudioFiles, Toast.LENGTH_SHORT).show();
                break;
            default:
                throw new AssertionError("Not Implemented");
        }
    }

    public void stop()
    {
        findViewById(R.id.button).setEnabled(true);
        ((Button)findViewById(R.id.button)).setText("● REC");
    }

    /**
     * Animates the record button label while recording
     * Also plays the audio during debug
     */
    // do not remove mic being relayed explicitly
    // breaks AudioRecord for some reason
    private class RecordingProgress extends AsyncTask<Mic, Integer, Integer>
    {
        private String prefix = "Recoding";
        private int maxPostfixes = 3;

        @Override
        protected Integer doInBackground(Mic ... mics)
        {
            mic = mics[0];
            int recCell = 0;
            while (mic.isRecording())
            {
                publishProgress(recCell);
                if(playAudioFiles && mic.previewFileAvailable)
                {
                    ap.playPCM(mic.getPreview_buff());
                    mic.previewFileAvailable = false;
                }
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                    return 0;
                }

                recCell++;
                recCell %= maxPostfixes + 1;
            }
            return 1;
        }

        @Override
        protected void onProgressUpdate(Integer ... ints)
        {
            int i = ints[0];
            int iter;
            String text = prefix;
            for (iter = 0; iter < i; iter++)
            {
                text += ".";
            }
            for (; iter < maxPostfixes; iter++)
            {
                text += " ";
            }
            ((Button)findViewById(R.id.button)).setText(text);
        }

        @Override
        protected void onPostExecute(Integer i)
        {
            MainActivity.this.stop();
        }
    }
}
