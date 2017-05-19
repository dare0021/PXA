package edu.kaist.jkih.mscg_speaker_id;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.microsoft.cognitive.speakerrecognition.SpeakerIdentificationRestClient;
import com.microsoft.cognitive.speakerrecognition.contract.GetProfileException;
import com.microsoft.cognitive.speakerrecognition.contract.identification.IdentificationException;
import com.microsoft.cognitive.speakerrecognition.contract.identification.IdentificationOperation;
import com.microsoft.cognitive.speakerrecognition.contract.identification.OperationLocation;
import com.microsoft.cognitive.speakerrecognition.contract.identification.Profile;

import static edu.kaist.jkih.mscg_speaker_id.MSOutputWrapper.Result.Bad;
import static edu.kaist.jkih.mscg_speaker_id.MSOutputWrapper.Result.Good;

/**
 * Created by jkih on 2017-05-02.
 */

public class MSCogServWrapper
{
    private static final int NUM_RETRIES = 0;
    // in ms
    private static final int RETRY_DELAY = 500;

    // do not use directly lest ye tempt the gods of thread safety
    private HashMap<Integer, IdentificationOperation> readyResults = new HashMap<>();
    private int uid = 0;
    private SpeakerIdentificationRestClient msWrapper;
    private HashMap<UUID, String> aliases = new HashMap<>();
    private List<Profile> profiles;
    private AsyncTask idThread = null;

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
                aliases.put(UUID.fromString(strarr[0]), strarr[1]);
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

        msWrapper = new SpeakerIdentificationRestClient(new String(apikey));
        update();
    }

    public void update()
    {
        new UpdateAllSpeakers().execute(msWrapper);
    }

    /**
     * Pops and returns the desired profile
     * @param receipt
     * @return null when unavailable
     */
    public synchronized MSOutputWrapper getProfile(int receipt)
    {
        IdentificationOperation result = readyResults.get(receipt);
        if (result != null)
        {
            readyResults.remove(receipt);
            MSOutputWrapper retval = new MSOutputWrapper();
            retval.receipt = receipt;
            if (result.processingResult != null)
            {
                retval.result = Good;
                retval.id = result.processingResult.identifiedProfileId;
                retval.confidence = result.processingResult.confidence.toString();
                retval.alias = aliases.get(retval.id);
            }
            else
            {
                retval.alias = result.message;
                retval.result = Bad;
            }
            return retval;
        }
        return null;
    }

    private synchronized void pushNewResult(int receipt, IdentificationOperation profile)
    {
        readyResults.put(receipt, profile);
    }

    /**
     * @param filePath audio file to upload for identification
     * @param isShort audio file is < 30 sec short
     * @return receipt number
     */
    public int identify(String filePath, boolean isShort)
    {
        int receipt = getUID();

        if (idThread != null && idThread.getStatus() != AsyncTask.Status.FINISHED)
        {
            Log.d("OUT", "Thread state: " + idThread.getStatus());
            Log.d("OUT", "Existing concurrent request. Request denied.");
            return receipt * -1;
        }
        Log.d("OUT", "Request accepted");

        Object[] args = new Object[4];
        args[0] = msWrapper;
        args[1] = filePath;
        args[2] = isShort;
        args[3] = receipt;
        idThread = new Identify().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, args);
//        MS SDK only allows 1 concurrent connection and throws an unhandled exception that kills the session otherwise
//        new Identify().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, args);
        return receipt;
    }

    private class Identify extends AsyncTask<Object, Integer, IdentificationOperation>
    {
        private int receipt;

        /**<pre>
         * Arguments:
         * 0) SpeakerIdentificationRestClient
         * 1) String filePath
         * 2) boolean isShort
         * 3) int receipt
         */
        @Override
        protected IdentificationOperation doInBackground(Object ... args)
        {
            SpeakerIdentificationRestClient client = (SpeakerIdentificationRestClient) args[0];
            String filePath = (String) args[1];
            boolean isShort = (boolean) args[2];
            receipt = (int) args[3];

            List<UUID> ids = new ArrayList<>();
            for (Profile profile : profiles)
            {
                ids.add(profile.identificationProfileId);
            }

            IdentificationOperation result = null;
            try
            {
                OperationLocation processPollingLocation;
                InputStream is = new FileInputStream(filePath);
                processPollingLocation = client.identify(is, ids, isShort);
                is.close();

                int retries = 0;
                while (true)
                {
                    result = client.checkIdentificationStatus(processPollingLocation);

                    if (result.status == com.microsoft.cognitive.speakerrecognition.contract.identification.Status.SUCCEEDED)
                    {
                        break;
                    }
                    else if (result.status == com.microsoft.cognitive.speakerrecognition.contract.identification.Status.FAILED
                            || retries >= NUM_RETRIES)
                    {
                        throw new IdentificationException((result.message));
                    }

                    retries++;
                    publishProgress(retries);
                    try
                    {
                        Thread.sleep(RETRY_DELAY);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
                if (retries >= NUM_RETRIES)
                {
                    Log.d("ERR", "server timeout");
//                    throw new IdentificationException("Server timeout");
                }
                Log.d("OUT", "Identification done");
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            catch (IdentificationException e)
            {
                e.printStackTrace();
            }

            return result;
        }

        @Override
        protected void onProgressUpdate(Integer ... ints)
        {
            int delayNum = ints[0];
            Log.d("API", "No response, attempt " + (delayNum + 1));
        }

        @Override
        protected void onPostExecute(IdentificationOperation result)
        {
            pushNewResult(receipt, result);
        }
    }

    private class UpdateAllSpeakers extends AsyncNoUpdate<SpeakerIdentificationRestClient>
    {
        @Override
        protected Integer doInBackground(SpeakerIdentificationRestClient ... clients)
        {
            SpeakerIdentificationRestClient client = clients[0];
            try
            {
                Log.d("OUT", "Retrieving all profiles...");
                profiles = client.getProfiles();
                Log.d("OUT", "Retrieval successful");
            }
            catch (IOException e)
            {
                Log.d("ERR", "Failed to retrieve profiles");
                e.printStackTrace();
            }
            catch (GetProfileException e)
            {
                Log.d("API", "GetProfile request declined");
                e.printStackTrace();
            }
            return 1;
        }
    }

    private int getUID()
    {
        if (uid + 1 < Integer.MAX_VALUE)
            uid++;
        else
            uid = 0;
        return uid;
    }
}
