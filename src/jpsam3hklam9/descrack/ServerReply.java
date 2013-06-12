package jpsam3hklam9.descrack;

/**
 * A small aggregate class to hold data from a server reply.
 */
public class ServerReply
{
//    private JsonHandler.RequestType type; // Originally implemented, but discovered we never actually needed this.
    protected byte[] key;
    protected byte[] cipherText;

    public ServerReply(JsonHandler.RequestType type, String key, String cipherText)
            throws JsonFormatException
    {
        if (type == null || key == null || key.length() > 11 || cipherText == null)
            throw new JsonFormatException();

//        this.type = type;
        this.key = key.getBytes(); 			// need to get key range and decode base64
        this.cipherText = cipherText.getBytes();
    }
}