package jpsam3hklam9.descrack;

import java.util.Arrays;

/**
 * A small aggregate class to hold data from a server request.
 */
public class ServerRequest
{
    private String groupID;
    private JsonHandler.RequestType type;
    private byte[] foundKey;

    public ServerRequest(String groupID, JsonHandler.RequestType type)
            throws JsonFormatException
    {
        if (type == null)
            throw new JsonFormatException();

        this.type = type;
        this.groupID = groupID;
        this.foundKey = null;   // Not used unless sending off "found" type request.
    }

    /**
     * Sets the foundKey to specified value.
     * @param foundKey The found key in Base64 URL_SAFE format.
     */
    public void setFoundKey(byte[] foundKey)
    {
        this.foundKey = foundKey;
    }

    @Override
    public String toString()
    {
        String requestType = null;
        try
        {
            requestType = JsonHandler.getStringFromRequestType(type);
        } catch (JsonFormatException e)
        {
            MainActivity.status.setText("Fatal exception caught: Cannot format JSON object.");
        }

        // If there is a foundKey, append this to requestType.
        if (foundKey != null)
            requestType += ":" + Arrays.toString(foundKey);

        return "{\n" +
                "    \"request\" : {\n" +
                "        \"groupid\" : \"" + groupID + "\",\n" +
                "        \"type\" : \"" + requestType + "\"\n" +
                "    }\n" +
                "}\n";
    }
}
