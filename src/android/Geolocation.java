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
import android.location.Location;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import javax.security.auth.callback.Callback;

public class Geolocation extends CordovaPlugin {

    String TAG = "GeolocationPlugin";
    CallbackContext context;
    private CordovaLocationListener mListener;

    String [] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION };

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mListener = new CordovaLocationListener(this, TAG);
        LOG.d(TAG,"Plugin Initialization Completed!!!");
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        LOG.d(TAG, "Geolocation.java We are entering execute........");
        context = callbackContext;

        if(!hasPermission()){
            PermissionHelper.requestPermissions(this, 0, permissions);
            return true;
        }

        if (action == null || !action.matches("getCurrentPosition|addWatch|clearWatch")) {
            return false;
        }

        final String id = args.optString(0, "");

        if (action.equals("clearWatch")) {
            clearWatch(id);
            return true;
        }

        if (action.equals("getCurrentPosition")) {
            getLastLocation(args, callbackContext);
        } 
        
        if (action.equals("addWatch")) {
            addWatch(id, callbackContext);
        }

        return true;
    }

    /**
     * Called when the activity is to be shut down. Stop listener.
     */
    public void onDestroy() {
        if (mListener != null) {
            mListener.destroy();
        }
    }

    /**
     * Called when the view navigates. Stop the listeners.
     */
    public void onReset() {
        this.onDestroy();
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException
    {
        PluginResult result;
        //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
        if(context != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    LOG.d(TAG, "Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                    context.sendPluginResult(result);
                    return;
                }

            }
            result = new PluginResult(PluginResult.Status.OK);
            context.sendPluginResult(result);
        }
    }

    public boolean hasPermission() {
        for(String p : permissions)
        {
            if(!PermissionHelper.hasPermission(this, p))
            {
                return false;
            }
        }
        return true;
    }

    private CordovaLocationListener getListener() {
        if (mListener == null) {
            mListener = new CordovaLocationListener(this, TAG);
        }
        return mListener;
    }

    /*
     * We override this so that we can access the permissions variable, which no longer exists in
     * the parent class, since we can't initialize it reliably in the constructor!
     */

    public void requestPermissions(int requestCode)
    {
        PermissionHelper.requestPermissions(this, requestCode, permissions);
    }

    public JSONObject returnLocationJSON(Location loc) {
        JSONObject o = new JSONObject();

        try {
            o.put("latitude", loc.getLatitude());
            o.put("longitude", loc.getLongitude());
            o.put("altitude", (loc.hasAltitude() ? loc.getAltitude() : null));
            o.put("accuracy", loc.getAccuracy());
            o.put("heading",
                    (loc.hasBearing() ? (loc.hasSpeed() ? loc.getBearing()
                            : null) : null));
            o.put("velocity", loc.getSpeed());
            o.put("timestamp", loc.getTime());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return o;
    }

    /**
     * Get the Last Known Location from the LocationListenr.
     * @param args
     * @param callbackContext
     */
    private void getLastLocation(JSONArray args, CallbackContext callbackContext) {
        int maximumAge;
        try {
            maximumAge = args.getInt(0);
        } catch (JSONException e) {
            e.printStackTrace();
            maximumAge = 0;
        }
        Location last = getListener().getLastKnownLocation();
        // Check if we can use lastKnownLocation to get a quick reading and use
        // less battery
        if (last != null && (System.currentTimeMillis() - last.getTime()) <= maximumAge) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, returnLocationJSON(last));
            callbackContext.sendPluginResult(result);
        } else {
            getCurrentPosition(callbackContext, Integer.MAX_VALUE);
        }
    }

    // Get the current Position.
    private void getCurrentPosition(CallbackContext callbackContext, int timeout) {
        getListener().addCallback(callbackContext, timeout);
    }

    // Add a Watch
    private void addWatch(String timerId, CallbackContext callbackContext) {
        getListener().addWatch(timerId, callbackContext);
    }

    // Remove a watch
    private void clearWatch(String id) {
        getListener().clearWatch(id);
    }

    /**
     * Location Successfull.
     *
     * @param loc
     * @param callbackContext
     * @param keepCallback
     */
    public void win(Location loc, CallbackContext callbackContext, boolean keepCallback) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, this.returnLocationJSON(loc));
        result.setKeepCallback(keepCallback);
        callbackContext.sendPluginResult(result);
    }

    /**
     * Location failed. Send error back to JavaScript.
     *
     * @param code
     *            The error code
     * @param msg
     *            The error message
     * @throws JSONException
     */
    public void fail(int code, String msg, CallbackContext callbackContext, boolean keepCallback) {
        JSONObject obj = new JSONObject();
        String backup = null;
        try {
            obj.put("code", code);
            obj.put("message", msg);
        } catch (JSONException e) {
            obj = null;
            backup = "{'code':" + code + ",'message':'"
                    + msg.replaceAll("'", "\'") + "'}";
        }
        PluginResult result;
        if (obj != null) {
            result = new PluginResult(PluginResult.Status.ERROR, obj);
        } else {
            result = new PluginResult(PluginResult.Status.ERROR, backup);
        }

        result.setKeepCallback(keepCallback);
        callbackContext.sendPluginResult(result);
    }

}