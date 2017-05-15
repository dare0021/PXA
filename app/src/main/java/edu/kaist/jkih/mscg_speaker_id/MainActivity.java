package edu.kaist.jkih.mscg_speaker_id;

import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import static android.os.Environment.getExternalStorageDirectory;

public class MainActivity extends AppCompatActivity
{
    // how often to check if net io is finished. In ms.
    private static final int UPLOAD_CHECK_INTERVAL = 50;

    private boolean playAudioFiles = false;
    private boolean uploadFiles = false;

    Mic mic = null;
    AudioPlayer ap = null;
    MSCogServWrapper ms = null;
    TestStats ts = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mic = new Mic(this);
        ap = new AudioPlayer(this.getApplicationContext());
        ms = new MSCogServWrapper(getExternalStorageDirectory().toString() + getString(R.string.apikey_file),
                                    getExternalStorageDirectory().toString() + getString(R.string.alias_file));
        resetStats(null);
    }

    /** <pre>
     * Do not add additional logic to this function
     * Will break compatibility for Mic's other modes
     */
    public void recBtn(View view)
    {
        if (mic.isRecording())
        {
            stop();
        }
        else
        {
            record();
        }
    }

    public void record()
    {
        Log.d("OUT", "Attempt recording");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mic.record();
        new RecordingProgress().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mic);
    }

    public void play(View view)
    {
        switch (mic.getMode())
        {
            case ONE_OFF:
                ap.play(getExternalStorageDirectory() + "/temp.wav");
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
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mic.stop();
        ((Button)findViewById(R.id.button)).setText("● REC");
    }

    public void upload(String path)
    {
        int receipt = ms.identify(path, true);
        if (receipt >= 0)
        {
            new WaitForUpload().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, receipt);
        }
    }

    /**
     * Debugging code
     */
    public void uploadTest(View view)
    {
        if (view.getId() == R.id.buttonUpload1)
            upload(getExternalStorageDirectory() + "/test1.wav");
        else if (view.getId() == R.id.buttonUpload2)
            upload(getExternalStorageDirectory() + "/test2.wav");
    }

    /**
     * Debugging code
     */
    public void toggleAutoUpload(View view)
    {
        uploadFiles = !uploadFiles;
        Toast.makeText(this, "uploadFiles = " + uploadFiles, Toast.LENGTH_SHORT).show();
    }

    public void toggleTruthValue(View view)
    {
        ts.truthIsChild = !ts.truthIsChild;
        String truth = "Truth: ";
        if (ts.truthIsChild)
        {
            truth += "Child";
        }
        else
        {
            truth += "Adult";
        }
        ((Button)view).setText(truth);
    }

    public void saveLogFile(View view)
    {
        ts.saveLog(getExternalStorageDirectory().toString() + "/mscogserv/teststats.log");
    }

    /**
     * Constructor calls resetStats(null)
     * Rewrite if the code begins using the view argument
     */
    public void resetStats(View view)
    {
        ts = new TestStats();
    }

    private class WaitForUpload extends AsyncTask<Integer, Integer, MSOutputWrapper>
    {
        @Override
        protected MSOutputWrapper doInBackground(Integer ... ints)
        {
            int receipt = ints[0];
            MSOutputWrapper result = null;
            while (result == null)
            {
                try
                {
                    Thread.sleep(UPLOAD_CHECK_INTERVAL);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                result = ms.getProfile(receipt);
            }
            return result;
        }

        @Override
        protected void onPostExecute(MSOutputWrapper result)
        {
            int fg = Color.GRAY;
            // thread closed by user action
            if (result == null)
            {
                return;
            }
            // Failure to ID on MS side
            else if (result.result != MSOutputWrapper.Result.Good || result.alias == null)
            {
                ts.addEntry(TestStats.Result.Neither);

                String outval = "";
                outval += "receipt: " + result.receipt + "\n";
                outval += "Message: " + result.alias + "\n";
                ((TextView)findViewById(R.id.textview)).setText(outval);
                ((TextView)findViewById(R.id.textview)).setTextColor(fg);
                return;
            }
            if (result.alias.endsWith("C"))
            {
                ts.addEntry(TestStats.Result.Child);
                fg = Color.BLUE;
            }
            else if (result.alias.endsWith("M"))
            {
                ts.addEntry(TestStats.Result.Adult);
                fg = Color.RED;
            }
            String outval = "";
            outval += "receipt: " + result.receipt + "\n";
            outval += "UID: " + result.id + "\n";
            outval += "Alias: " + result.alias + "\n";
            outval += "Confidence: " + result.confidence;
            ((TextView)findViewById(R.id.textview)).setText(outval);
            ((TextView)findViewById(R.id.textview)).setTextColor(fg);
        }
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

                if (uploadFiles == true)
                {
                    String path = mic.getLatestFile();
                    if (path.length() > 0)
                    {
                        upload(path);
                    }
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

        }
    }
}
