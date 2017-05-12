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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.cognitive.speakerrecognition.SpeakerIdentificationRestClient;
import com.microsoft.cognitive.speakerrecognition.contract.GetProfileException;
import com.microsoft.cognitive.speakerrecognition.contract.identification.IdentificationException;
import com.microsoft.cognitive.speakerrecognition.contract.identification.IdentificationOperation;
import com.microsoft.cognitive.speakerrecognition.contract.identification.OperationLocation;
import com.microsoft.cognitive.speakerrecognition.contract.identification.Profile;

/**
 * Created by jkih on 2017-05-02.
 */

public class MSCogServWrapper
{
    private static final int NUM_RETRIES = 10;
    // in ms
    private static final int RETRY_DELAY = 500;
    // in ms, the amount of time before sending another request
    private static final int REQUEST_INTERVAL = 2000;
    // in ms, the amount of time the upload polling thread sleeps between polls
    private static final int UPLOAD_POLLING_DELAY = 100;

    public enum RequestBehavior
    {
        /**
         * All requests are served, even if a queue forms
         */
        SEQUENTIAL_QUEUE,
        /**
         * Requests made before the REQUEST_INTERVAL are discarded
         * Default value
         */
        DISCARD
    }

    // do not use directly lest ye tempt the gods of thread safety
    private HashMap<Integer, IdentificationOperation> readyResults = new HashMap<>();
    private int uid = 0;
    private SpeakerIdentificationRestClient msWrapper;
    private HashMap<UUID, String> aliases = new HashMap<>();
    private List<Profile> profiles;
    /** Do not manipulate directly */
    private RequestBehavior requestBehavior;
    private AtomicBoolean uploadToken = new AtomicBoolean(true);
    /** Do not manipulate directly */
    private Object[] uploadQueue;

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
        setRequestBehavior(RequestBehavior.DISCARD);
        Log.d("IMP", "ASYNCTASK POOL SIZE: " + Runtime.getRuntime().availableProcessors() + 1);
        new IdentifyThread().execute();
    }

    public void update()
    {
        new UpdateAllSpeakers().execute(msWrapper);
    }

    public void setRequestBehavior(RequestBehavior newBehavior)
    {
        if (requestBehavior != RequestBehavior.DISCARD && newBehavior == RequestBehavior.DISCARD)
        {
            new DiscarderThread().execute();
        }
        //discarder deactivation done automatically from the discarder thread
        requestBehavior = newBehavior;
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
            retval.id = result.processingResult.identifiedProfileId;
            retval.confidence = result.processingResult.confidence.toString();
            retval.alias = aliases.get(retval.id);
            return retval;
        }
        return null;
    }

    private synchronized void pushNewProfile(int receipt, IdentificationOperation profile)
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
        Object[] args = new Object[4];
        args[0] = msWrapper;
        args[1] = filePath;
        args[2] = isShort;
        args[3] = receipt;
        getsetQueue(args);
        return receipt;
    }

    private synchronized Object[] getsetQueue(Object[] args)
    {
        Object[] retval = null;
        if (args != null)
        {
            uploadQueue = args;
            retval = args;
        }
        else
        {
            retval = uploadQueue;
            uploadQueue = null;
        }
        return retval;
    }

    private class IdentifyThread extends AsyncTask<Integer, Integer, Integer>
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
        protected Integer doInBackground(Integer ... ints)
        {
            Object[] args = null;
            while (true)
            {
                Log.d("OUT", "ID running...");
                while (requestBehavior == RequestBehavior.DISCARD &&
                        uploadToken.getAndSet(false) == false)
                {
                    args = getsetQueue(null);
                    if(args != null)
                    {
                        break;
                    }
                    else
                    {
                        uploadToken.set(true);
                    }
                    try
                    {
                        Thread.sleep(UPLOAD_POLLING_DELAY);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }

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
                        } else if (result.status == com.microsoft.cognitive.speakerrecognition.contract.identification.Status.FAILED
                                || retries >= NUM_RETRIES)
                        {
                            throw new IdentificationException((result.message));
                        }

                        retries++;
                        publishProgress(retries);
                        try
                        {
                            Thread.sleep(RETRY_DELAY);
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                    if (retries >= NUM_RETRIES)
                    {
                        throw new IdentificationException("Server timeout");
                    }
                    Log.d("OUT", "Identification done");
                } catch (FileNotFoundException e)
                {
                    e.printStackTrace();
                } catch (IOException e)
                {
                    e.printStackTrace();
                } catch (IdentificationException e)
                {
                    e.printStackTrace();
                }

                pushNewProfile(receipt, result);
            }
        }

        @Override
        protected void onProgressUpdate(Integer ... ints)
        {
            int delayNum = ints[0];
            Log.d("API", "No response, attempt " + (delayNum + 1));
        }

        @Override
        protected void onPostExecute(Integer result)
        {
        }
    }

    private class DiscarderThread extends AsyncNoUpdate<Integer>
    {
        @Override
        protected Integer doInBackground(Integer ... ints)
        {
            while (requestBehavior == RequestBehavior.DISCARD)
            {
                Log.d("OUT", "Discarder running...");
                try
                {
                    Thread.sleep(REQUEST_INTERVAL);
                    uploadToken.set(true);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            uploadToken.set(true);

            return 0;
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

    private synchronized int getUID()
    {
        if (uid + 1 < Integer.MAX_VALUE)
            uid++;
        else
            uid = 0;
        return uid;
    }
}
