/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at
         http://www.apache.org/licenses/LICENSE-2.0
       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */


package org.apache.cordova.geolocation;

import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;

import javax.security.auth.callback.Callback;

public class Geolocation extends CordovaPlugin {

    String TAG = "GeolocationPlugin";
    CallbackContext context;

    String [] permissions;

    public Geolocation()
    {
        permissions = new String[2];
        permissions[0] = Manifest.permission.ACCESS_COARSE_LOCATION;
        permissions[1] = Manifest.permission.ACCESS_FINE_LOCATION;
    }


    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        context = callbackContext;
        if(action.equals("getPermission"))
        {
            if(hasPermisssion())
            {
                PluginResult r = new PluginResult(PluginResult.Status.OK);
                context.sendPluginResult(r);
                return true;
            }
            else {
                cordova.requestPermissions(this, 0);
            }
            return true;
        }
        return false;
    }

    public String[] getPermissionRequest() {
        return permissions;
    }


    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        PluginResult result;
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                LOG.d(TAG, "Permission Denied!");
                result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                context.sendPluginResult(result);
                return;
            }
        }
        result = new PluginResult(PluginResult.Status.OK);
        context.sendPluginResult(result);
    }

    public boolean hasPermisssion() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            return true;
        }
        for(String p : permissions)
        {
            if(PackageManager.PERMISSION_DENIED == cordova.getActivity().checkSelfPermission(p))
            {
                return false;
            }
        }

        return true;
    }


}