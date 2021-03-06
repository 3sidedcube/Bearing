package net.atomcode.bearing.geocoding;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Task for geocoding a supplied query into latitude and longitude elements
 */
public class ReverseGeocodingTask extends GeocodingTask<Double>
{
	/**
	 * Reverse geocode the supplied request using the devices current locale
	 * @param context The current app context
	 */
	public ReverseGeocodingTask(Context context, Double[]latlng)
	{
		super(context, latlng);
	}

	/**
	 * Reverse geocode the supplied request using the given explicit locale
	 * @param locale The locale to use when geocoding the query
	 */
	public ReverseGeocodingTask(Context context, Double[] latlng, Locale locale)
	{
		super(context, latlng, locale);
	}

	@Override protected List<Address> doInBackground(Double... params)
	{
		if (params == null || params.length < 2)
		{
			Log.w("Bearing", "Invalid lat,lng supplied to ReverseGeocoder");
			return null;
		}

		Double lat = params[0];
		Double lng = params[1];

		// Attempt to use the native geocoder if the device supports it
		// Native geocoding is sometimes spotty and will fail, so if it doesn't return anything then use the remote geocoder
		if (deviceHasNativeGeocoding())
		{
			try
			{
				List<Address> nativeGeocodingResults = addressForNativeGeocodedQuery(lat, lng);
				if (nativeGeocodingResults != null)
				{
					return nativeGeocodingResults;
				}
			}
			catch (IOException ex)
			{
				// continue
			}
		}

		return Collections.emptyList();
	}

	/**
	 * Geocode the query natively and return the result.
	 *
	 * Note
	 * =====
	 * Some devices, namely Amazon kindles, will report native geocoding support but
	 * actually not support it. This is caught by a null response. If this occurs
	 * the fallback {@code addressForRemoteGeocodedQuery} will be called
	 *
	 * @param latitude The latitiude of the location to reverse geocode
	 * @param longitude The longitude of the location to reverse geocode
	 * @return The geocoded location
	 */
	private List<Address> addressForNativeGeocodedQuery(Double latitude, Double longitude) throws IOException
	{
		Geocoder geocoder = new Geocoder(context, locale);
		return geocoder.getFromLocation(latitude, longitude, resultCount);
	}
}
