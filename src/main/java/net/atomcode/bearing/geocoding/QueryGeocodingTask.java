package net.atomcode.bearing.geocoding;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.text.TextUtils;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
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
	private List<Address> addressForRemoteGeocodedQuery(String query)
	{
		StringBuilder data = new StringBuilder();
		try
		{
			// Make query API compliant
			query = query.replace(" ", "+");
			String params = "?address=" + query + "&sensor=false";

			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet(WEB_API_URL + params);
			HttpResponse response;

			if (!isCancelled())
			{
				try
				{
					response = client.execute(request);
				}
				catch (ClientProtocolException ex)
				{
					ex.printStackTrace();
					return null;
				}

				InputStream content = response.getEntity().getContent();

				InputStreamReader inputStreamReader = new InputStreamReader(content);
				BufferedReader reader = new BufferedReader(inputStreamReader);

				String line;
				while ((line = reader.readLine()) != null && !isCancelled())
				{
					data.append(line);
				}
			}
		}
		catch (IOException ex)
		{
			Log.e("Bearing", "Network error connecting to Google Geocoding API" + ex.getMessage());
			return null;
		}

		try
		{
			/* The JSON response structure
			{
				"results": [
					{
						"formatted_address": <formatted_address>,
						"geometry": {
							"location": {
								"lat": <latitude>
								"lng": <longitude>
							}
						}
					}
				]
			}
			*/

			if (!isCancelled())
			{
				JSONObject geocodeData = new JSONObject(data.toString());
				JSONArray addresses = geocodeData.getJSONArray("results");

				int resultsToRead = Math.min(resultCount, addresses.length());

				List<Address> addressList = new ArrayList<Address>(resultsToRead);
				for (int i = 0; i < resultsToRead; i++)
				{
					JSONObject result = addresses.getJSONObject(0);

					JSONObject geometry = result.getJSONObject("geometry");
					JSONObject locationData = geometry.getJSONObject("location");

					Address address = new Address(locale);
					address.setLatitude(locationData.getDouble("lat"));
					address.setLongitude(locationData.getDouble("lng"));

					String[] addressParts = TextUtils.split(result.getString("formatted_address"), ", ");
					int addressIndex = 0;
					for (String addressPart: addressParts)
					{
						address.setAddressLine(addressIndex, addressPart);
						++addressIndex;
					}

					JSONArray addressComponents = result.getJSONArray("address_components");
					for (int componentIndex = 0; componentIndex < addressComponents.length(); componentIndex++)
					{
						JSONObject component = addressComponents.getJSONObject(componentIndex);
						populateAddressFromGoogleMapComponent(address, component);
					}

					addressList.add(address);
				}

				return addressList;
			}
		}
		catch (JSONException ex)
		{
			Log.e("Bearing", "Google Geocoding API format parsing failed! " + ex.getMessage());
		}

		return null;
	}

	private void populateAddressFromGoogleMapComponent(Address address, JSONObject component) throws JSONException
	{
		String componentLongName = component.getString("long_name");
		String componentShortName = component.getString("long_name");
		JSONArray componentTypes = component.getJSONArray("types");
		for (int typeIndex = 0; typeIndex < componentTypes.length(); typeIndex++)
		{
			String typeText = componentTypes.getString(typeIndex);
			switch (typeText)
			{
				case "country":
				{
					address.setCountryName(componentLongName);
					address.setCountryCode(componentShortName);
					break;
				}
				case "route":
				{
					address.setThoroughfare(componentLongName);
					break;
				}
				case "administrative_area_level_1":
				{
					address.setAdminArea(componentLongName);
					break;
				}
				case "administrative_area_level_2":
				{
					address.setSubAdminArea(componentLongName);
					break;
				}
				case "locality":
				case "ward":
				{
					address.setLocality(componentLongName);
					break;
				}
				case "sublocality":
				{
					address.setSubLocality(componentLongName);
					break;
				}
				case "neighborhood":
				{
					if (address.getSubLocality() == null) address.setSubLocality(componentLongName);
					break;
				}
				case "premise":
				{
					address.setPremises(componentLongName);
					break;
				}
				case "postal_code":
				{
					address.setPostalCode(componentLongName);
					break;
				}
				case "natural_feature":
				case "airport":
				case "park":
				case "point_of_interest":
				case "intersection":
				{
					address.setFeatureName(componentLongName);
					break;
				}
				case "administrative_area_level_3":
				case "administrative_area_level_4":
				case "administrative_area_level_5":
				case "subpremise":
				case "street_number":
				case "political":
				case "colloquial_area":
				{
					break;
				}
			}
		}
	}
}
