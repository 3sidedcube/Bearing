package net.atomcode.bearing.location.provider;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import net.atomcode.bearing.location.LocationListener;
import net.atomcode.bearing.location.LocationProvider;
import net.atomcode.bearing.location.LocationProviderRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple location provider using the legacy android location services
 */
public class LegacyLocationProvider implements LocationProvider
{
	private static LegacyLocationProvider instance;

	public static LegacyLocationProvider getInstance()
	{
		if (instance == null)
		{
			instance = new LegacyLocationProvider();
		}
		return instance;
	}

	private LocationManager locationManager;

	private Map<String, android.location.LocationListener> runningRequests = new HashMap<>();

	@Override public void create(Context context)
	{
		locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
	}

	@Override public void destroy()
	{
		for (android.location.LocationListener runningRequest : runningRequests.values())
		{
			locationManager.removeUpdates(runningRequest);
		}
		runningRequests.clear();
	}

	@Override
	public Location getLastKnownLocation(LocationProviderRequest request)
	{
		ArrayList<String> providers = getProviderForRequest(request);
		long lastTime = Long.MIN_VALUE;
		Location latestLocation = null;

		for (String provider : providers)
		{
			Location location = locationManager.getLastKnownLocation(provider);

			if (location != null)
			{
				if (location.getTime() > lastTime)
				{
					latestLocation = location;
					lastTime = location.getTime();
				}
			}
		}

		return latestLocation;
	}

	@Override
	public String requestSingleLocationUpdate(LocationProviderRequest request, final LocationListener listener)
	{
		ArrayList<String> providers = getProviderForRequest(request);

		if (request.useCache)
		{
			Location lastKnownUserLocation = getLastKnownLocation(request);

			// Check if last known location is valid
			if (lastKnownUserLocation != null && System.currentTimeMillis() - lastKnownUserLocation.getTime() < request.cacheExpiry)
			{
				if (lastKnownUserLocation.getAccuracy() < request.accuracy.value)
				{
					if (listener != null)
					{
						listener.onUpdate(lastKnownUserLocation);
						return null;
					}
				}
			}
		}

		final String requestId = UUID.randomUUID().toString();

		runningRequests.put(requestId, new android.location.LocationListener()
		{
			@Override public void onLocationChanged(Location location)
			{
				if (listener != null)
				{
					listener.onUpdate(location);
				}

				runningRequests.remove(requestId);
			}

			@Override public void onStatusChanged(String provider, int status, Bundle extras)
			{

			}

			@Override public void onProviderEnabled(String provider)
			{

			}

			@Override public void onProviderDisabled(String provider)
			{

			}
		});

		for (String provider : providers)
		{
			locationManager.requestSingleUpdate(provider, runningRequests.get(requestId), Looper.getMainLooper());
		}

		return requestId;
	}

	@Override
	public String requestRecurringLocationUpdates(final LocationProviderRequest request, final LocationListener listener)
	{
		String requestId = UUID.randomUUID().toString();

		int powerCriteria = Criteria.POWER_LOW;
		int accuracyCriteria = Criteria.ACCURACY_MEDIUM;

		switch (request.accuracy)
		{
			case LOW:
				powerCriteria = Criteria.POWER_LOW;
				accuracyCriteria = Criteria.ACCURACY_COARSE;
				break;
			case MEDIUM:
				powerCriteria = Criteria.POWER_MEDIUM;
				accuracyCriteria = Criteria.ACCURACY_MEDIUM;
				break;
			case HIGH:
				powerCriteria = Criteria.POWER_HIGH;
				accuracyCriteria = Criteria.ACCURACY_FINE;
		}

		Criteria criteria = new Criteria();
		criteria.setPowerRequirement(powerCriteria);
		criteria.setAccuracy(accuracyCriteria);

		String bestProvider = locationManager.getBestProvider(criteria, false);

		runningRequests.put(requestId, new android.location.LocationListener()
		{
			@Override public void onLocationChanged(Location location)
			{
				if (listener != null)
				{
					listener.onUpdate(location);
				}
			}

			@Override public void onStatusChanged(String provider, int status, Bundle extras)
			{

			}

			@Override public void onProviderEnabled(String provider)
			{

			}

			@Override public void onProviderDisabled(String provider)
			{

			}
		});

		locationManager.requestLocationUpdates(bestProvider, request.trackingRate, 0, runningRequests.get(requestId));
		return null;
	}

	@Override
	public void cancelUpdates(String requestId)
	{
		if (runningRequests.containsKey(requestId))
		{
			locationManager.removeUpdates(runningRequests.get(requestId));
			runningRequests.remove(requestId);
		}
	}

	/**
	 * Get the provider for the given request
	 */
	private ArrayList<String> getProviderForRequest(LocationProviderRequest request)
	{
		ArrayList<String> providers = new ArrayList<>();
		switch (request.accuracy)
		{
			case HIGH:
				providers.add(LocationManager.GPS_PROVIDER);

			case MEDIUM:
				if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
				{
					providers.add(LocationManager.NETWORK_PROVIDER);
				}

			case LOW:
				if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER))
				{
					providers.add(LocationManager.PASSIVE_PROVIDER);
				}
		}

		return providers;
	}

}
