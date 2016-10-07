package org.apache.cordova.geolocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;

public class CordovaLocationListener implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    public static int PERMISSION_DENIED = 1;
    public static int POSITION_UNAVAILABLE = 2;
    public static int TIMEOUT = 3;

    public HashMap<String, CallbackContext> watches = new HashMap<String, CallbackContext>();

    protected boolean mIsRunning = false;

    private Geolocation mOwner;
    private List<CallbackContext> mCallbacks = new ArrayList<CallbackContext>();
    private Timer mTimer = null;
    private String TAG;

    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private static final String CURRENT_LOCATION = "CURRENT_LOCATION";
    private Boolean onConnectedCall=false;

    // The location request used to poll for location updates if needed.
    private static final LocationRequest REQUEST = LocationRequest.create()
            .setInterval(10000) // 10 seconds
            .setFastestInterval(16) // 16ms = 60fps
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    public CordovaLocationListener(Geolocation owner, String tag) {
        mOwner = owner;
        TAG = tag;
        setUpLocationClientIfNeeded();
    }

    public Location getLastKnownLocation(){
        return mCurrentLocation;
    }

    private void setUpLocationClientIfNeeded() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(mOwner.cordova.getActivity())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();

            if(!mGoogleApiClient.isConnected()){
                mGoogleApiClient.connect();
            }
        }
    }

    public int size() {
        return watches.size() + mCallbacks.size();
    }

    public void addWatch(String timerId, CallbackContext callbackContext) {
        watches.put(timerId, callbackContext);

        if (size() == 1) {
            start();
        }
    }

    public void addCallback(CallbackContext callbackContext, int timeout) {
        LOG.d(TAG,"timeout set to"+timeout);
        
        if (mTimer == null) {
            mTimer = new Timer();
        }

        mTimer.schedule(new LocationTimeoutTask(callbackContext, this), timeout);
        mCallbacks.add(callbackContext);

        if (size() == 1) {
            start();
        }
    }

    public void clearWatch(String timerId) {
        if (watches.containsKey(timerId)) {
            watches.remove(timerId);
        }
        if (size() == 0) {
            stop();
        }
    }

    /**
     * Stop the Location Update
     */
    public void destroy() {
        stop();
    }

    protected void fail(int code, String message) {
        cancelTimer();

        for (CallbackContext callbackContext : mCallbacks) {
            mOwner.fail(code, message, callbackContext, false);
        }

        if (watches.size() == 0) {
            stop();
        }

        mCallbacks.clear();

        for (CallbackContext callbackContext : watches.values()) {
            mOwner.fail(code, message, callbackContext, true);
        }
    }

    protected void win(Location loc) {
        cancelTimer();

        for (CallbackContext callbackContext : mCallbacks) {
            mOwner.win(loc, callbackContext, false);
        }

        if (watches.size() == 0) {
            stop();
        }

        mCallbacks.clear();

        for (CallbackContext callbackContext : watches.values()) {
            mOwner.win(loc, callbackContext, true);
        }
    }

    private void start() {
        if(!mGoogleApiClient.isConnected()){
                mGoogleApiClient.connect();
        }else{
             startLocationUpdate();
        }
    }

    private void stop() {
        cancelTimer();
        stopLocationUpdate();
    }

    private void cancelTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult arg0) {
        // Do nothing
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Do nothing
    }

    /**
     * Once Location client is connected we get the last known location to speed Up Process.
     * Otherwise we ask for a location update. THe location updates is used just once to avoid
     * battery drain.
     */
    @Override
    public void onConnected(Bundle arg0) {
        startLocationUpdate();
    }

    /**
     * Start location Update
     */
    private void startLocationUpdate(){
        int coarseLocationCheck = ContextCompat.checkSelfPermission(mOwner.cordova.getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION);
        int fineLocationCheck = ContextCompat.checkSelfPermission(mOwner.cordova.getActivity(), Manifest.permission.ACCESS_FINE_LOCATION);

        if(PackageManager.PERMISSION_GRANTED == coarseLocationCheck || PackageManager.PERMISSION_GRANTED == fineLocationCheck) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, REQUEST, this);
        }

    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "The location has been updated!");
        mCurrentLocation=location;
        win(location);
    }

    /**
     * Stop location updates
     */
    private void stopLocationUpdate(){
        if(mGoogleApiClient.isConnected()){
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }


    private class LocationTimeoutTask extends TimerTask {

        private CallbackContext mCallbackContext = null;
        private CordovaLocationListener mListener = null;

        public LocationTimeoutTask(CallbackContext callbackContext, CordovaLocationListener listener) {
            mCallbackContext = callbackContext;
            mListener = listener;
        }

        @Override
        public void run() {
            for (CallbackContext callbackContext : mListener.mCallbacks) {
                if (mCallbackContext == callbackContext) {
                    mListener.mCallbacks.remove(callbackContext);
                    break;
                }
            }

            if (mListener.size() == 0) {
                mListener.stop();
            }
        }
    }
}