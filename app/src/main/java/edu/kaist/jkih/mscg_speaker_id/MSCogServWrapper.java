package edu.kaist.jkih.mscg_speaker_id;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by jkih on 2017-05-02.
 */

public class MSCogServWrapper
{
    private int uid = 0;
    private HashMap<UUID, String> aliases = new HashMap<>();
    private List<UUID> serversideProfiles = new ArrayList<>();
    private String apikey;
    private RequestQueue volleyQueue;
    private ArrayList<MSOutputWrapper> requests = new ArrayList<>();
    private boolean ready = false;

    public MSCogServWrapper(String apikeyPath, String aliasPath, Context context)
    {
        try
        {
            char[] charbuff =  new char[32];
            FileInputStream fis = new FileInputStream(apikeyPath);
            for (int i = 0; i < 32; i++)
            {
                charbuff[i] = (char) fis.read();
            }
            fis.close();
            apikey = new String(charbuff);
        } catch (FileNotFoundException e)
        {
            Log.d("ERR", "API key file missing");
            Log.d("ERR", "Should be at: " + apikeyPath);
            e.printStackTrace();
        } catch (IOException e)
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
                aliases.put(UUID.fromString(strarr[0]), strarr[1]);
                Log.d("OUT", "loaded alias " + strarr[0] + ", " + strarr[1]);
                // loop maintenance
                line = br.readLine();
            }
            br.close();
        } catch (FileNotFoundException e)
        {
            Log.d("ERR", "Alias file missing");
            Log.d("ERR", "Should be at: " + aliasPath);
            e.printStackTrace();
        } catch (IOException e)
        {
            Log.d("ERR", "Failed to read alias file");
            e.printStackTrace();
        }

        volleyQueue = Volley.newRequestQueue(context);
        GetAllProfiles();
    }

    private int getUID()
    {
        if (uid + 1 < Integer.MAX_VALUE)
            uid++;
        else
            uid = 0;
        return uid;
    }

    private void GetAllProfiles()
    {
        String url = "https://westus.api.cognitive.microsoft.com/spid/v1.0/identificationProfiles";
        String keyHeader = "Ocp-Apim-Subscription-Key";
        Map<String, String> header = new HashMap<>();
        header.put(keyHeader, apikey);
        HeadierRequest req = new HeadierRequest(Request.Method.GET, url, header, new Response.Listener<JSONObject>()
        {
            @Override
            public void onResponse(JSONObject re)
            {
                if (re == null)
                {
                    return;
                }
                try
                {
                    JSONArray profiles = re.getJSONArray("results");
                    JSONObject iter;
                    for (int i=0; i < profiles.length(); i++)
                    {
                        iter = profiles.getJSONObject(i);
                        serversideProfiles.add(UUID.fromString(iter.getString("identificationProfileId")));
                    }
                } catch (JSONException e)
                {
                    e.printStackTrace();
                }
                ready = true;
            }
        });

        volleyQueue.add(req);
    }

    private int Identification(String path, boolean minimumLengthOverride)
    {
        String url = "https://westus.api.cognitive.microsoft.com/spid/v1.0/identify";
        String keyHeader = "Ocp-Apim-Subscription-Key";
        Map<String, String> header = new HashMap<>();
        Map<String, String> params = new HashMap<>();
        // use this
        header.put(keyHeader, apikey);
        final int id = getUID();
        try
        {
            InputStream is = new FileInputStream(path);
            // get a byte array or sth
        }
        catch (FileNotFoundException e)
        {
            Log.d("ERR", "Cannot find file to ID: " + path);
            e.printStackTrace();
        }
        HeadierRequest req = new HeadierRequest(Request.Method.POST, url, header, params, new Response.Listener<JSONObject>()
        {
            @Override
            public void onResponse(JSONObject re)
            {
                if (re == null)
                {
                    return;
                }
                try
                {
                    int response = re.getInt("responseCode");
                    if (response == 202)
                    {
                        getRequest(id).result = MSOutputWrapper.Result.Processing;
                    }
                    else
                    {
                        getRequest(id).result = MSOutputWrapper.Result.Bad;
                    }
                } catch (JSONException e)
                {
                    e.printStackTrace();
                }
            }
        });
        volleyQueue.add(req);
        MSOutputWrapper output = new MSOutputWrapper(id);
        output.result = MSOutputWrapper.Result.Waiting;
        requests.add(output);
        return id;
    }

    public MSOutputWrapper getRequest(int receipt)
    {
        for (MSOutputWrapper output : requests)
        {
            if (output.getReceipt() == receipt)
                return output;
        }
        return null;
    }

    public int identify(String path, boolean minimumLengthOverride)
    {
        if (!ready)
        {
            Log.d("ERR", "Class not initialized yet");
            return -1;
        }
        return Identification(path, minimumLengthOverride);
    }

    public MSOutputWrapper getProfile(int receipt)
    {
        // attempt to get the result for item with receipt %receipt
        return null;
    }

    public void test()
    {
        for (UUID id : serversideProfiles)
        {
            Log.d("OUT", id.toString());
        }
    }
}
