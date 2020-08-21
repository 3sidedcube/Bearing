package net.atomcode.bearing.geocoding;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import java.io.IOException;
import java.util.List;

/**
 * Task for geocoding a supplied query into latitude and longitude elements
 */
public class QueryGeocodingTask extends GeocodingTask<String>
{
	public QueryGeocodingTask(Context context, String[] queries)
	{
		super(context, queries);
	}

	@Override protected List<Address> doInBackground(String... params)
	{
		if (params == null || params.length == 0)
		{
			// No query
			return null;
		}

		String query = params[0];

		// Attempt to use the native geocoder if the device supports it
		if (deviceHasNativeGeocoding())
		{
			try
			{
				return addressForNativeGeocodedQuery(query);
			}
			catch (IOException ignored)
			{
			}
		}
		// Returning null calls onFailure callback.
		return null;
	}

	/**
	 * Geocode the query natively and return the result.
	 *
	 * Note
	 * =====
	 * Some devices, namely Amazon kindles, will report native geocoding support but
	 * actually not support it. This is caught by a null response. If this occurs
	 * the fallback {@code addressForRemoteGeocodedQuery} could be called
	 *
	 * @param query The query to geocode
	 * @return The geocoded locations
	 */
	private List<Address> addressForNativeGeocodedQuery(String query) throws IOException
	{
		Geocoder geocoder = new Geocoder(context, locale);
		return geocoder.getFromLocationName(query, resultCount);
	}
}
