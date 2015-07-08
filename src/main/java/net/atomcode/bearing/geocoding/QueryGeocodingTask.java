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

		if (deviceHasNativeGeocoding())
		{
			return addressForNativeGeocodedQuery(query);
		}
		else
		{
			return addressForRemoteGeocodedQuery(query);
		}
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
	 * @param query The query to geocode
	 * @return The geocoded locations
	 */
	private List<Address> addressForNativeGeocodedQuery(String query)
	{
		Geocoder geocoder = new Geocoder(context, locale);

		List<Address> results;

		try
		{
			results = geocoder.getFromLocationName(query, resultCount);

			if (results != null && !isCancelled())
			{
				return results;
			}
			else
			{
				return addressForRemoteGeocodedQuery(query);
			}
		}
		catch (IOException ex)
		{
			return addressForRemoteGeocodedQuery(query);
		}
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
