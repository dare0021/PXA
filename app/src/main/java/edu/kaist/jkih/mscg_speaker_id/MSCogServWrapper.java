package edu.kaist.jkih.mscg_speaker_id;

import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import com.microsoft.cognitive.speakerrecognition.SpeakerIdentificationRestClient;

/**
 * Created by jkih on 2017-05-02.
 */

public class MSCogServWrapper
{
    private SpeakerIdentificationRestClient msWrapper;
    private ArrayList<Pair<UUID, String>> aliases = new ArrayList<>();

    public MSCogServWrapper(String apikeyPath, String aliasPath)
    {
        char[] apikey = new char[32];
        try
        {
            FileInputStream fis = new FileInputStream(apikeyPath);
            for (int i=0; i<32; i++)
            {
                apikey[i] = (char)fis.read();
            }
            fis.close();
        }
        catch (FileNotFoundException e)
        {
            Log.d("ERR", "API key file missing");
            Log.d("ERR", "Should be at: " + apikeyPath);
            e.printStackTrace();
        }
        catch (IOException e)
        {
            Log.d("ERR", "Failed to read API key file");
            e.printStackTrace();
        }

        try
        {
            BufferedReader br = new BufferedReader(new FileReader(aliasPath));
            String line = br.readLine();
            while (line != null && line.length() > 37) // guid length + tab separator
            {
                String[] strarr = line.split("\t");
                aliases.add(new Pair<>(UUID.fromString(strarr[0]), strarr[1]));
                Log.d("OUT", "loaded alias " + strarr[0] + ", " + strarr[1]);
                // loop maintenance
                line = br.readLine();
            }
            br.close();
        }
        catch (FileNotFoundException e)
        {
            Log.d("ERR", "Alias file missing");
            Log.d("ERR", "Should be at: " + aliasPath);
            e.printStackTrace();
        }
        catch (IOException e)
        {
            Log.d("ERR", "Failed to read alias file");
            e.printStackTrace();
        }

        msWrapper = new SpeakerIdentificationRestClient(apikey.toString());
    }
}
