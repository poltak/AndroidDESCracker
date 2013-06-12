package jpsam3hklam9.descrack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;

public class TextRecogniser
{
	protected final static String TEXT_ENCODING = "US-ASCII";
	protected final static String NON_ALPHA = "[^a-zA-Z\\s]"; // matches non-alphabetics and non-whitespace

    private static int numOfWords;

    /**
     * Checks each word in @param text to see if they're in @param dictionary. If over 75% are, returns true.
     * @param text Text to mangle and search dictionary for.
     * @param dictionary Dictionary generated elsewhere to search against.
     * @return A boolean value denoting whether or not >75% of words in @param text are in @param dictionary.
     */
	public static boolean isValidEnglish(byte[] text, HashSet<String> dictionary)
	{
		// Maps each word to a boolean, which denotes whether they are found to be in the dictionary.
		HashMap<String, Boolean> candidateWords = getCandidateWords(new String(text));

		// Makes sure that there are a reasonable number of characters to words. From external
		// testing with very large texts (Project Gutenberg), the average ratio is around 1:16 (words to chars).
		// Choosing 13% allows for some leeway for some exceptional cases, and also checks that the
		// number of words is greater than 2, so as to not return false negatives on very large
		// words encrypted by themselves.
		double charsToWordsRatio = (double) numOfWords / (double) text.length;
		if (charsToWordsRatio < 0.13 && numOfWords > 2)
			return false;

		// For each candidate word, if they're in the dictionary, set word's flag to true.
		for (String word : candidateWords.keySet())
		{
			if (dictionary.contains(word.toLowerCase()))
				candidateWords.put(word, true);
		}

        return isReasonableMatch(candidateWords);
    }

    /**
     * Splits a String on whitespace and formats the words into a HashMap which maps to booleans. These booleans say
     * whether or not the word is in the dictionary (done at later stage). Use of HashMap reduces memory (no duplicates)
     * and increases performance when accessing the words.
     * @param text String to make into HashMap.
     * @return HashMap of Strings to Booleans containing all words from @param text.
     */
    private static HashMap<String, Boolean> getCandidateWords(String text)
    {
        HashMap<String, Boolean> candidateWords = new HashMap<String, Boolean>(1);

        // Removes non-alphabetics/non-whitespace from String before splitting on whitespace.
        String[] candidates = text.replaceAll(NON_ALPHA, "").split("\\s");
        numOfWords = candidates.length;

        // Only add words with more than 1 char (saves space and time, as there's not going to be any long English string
        // with a whole lot of single lettered words).
        for (String word : candidates)
            if (word.length() > 1)
                candidateWords.put(word, false);
        return candidateWords;
    }

    /**
     * Given a HashMap of Strings to Booleans, says whether or not it looks like English given the amount of words that
     * have their "isEnglish" flag set to true (the Boolean value).
     * @param candidateWords String keys mapped to Boolean flags to check.
     * @return True if over 75% are English words, false otherwise.
     */
    private static boolean isReasonableMatch(HashMap<String, Boolean> candidateWords)
    {
        int validCounter = 0;

        for (boolean wordIsEnglish : candidateWords.values())
            if (wordIsEnglish)
                validCounter++;

        double percentCorrect = (double) validCounter / (double) candidateWords.size();
        return percentCorrect > 0.75;
    }

	/**
	 * From text file in location @param dictPath, creates a HashSet dictionary from the text in
	 * that file.
	 * @return A HashSet of Strings containing entries for all words in file.
	 * @throws IOException
	 */
	public static HashSet<String> generateDictionary(InputStream dictAsset) throws IOException
	{
		HashSet<String> dictionary = new HashSet<String>();
		String word;
		BufferedReader dictReader = null;

		try	{
			dictReader = new BufferedReader(new InputStreamReader(dictAsset,
					Charset.forName(TEXT_ENCODING)));
			while ((word = dictReader.readLine()) != null)
				dictionary.add(word.toLowerCase());
		} finally {
            if (dictReader != null)
                dictReader.close();
        }
		return dictionary;
	}
}
