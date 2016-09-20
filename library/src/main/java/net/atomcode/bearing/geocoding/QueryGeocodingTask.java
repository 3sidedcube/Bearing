package net.atomcode.bearing.geocoding;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import org.apache.http.client.methods.HttpGet;

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

//	public QueryGeocodingTask(Context context, String[] queries, Locale locale)
//	{
//		super(context, queries, locale);
//	}

	@Override protected List<Address> doInBackground(String... params)
	{
		if (params == null || params.length == 0)
		{
			// No query
			return null;
		}

		String query = params[0];

		// Attempt to use the native geocoder if the device supports it
		// Native geocoding is sometimes spotty and will fail, so if it doesn't return anything then use the remote geocoder
		if (deviceHasNativeGeocoding())
		{
			try
			{
				List<Address> nativeGeocodingResults = addressForNativeGeocodedQuery(query);
				if (nativeGeocodingResults != null && !nativeGeocodingResults.isEmpty())
				{
					return nativeGeocodingResults;
				}
			}
			catch (IOException ex)
			{
				// continue and try to use the remote geocoder
			}
		}

		return addressForRemoteGeocodedQuery(query);
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

	/**
	 * A fallback alternative that will use a web request to geocode the query.
	 *
	 * @param query The query to geocode
	 * @return The geocoded location as returned from the web service.
	 */
	protected List<Address> addressForRemoteGeocodedQuery(String query)
	{
		// Make query API compliant
		query = query.replace(" ", "+");
		String params = "?address=" + query + "&sensor=false";
		return super.addressForRemoteGeocodedQuery(new HttpGet(WEB_API_URL + params));
	}
}
