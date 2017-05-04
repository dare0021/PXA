package edu.kaist.jkih.mscg_speaker_id;

import android.os.AsyncTask;

/**
 * What if I dont want to publish progress?
 * Why not just use Thread?
 * Thread forks the process, meaning using it for one-off situations result in performance hits
 * ...theoretically
 */

public abstract class AsyncNoUpdate<T> extends AsyncTask<T, Integer, Integer>
{
    @Override
    protected void onProgressUpdate(Integer ... ints)
    {

    }

    @Override
    protected void onPostExecute(Integer i)
    {

    }
}
