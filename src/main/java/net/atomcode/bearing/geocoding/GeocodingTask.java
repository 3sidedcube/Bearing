package net.atomcode.bearing.geocoding;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
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
import java.util.Locale;

/**
 * Base Geocoding task, supplies listener and other definitions
 */
public abstract class GeocodingTask<T> extends AsyncTask<T, Void, List<Address>>
{
	private static final int DEFAULT_RESULT_COUNT = 10;

	protected static final String WEB_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";

	public interface Listener
	{
		public void onSuccess(List<Address> locations);
		public void onFailure();
	}

	protected Context context;
	protected Locale locale;

	protected Listener listener;

	private T[] params;

	protected int resultCount;

	GeocodingTask(Context context, T[] params)
	{
		this(context, params, context.getResources().getConfiguration().locale);
	}

	GeocodingTask(Context context, T[] params, Locale locale)
	{
		this.context = context;
		this.locale = locale;

		this.params = params;

		// Set a default result count
		this.resultCount = DEFAULT_RESULT_COUNT;
	}

	/**
	 * Attach the given listener to the Geocoding task
	 */
	@SuppressWarnings("unused")
	public GeocodingTask listen(Listener listener)
	{
		this.listener = listener;
		return this;
	}

	/**
	 * Set the desired number of results from this query
	 */
	@SuppressWarnings("unused")
	public GeocodingTask results(int resultCount)
	{
		this.resultCount = resultCount;
		return this;
	}

	/**
	 * Begin the task execution. Returns the task for future cancellation if required
	 */
	@SuppressWarnings("unused, unchecked")
	public GeocodingTask start()
	{
		execute(params);
		return this;
	}

	/**
	 * Simple listener callbacks to check for valid return values
	 */
	@Override protected void onPostExecute(List<Address> address)
	{
		super.onPostExecute(address);
		if (address != null)
		{
			if (listener != null)
			{
				listener.onSuccess(address);
			}
		}
		else
		{
			listener.onFailure();
		}
	}

	/**
	 * Check to see if the device has native geocoding capability.
	 * @return {@code true} if ability present, {@code false} otherwise.
	 */
	protected boolean deviceHasNativeGeocoding()
	{
		return Geocoder.isPresent();
	}

	protected List<Address> addressForRemoteGeocodedQuery(HttpGet request)
	{
		StringBuilder data = new StringBuilder();
		try
		{
			HttpClient client = new DefaultHttpClient();
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
					JSONObject result = addresses.getJSONObject(i);

					JSONObject geometry = result.getJSONObject("geometry");
					JSONObject locationData = geometry.getJSONObject("location");

					Address address = new Address(locale);
					address.setLatitude(locationData.getDouble("lat"));
					address.setLongitude(locationData.getDouble("lng"));

					String[] addressParts = TextUtils.split(result.getString("formatted_address"), ", ");
					int addressLine = 0;
					for (int addressPartIndex = 0; addressPartIndex < addressParts.length; ++addressPartIndex)
					{
						String addressPart = addressParts[addressPartIndex].trim();
						// If the address part is just a number then prepend it to the next address part
						if (TextUtils.isDigitsOnly(addressPart) && addressPartIndex < addressParts.length - 1)
						{
							addressParts[addressPartIndex + 1] = addressPart + ", " + addressParts[addressPartIndex + 1];
						}
						// Don't allow consecutive duplicate address lines
						else if (addressLine == 0 || !addressPart.equals(address.getAddressLine(addressLine - 1)))
						{
							address.setAddressLine(addressLine, addressPart);
							++addressLine;
						}
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
				case "street_number":
				{
					if (address.getPremises() == null) address.setPremises(componentLongName);
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
				case "political":
				case "colloquial_area":
				default:
				{
					break;
				}
			}
		}
	}
}
