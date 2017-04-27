package edu.kaist.jkih.mscg_speaker_id;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity
{
    Mic mic = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mic = new Mic(this);
    }

    public void record(View view)
    {
        Button btn = (Button) findViewById(R.id.button);
        btn.setText("RECORDING");
        Mic mic = new Mic(this);
        Log.d("OUT", "Attempt recording");
        mic.record();
        btn.setEnabled(false);
    }

    public void stop()
    {
        findViewById(R.id.button).setEnabled(true);
        ((Button)findViewById(R.id.button)).setText("● REC");
    }
}
