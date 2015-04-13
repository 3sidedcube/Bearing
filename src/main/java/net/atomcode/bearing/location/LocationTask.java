package net.atomcode.bearing.location;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import net.atomcode.bearing.Bearing;
import net.atomcode.bearing.BearingTask;
import net.atomcode.bearing.location.provider.GMSLocationProvider;
import net.atomcode.bearing.location.provider.LegacyLocationProvider;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Base location task for acquiring locations
 */
public abstract class LocationTask implements BearingTask
{
	protected LocationProvider locationProvider;
	protected LocationProviderRequest request;

	protected LocationListener listener;

	protected boolean running = false;

	protected String taskId;

	public LocationTask(Context context)
	{
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
		if (resultCode == ConnectionResult.SUCCESS)
		{
			locationProvider = GMSLocationProvider.getInstance();
		}
		else if (Bearing.isLocationServicesAvailable(context))
		{
			locationProvider = LegacyLocationProvider.getInstance();
		}
		else
		{
			throw new IllegalStateException("No location provider available on this device!");
		}

		locationProvider.create(context);

		request = new LocationProviderRequest();
	}

	public Location getLastLocation()
	{
		return locationProvider.getLastKnownLocation(request);
	}

	@Override
	public BearingTask start()
	{
		running = true;
		if (request.fallbackTimeout > 0)
		{
			new Timer().schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					if (isRunning())
					{
						LocationTask.this.cancel();
						if (listener != null)
						{
							listener.onTimeout();
							handleTimeoutFallback();
						}
					}
				}
			}, request.fallbackTimeout);
		}

		return this;
	}

	@Override
	public void cancel()
	{
		running = false;
		if (taskId != null)
		{
			locationProvider.cancelUpdates(taskId);
		}
	}

	/**
	 * Listen for location updates
	 */
	public LocationTask listen(LocationListener listener)
	{
		this.listener = listener;
		return this;
	}

	@Override
	public boolean isRunning()
	{
		return running;
	}

	/*
	 * ==============================================
	 * LOCATION TASK API
	 * ==============================================
	 */

	/**
	 * Set the accuracy of the location request(s)
	 * @param accuracy The accuracy to which the location should be gathered
	 */
	public LocationTask accuracy(Accuracy accuracy)
	{
		request.accuracy = accuracy;
		return this;
	}

	/**
	 * Whether to use a cached location if available,
	 * and how old does the location need to be to be treated as valid
	 * @param use Whether to use the cached location
	 * @param expiry How old does a cached location have to be to be invalid?
	 */
	public LocationTask cache(boolean use, long expiry)
	{
		request.useCache = use;
		request.cacheExpiry = expiry;
		return this;
	}

	/**
	 * Fallback for if the timeout is reached
	 */
	@Override
	public LocationTask fallback(int fallback, long timeout)
	{
		request.fallback = fallback;
		request.fallbackTimeout = timeout;
		return this;
	}

	/*
	 * ==============================================
	 * INTERNAL METHODS
	 * ==============================================
	 */

	/**
	 * Handle the timeout fallback here.
	 * listener is non-null at this point.
	 */
	private void handleTimeoutFallback()
	{
		new Handler(Looper.getMainLooper()).post(new Runnable()
		{
			@Override public void run()
			{
				if (request.fallback == LocationProviderRequest.FALLBACK_CACHE)
				{
					Location cachedLocation = locationProvider.getLastKnownLocation(request);
					if (cachedLocation != null)
					{
						listener.onUpdate(cachedLocation);
					}
					else
					{
						listener.onFailure();
					}
				}
			}
		});
	}
}
