package edu.kaist.jkih.mscg_speaker_id;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Counter for tests
 *
 * Created by jkih on 2017-05-15.
 */

public class TestStats
{
    public boolean truthIsChild = true;
    private int total = 0;
    private int correctChild = 0;
    private int correctAdult = 0;
    private int totalTruthChild = 0;
    private int totalTruthAdult = 0;
    private int totalDetectChild = 0;
    private int totalDetectNeither = 0;
    private int totalDetectAdult = 0;

    public TestStats()
    {

    }

    public float addEntry(boolean isChild)
    {
        total += 1;
        if (isChild)
        {
            totalDetectChild += 1;
        }
        else
        {
            totalDetectAdult += 1;
        }

        if (truthIsChild)
        {
            totalTruthChild += 1;
        }
        else
        {
            totalTruthAdult += 1;
        }

        return getTotalAccuracy();
    }

    public float getTotalAccuracy()
    {
        return (float)(correctAdult + correctChild) / total;
    }

    public float getTotalAccuracyIgnoreNeither()
    {
        return (float)(correctAdult + correctChild) / (total - totalDetectNeither);
    }

    @Override
    public String toString()
    {
        String retval = "";
        retval += "truthIsChild : " + truthIsChild + "\n";
        retval += "total : " + total + "\n";
        retval += "correctChild : " + correctChild + "\n";
        retval += "correctAdult : " + correctAdult + "\n";
        retval += "totalTruthChild : " + totalTruthChild + "\n";
        retval += "totalTruthAdult : " + totalTruthAdult + "\n";
        retval += "totalDetectChild : " + totalDetectChild + "\n";
        retval += "totalDetectNeither : " + totalDetectNeither + "\n";
        retval += "totalDetectAdult : " + totalDetectAdult + "\n";
        return retval;
    }

    public String saveLog(String path)
    {
        String data = toString();
        try
        {
            // what if overwrite is literally that and doesn't delete the previous file first?
            // the file size will be the same anyway since this is uncompressed
            // also, what if network is slow?

            PrintStream ps = new PrintStream(new FileOutputStream(path, false));
            Log.d("OUT", "Log stats output to " + path);
            ps.println(data);
            ps.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return data;
    }
}
