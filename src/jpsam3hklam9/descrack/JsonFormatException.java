package jpsam3hklam9.descrack;

/**
 * Used so we app can distinguish errors involving incorrectly formatted JSON object to standard IOExceptions.
 */
public class JsonFormatException extends Exception
{
    protected JsonFormatException()
    {
        super();
    }
}
