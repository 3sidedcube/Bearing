package net.atomcode.bearing.location.provider;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import net.atomcode.bearing.Bearing;
import net.atomcode.bearing.location.LocationListener;
import net.atomcode.bearing.location.LocationProvider;
import net.atomcode.bearing.location.LocationProviderRequest;

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
		long lastTime = System.currentTimeMillis() - request.cacheExpiry;
		Location latestLocation = null;

		for (String provider : locationManager.getProviders(true))
		{
			Location location = locationManager.getLastKnownLocation(provider);

			if (location != null && location.getAccuracy() < request.accuracy.value && location.getTime() > lastTime)
			{
				latestLocation = location;
				lastTime = location.getTime();
			}
		}

		return latestLocation;
	}

	@Override
	public String requestSingleLocationUpdate(LocationProviderRequest request, final LocationListener listener)
	{
		if (request.useCache)
		{
			Bearing.log("pending", "LEGACY: Checking for cached locations...");
			Location lastKnownUserLocation = getLastKnownLocation(request);
			if (lastKnownUserLocation != null)
			{
				if (listener != null)
				{
					Bearing.log("pending", "LEGACY: Got cached location: " + lastKnownUserLocation);
					listener.onUpdate(lastKnownUserLocation);
					return null;
				}
			}
		}

		final String requestId = UUID.randomUUID().toString();

		runningRequests.put(requestId, new android.location.LocationListener()
		{
			@Override public void onLocationChanged(Location location)
			{
				Bearing.log(requestId, "LEGACY: Location changed to " + location);

				if (listener != null)
				{
					listener.onUpdate(location);
				}

				runningRequests.remove(requestId);
			}

			@Override public void onStatusChanged(String provider, int status, Bundle extras)
			{
				Bearing.log(requestId, "LEGACY: Status changed for " + provider + ": " + status);
			}

			@Override public void onProviderEnabled(String provider)
			{
				Bearing.log(requestId, "LEGACY: Enabled " + provider);
			}

			@Override public void onProviderDisabled(String provider)
			{
				Bearing.log(requestId, "LEGACY: Disabled " + provider);
			}
		});

		Criteria criteria = getCriteriaFromRequest(request);
		String bestProvider = locationManager.getBestProvider(criteria, true);

		Bearing.log(requestId, "LEGACY: Request location update from " + bestProvider);
		locationManager.requestSingleUpdate(bestProvider, runningRequests.get(requestId), Looper.getMainLooper());

		return requestId;
	}

	private Criteria getCriteriaFromRequest(LocationProviderRequest request)
	{
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

		return criteria;
	}

	@Override
	public String requestRecurringLocationUpdates(final LocationProviderRequest request, final LocationListener listener)
	{
		String requestId = UUID.randomUUID().toString();

		Criteria criteria = getCriteriaFromRequest(request);
		String bestProvider = locationManager.getBestProvider(criteria, true);

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
		// TODO: This call is ignoring the trackingDisplacement field
		return requestId;
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

}
