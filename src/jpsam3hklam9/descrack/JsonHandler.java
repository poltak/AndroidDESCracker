package jpsam3hklam9.descrack;

import android.util.JsonReader;

import java.io.IOException;
import java.io.StringReader;

/**
 * Class to handle all JSON object reading and writing needed for sending and receiving requests
 * and replies from server. Also declares all possible request types in static enum type RequestType.
 */
public class JsonHandler
{
	/**
	 * Enum type to specify all possible request types as specified in assignment specifications.
	 */
	public static enum RequestType {TEST_TRUE, TEST_FALSE, REAL, FOUND, NOT_FOUND}

    /**
	 * Reads JSON request from server and builds a new ServerRequest object to return to calling
	 * function. JSON request must be formatted as per server API specifications.
	 * @param in InputStreamReader to read from JSON file.
	 * @return	ServerRequest object built from values found in JSON file.
	 * @throws IOException Should only occur if JSON is formatted incorrectly.
	 * @throws JsonFormatException Only occurs if JSON reply contains invalid request type.
	 */
	public static ServerReply readReply(StringReader in)
			throws IOException, JsonFormatException
	{
		RequestType type = null;
		String key = null;
		String cipherText = null;

		String name;
		JsonReader reader = new JsonReader(in);

		reader.beginObject();
		{
			if (((name = reader.nextName()) == null) || !name.equals("reply"))	// No "reply" string at top.
			{
				reader.close();
				throw new JsonFormatException();
			}

			reader.beginObject();
			{
				while(reader.hasNext())
				{
                    if ((name = reader.nextName()) == null)
                    {
                        break;
                    }
					if (name.equals("type"))
					{
						type = getRequestTypeFromString(reader.nextString());
                        if (type == RequestType.NOT_FOUND) // Break out early if we're reading a NOT-FOUND reply.
                        {
                            reader.close();
                            return new ServerReply(type, "", "");
                        }
					}
					if (name.equals("key"))
					{
						key = reader.nextString();
					}
					if (name.equals("ciphertext"))
					{
						cipherText = reader.nextString();
					}
				}
			}
			reader.endObject();
		}
		reader.endObject();

		reader.close();
		// Create and return new ServerRequest object containing values specified in parsed JSON.
		return new ServerReply(type, key, cipherText);
	}

	/**
	 * Matches @param type to RequestType enum value.
	 * @param type String containing type representation.
	 * @return RequestType enum value matching @param type.
	 * @throws JsonFormatException Only occurs if @param type does not contain a valid
	 * String representation of a RequestType enum value.
	 */
	private static RequestType getRequestTypeFromString(String type)
			throws JsonFormatException
	{
		if (type.equals("test-true"))
			return RequestType.TEST_TRUE;
		if (type.equals("test-false"))
			return RequestType.TEST_FALSE;
		if (type.equals("real"))
			return RequestType.REAL;
		if (type.equals("found"))
			return RequestType.FOUND;
		if (type.equals("not-found"))
			return RequestType.NOT_FOUND;
		throw new JsonFormatException();
	}

	/**
	 * Matches RequestType enum @param type to valid String representation.
	 * @param type RequestType enum value to match against.
	 * @return Matched String form of @param type.
	 * @throws JsonFormatException Only occurs if @param type is not a valid enum value.
	 */
	public static String getStringFromRequestType(RequestType type)
			throws JsonFormatException
	{
		switch (type)
		{
		case TEST_TRUE:
			return "test-true";
		case TEST_FALSE:
			return "test-false";
		case REAL:
			return "real";
		case FOUND:
			return "found";
		case NOT_FOUND:
			return "not-found";
		default:
			throw new JsonFormatException();
		}
	}

}
