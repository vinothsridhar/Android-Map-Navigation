package com.sri.mapnavigation;

import android.location.Location;

public interface NavigationListener {
	public void actionGPSStatusChanged(int status);
	public void actionProviderStatusChanged(int status);
	public void actionLocationChanged(Location location);
	public void actionSensorChanged(float value);
}
