package edu.kaist.jkih.mscg_speaker_id;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

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
}
