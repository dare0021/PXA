package edu.kaist.jkih.mscg_speaker_id;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.io.IOException;

/**
 * Created by jkih on 2017-04-24.
 */

public class PermissionRequest
{
    public static void request(Activity caller, String permission)
    {
        int permissionCheck = ContextCompat.checkSelfPermission(caller, permission);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(caller, new String[]{permission}, PackageManager.PERMISSION_GRANTED);
        }
    }

    /**
     * Pings google.com
     */
    public static boolean internetConnectionAvailable()
    {
        boolean retval;
        try
        {
            retval = (Runtime.getRuntime().exec("ping -c 1 google.com").waitFor() == 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();;
            retval = false;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            retval = false;
        }
        return retval;
    }
}
