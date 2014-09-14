package com.sri.mapnavigation;

import java.util.List;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

public class NavigationService extends Service implements SensorEventListener {

	private boolean isActivityRunning = false;

	public static final String TAG = "PNavigationService";
	private LocationListener gpsLocListener = new MyGPSLocationListener();
	private GpsStatus.Listener gpsStatusListener = new MyGpsStatusListener();
	private LocationManager mlocManager;
	private Location mylocation;
	private SensorManager mSensorManager;
	private Sensor accelerometer, magnetometer;
	private long lastLocationChangeMilli;
	private double lastSensorChangeAngle,lastSensorChangeUpdateAngle;
	private boolean isGPSFix = false;

	@Override
	public void onCreate() {
		super.onCreate();
		registerReceiver();
		mlocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(navServiceReceiver);
		if (mlocManager != null) {
			mlocManager.removeUpdates(gpsLocListener);
			mlocManager.removeGpsStatusListener(gpsStatusListener);
			mlocManager = null;
		}

		if (mSensorManager != null) {
			mSensorManager = null;
		}
		L.i(TAG, "PNavigationService Destroyed");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	private void registerReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(NavigationHelper.ACTION_NAV_SERVICE);
		registerReceiver(navServiceReceiver, filter);
		L.i(TAG, "navService receiver registered.");
		sendInitialAction();
	}

	private BroadcastReceiver navServiceReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context paramContext, Intent paramIntent) {
//			L.i(TAG, "Inside OnReceive()");
			String action = paramIntent.getAction();
			if (action.equals(NavigationHelper.ACTION_NAV_SERVICE)) {
				doAction(paramIntent);
			}
		}
	};

	private void checkGPS() {
		if (isGpsAvailable()) {
			L.i(TAG, "GPS available");
			Intent i = new Intent();
			i.putExtra(NavigationHelper.NAV_ACTION, NavigationHelper.GPS_ON);
			sendBroadCast(i);
		} else {
			L.i(TAG, "GPS not available");
			if (mlocManager != null) {
				mlocManager.removeUpdates(gpsLocListener);
			}
			Intent i = new Intent();
			i.putExtra(NavigationHelper.NAV_ACTION, NavigationHelper.GPS_OFF);
			sendBroadCast(i);
		}
	}

	private void startGPS() {
		if (isGpsAvailable()) {
			mlocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, gpsLocListener);
			mlocManager.addGpsStatusListener(gpsStatusListener);
			Criteria criteria = new Criteria();
			String bestProvider = mlocManager.getBestProvider(criteria, false);
			Location location = mlocManager.getLastKnownLocation(bestProvider);
			callLocationChanged(location);
		}
	}
	
//	private void pauseGPS() {
//		if(mlocManager != null) {
//			mlocManager.removeUpdates(gpsLocListener);
//			mlocManager.removeGpsStatusListener(gpsStatusListener);
//		}
//	}
//	
//	private void resumeGPS() {
//		if(mlocManager != null) {
//			startGPS();
//		}
//	}

	private boolean isGpsAvailable() {
		boolean GPSStatus = false;
		GPSStatus = mlocManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		List<String> providerList = mlocManager.getAllProviders();
		if (providerList.contains(LocationManager.GPS_PROVIDER)) {
			GPSStatus = mlocManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		}
		return GPSStatus;
	}

	private void sendBroadCast(Intent data) {
//		L.i(TAG, "Inside Nav sendBroadCast()");
		data.setAction(NavigationHelper.ACTION_HELPER_SERVICE);
		if (isActivityRunning)
			sendBroadcast(data);
	}

	private void sendInitialAction() {
		Intent i = new Intent();
		i.putExtra(NavigationHelper.NAV_ACTION, NavigationHelper.SERVICE_RUNNING);
		i.setAction(NavigationHelper.ACTION_HELPER_SERVICE);
		sendBroadcast(i);
	}

	private void doAction(Intent data) {
		int action = data.getExtras().getInt(NavigationHelper.NAV_ACTION);
		switch (action) {
		case NavigationHelper.ACTIVITY_RUNNING:
			L.i(TAG, "Action activity running");
			isActivityRunning = true;
			break;
		case NavigationHelper.ACTIVITY_STOPPED:
			L.i(TAG, "Action activity stopped");
			isActivityRunning = false;
			break;
		case NavigationHelper.ACTION_CHECKGPS:
			L.i(TAG, "Action check gps");
			checkGPS();
			break;
		case NavigationHelper.ACTION_STARTGPS:
			L.i(TAG, "Action start gps");
			startGPS();
			break;
		case NavigationHelper.ACTIVITY_RESUMED:
			L.i(TAG, "Action activity resumed");
			registerSensors();
//			resumeGPS();
			break;
		case NavigationHelper.ACTIVITY_PAUSED:
			L.i(TAG, "Action activity paused");
			unregisterSensors();
//			pauseGPS();
			break;
		}
	}

	private void callLocationChanged(Location loc) {
		Intent i = new Intent();
		i.putExtra(NavigationHelper.NAV_ACTION, NavigationHelper.ACTION_LOCATION_CHANGED);
		i.putExtra(NavigationHelper.CHANGED_LOCATION, loc);
		sendBroadCast(i);
	}

	private void callGpsProviderStatusChanged(int status) {
		Intent i = new Intent();
		i.putExtra(NavigationHelper.NAV_ACTION, NavigationHelper.ACTION_GPS_PROVIDER_STATUS_CHANGED);
		i.putExtra(NavigationHelper.CHANGED_GPS_PROVIDER_STATUS, status);
		sendBroadCast(i);
	}
	
	private void callGpsStatusChanged(int status) {
		Intent i = new Intent();
		i.putExtra(NavigationHelper.NAV_ACTION, NavigationHelper.ACTION_GPS_STATUS_CHANGED);
		i.putExtra(NavigationHelper.CHANGED_GPS_STATUS, status);
		sendBroadCast(i);
	}
	
	private void callSensorChanged(float values) {
		double change = lastSensorChangeAngle - lastSensorChangeUpdateAngle;
		double sign = Math.signum(change);
		change = sign == +1.0 ? change : change * -1.0; 
		//L.i(TAG, "angle change:"+change);
		if(change < NavigationHelper.SENSONR_CHANGE_THRESHOLD_ANGLE) {
			return;
		}
		
//		L.i(TAG, "Inside callSensorChanged()");
		Intent i = new Intent();
		i.putExtra(NavigationHelper.NAV_ACTION, NavigationHelper.ACTION_SENSOR_CHANGED);
		i.putExtra(NavigationHelper.CHANGED_SENSOR, values);
		sendBroadCast(i);
		lastSensorChangeUpdateAngle = Math.toDegrees(values);
	}

	public class MyGPSLocationListener implements LocationListener {

		@Override
		public void onLocationChanged(Location loc) {
//			L.i(TAG, "**GPS onLocationChanged()**");
			if (loc == null) {
				L.i(TAG, "Location is null.");
				return;
			}
			
			lastLocationChangeMilli = SystemClock.elapsedRealtime();
			mylocation = loc;
			callLocationChanged(loc);
		}

		@Override
		public void onProviderDisabled(String provider) {
			L.i(TAG, "***GPS provider disabled");
			checkGPS();
		}

		@Override
		public void onProviderEnabled(String provider) {
			L.i(TAG, "***GPS provider enabled. " + provider);
			checkGPS();
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
//			L.i(TAG, "***GPS status changed. " + status);
			callGpsProviderStatusChanged(status);
		}
	}

	private void registerSensors() {
		L.i(TAG, "Inside registerSensors");
		mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
		mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
	}

	private void unregisterSensors() {
		L.i(TAG, "Inside unregisterSensors");
		mSensorManager.unregisterListener(this, accelerometer);
		mSensorManager.unregisterListener(this, magnetometer);

	}

	float[] mGravity;
	float[] mGeomagnetic;

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
			mGravity = event.values;
		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
			mGeomagnetic = event.values;

		if (mGravity != null && mGeomagnetic != null) {
			float R[] = new float[9];
			float I[] = new float[9];
			boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
			if (success) {
				float orientation[] = new float[3];
				SensorManager.getOrientation(R, orientation);
				lastSensorChangeAngle = Math.toDegrees(orientation[0]);
				callSensorChanged(orientation[0]);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor paramSensor, int paramInt) {	}
	
	private class MyGpsStatusListener implements GpsStatus.Listener {

		@Override
		public void onGpsStatusChanged(int event) {
			switch (event) {
			case GpsStatus.GPS_EVENT_FIRST_FIX:
				callGpsStatusChanged(event);
				isGPSFix = true;
				break;
			case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
				if(mylocation != null) {
					isGPSFix = (SystemClock.elapsedRealtime() - lastLocationChangeMilli) < NavigationHelper.GPS_STATUS_THRESHOLD_TIME;
				}
				
				if(!isGPSFix) {
					callGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED);
				}
				
				break;
			case GpsStatus.GPS_EVENT_STARTED:
				L.i(TAG,"Gps event started.");
				callGpsStatusChanged(event);
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				L.i(TAG,"Gps event stopped.");
				callGpsStatusChanged(event);
				break;
			}
		}
		
	}
}
