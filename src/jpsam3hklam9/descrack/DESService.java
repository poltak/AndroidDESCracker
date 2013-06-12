package jpsam3hklam9.descrack;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Base64;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

public class DESService extends Service
{
    /* CONSTANTS */
    private final static String GROUP_ID = "12889";
    // Signals used for progress updates.
    private final static int UPDATE_PROGRESS_BAR = 0, UPDATE_SEARCH_RATES = 1, UPDATE_TOTAL_TIME = 2,
            JSON_FORMAT_EXCEPTION = 3;
	protected final static long IV = 0x0001010100010001L;
	// Index table used to extrapolate 56 bit key to 64 bits using DES generic permutation method.
	// Essentially adds an arbitrary valued bit at the end of each block of 7 bits.
	protected final static byte[] EXTRAPOLATION_TABLE = {
		 1,	2, 3, 4, 5, 6, 7, 7,
		 8, 9,10,11,12,13,14,14,
		15,16,17,18,19,20,21,21,
		22,23,24,25,26,27,28,28,
		29,30,31,32,33,34,35,35,
		36,37,38,39,40,41,42,42,
		43,44,45,46,47,48,49,49,
		50,51,52,53,54,55,56,56
	};

    private BruteForceTask bruteForceTask = new BruteForceTask();
    private static HashSet<String> dictionary;
    private static boolean fetchNextKeyspace = false;   // Flag for automated "real" keyspace fetching.

    @Override
    public void onCreate()
    {
        Toast.makeText(this, "Service started.", Toast.LENGTH_SHORT).show();
        MainActivity.progressBar.setProgress(0);    // Reset progress bar.
    }

    @Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
        // Get JSON from server and brute force reply data on a separate thread.
        bruteForceTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public void onDestroy()
	{
        // Starts a new DESService before stopping current if flag is set. Flag can only be set in a "real" run if the
        // key was not found. This lets the cracking seamlessly continue on a "real" request without user intervention.
        if (fetchNextKeyspace)
        {
            fetchNextKeyspace = false;
            startService(new Intent(this, DESService.class));
        } else
        {
            // If a "real" search is manually stopped by user, sends a "not-found" to server so we can resume it later.
            if (MainActivity.requestType == JsonHandler.RequestType.REAL) {
                try {
                    new ServerConnectionTask().execute(new ServerRequest(GROUP_ID, JsonHandler.RequestType.NOT_FOUND));
                } catch (JsonFormatException e) {
                    MainActivity.status.setText("Can't format reply to send back to server.");
                }
            }

            Toast.makeText(this, "Service stopped.", Toast.LENGTH_SHORT).show();
            // Kill BruteForceTask thread if still active (when service manually stopped).
            if (bruteForceTask != null)     bruteForceTask.cancel(true);
            // Re-enable server request type buttons.
            MainActivity.setButtons(MainActivity.ENABLE, MainActivity.EXCLUDE_SERVICE_BUTTON);
            MainActivity.serviceButton.setText(R.string.service_stopped);
        }
	}



	/**
	 * An AsyncTask which shall do a brute force crack on the received ciphertext and keyspace from server reply.
	 */
	protected class BruteForceTask extends AsyncTask<Void, Integer, DecryptionDataAggregate>
	{
		@Override
		protected DecryptionDataAggregate doInBackground(Void... params)
		{
			// Clone generated dictionary to local Service variable in-case app gets stopped but Service still running.
            dictionary = new HashSet<String>();
            for (Object word : MainActivity.dictionary.toArray())
                dictionary.add((String) word);

			// Set up IV for CBC mode.
			DES.setIv(IV);

            // Send specified request to server and get reply to brute force.
            ServerReply reply = null;
            try {
                ServerRequest request = new ServerRequest(GROUP_ID, MainActivity.requestType);
                reply = new ServerConnectionTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, request).get();
            } catch (JsonFormatException e) {
                publishProgress(JSON_FORMAT_EXCEPTION);
                stopSelf();
            } catch (InterruptedException e) {
                stopSelf();
            } catch (ExecutionException e) {
                stopSelf();
            }

            if (reply == null)  return null; // Should never be null unless exception was caught (and handled properly).
            // Run a Base64 decode on the cipherText and keyspace gotten from server and do a
			//	brute force check using the keyspace entirety on the received ciphertext.
            return bruteForceCheck(Base64.decode(reply.cipherText, Base64.URL_SAFE),
                    Base64.decode(reply.key, Base64.URL_SAFE));
		}

		/**
		 * Runs a brute force crack on the @param cipherText for all keys in @param keyspace.
		 * @param cipherText Ciphertext to crack decoded from Base64 string.
		 * @param keyspace Keyspace to check decoded from Base64 string.
		 * @return If key found in @param keyspace, returns the data in aggregate object, else returns null.
		 */
		private DecryptionDataAggregate bruteForceCheck(byte[] cipherText, byte[] keyspace)
		{
			// Gets keyspace and sets currentKey to first value in keyspace.
			long currentKeyExtrapolated;
			long currentKey = DES.getLongFromBytes(keyspace, 0);
			currentKey <<= 16;

			// Check for each key in the keyspace.
			for (int i = 0; i < 0x10000; i++, currentKey++)
			{
                long timeBefore = System.currentTimeMillis();

				// Extrapolates 56 bit key to 64 bits to work with DES using EXTRAPOLATION_TABLE.
				currentKeyExtrapolated = DES.permute(EXTRAPOLATION_TABLE, 56, currentKey);

                // Perform DES decryption using current key and check for English text.
				byte[] decryptedText = DES.decryptCBC(cipherText, currentKeyExtrapolated);
                if (TextRecogniser.isValidEnglish(decryptedText, dictionary))
					return new DecryptionDataAggregate(new String(decryptedText), currentKey, i);

                // Update progress bar and search rates (only progress bar updated every turn).
                long totalRunTime = System.currentTimeMillis() - timeBefore;
                publishProgress(UPDATE_TOTAL_TIME, (int) totalRunTime);
                publishProgress(UPDATE_PROGRESS_BAR, i);
                if (i % 100 == 0)   publishProgress(UPDATE_SEARCH_RATES, i, (int) totalRunTime);
            }
			return null;    // Return null if key not found in keyspace.
		}

        /**
         * Progress update cases:
         * 1. to update the progress bar (done every key).
         * 2. to update search rates (done every 100 keys).
         * 3. update the total time counter (done ever key).
         * 4. error handling related to JSON incorrect formatting.
         * @param progress First arg contains case flag, other args contain progress data.
         */
        @Override
        protected void onProgressUpdate(Integer... progress)
        {
            switch (progress[0])
            {
                case UPDATE_PROGRESS_BAR:
                    MainActivity.progressBar.setProgress(progress[1]);
                    break;
                case UPDATE_SEARCH_RATES:
                    MainActivity.updateKeyRates(progress[1], progress[2]);
                    break;
                case UPDATE_TOTAL_TIME:
                    MainActivity.totalTimeProgressed += progress[1];
                    break;
                case JSON_FORMAT_EXCEPTION:
                    MainActivity.status.setText("Cannot find valid JSON format file. Try again later.");
                    break;
            }
        }

		/**
		 * Set status text view to display the key that was found. If no key was found (null arg), a message
		 * declaring this is displayed instead.
         * In the case of a REAL request type, the next keyspace is requested from the server.
         * In all cases, sends back another "request" to the server to notify of our search results.
		 */
		@Override
		protected void onPostExecute(DecryptionDataAggregate decryptionData)
		{
            if (MainActivity.requestType == JsonHandler.RequestType.REAL) // We are doing a "real" run.
			{
                if (decryptionData == null) // If key is not found (send "not-found" to server, ignore reply).
                {
                    try {
                        new ServerConnectionTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                                new ServerRequest(GROUP_ID, JsonHandler.RequestType.NOT_FOUND));
                    } catch (JsonFormatException e) {
                        publishProgress(JSON_FORMAT_EXCEPTION);
                    }
                    fetchNextKeyspace = true;// Set fetch flag to true to fetch next keyspace without user intervention.
                }
                else  checkResultWithServer(decryptionData);    // If key is found, need to check results with server.
			}
            else      // We are just doing a "test-true" or "test-false" run.
            {
                // If key not found ("test-false"), display not found message to user
                if (decryptionData == null) MainActivity.status.setText(R.string.status_notfound);
                // If key found ("test-true"), display details
                else                        MainActivity.status.setText("test-true results:" +
                                                "\nKey: 0x" + Long.toHexString(decryptionData.key) +
                                                "\nIndex in keyspace: " + String.valueOf(decryptionData.keyspaceIndex) +
                                                "\nPlain text:\n" + decryptionData.plainText);
            }

            stopSelf(); // Stop Service and handle next move based on fetchNextKeyspace flag (in onDestroy).
		}

        /**
         * Method to format found results to valid JSON request and send to server. Upon receiving, the server will
         * send back a reply stating whether or not we found the correct key.
         * The main status TextView is then set to notify user of whether or not the application found the correct key.
         * @param decryptionData Aggregate object containing all the data used/found in the cracking.
         */
        private void checkResultWithServer(DecryptionDataAggregate decryptionData)
        {
            ServerReply reply = null;

            try {
                ServerRequest result = new ServerRequest(GROUP_ID, JsonHandler.RequestType.FOUND);

                // Set found key to send back encoded in Base64.
                byte[] keyAsBytes = new byte[8];
                DES.getBytesFromLong(keyAsBytes, 0, decryptionData.key);
                int encodingFlags = Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE;
                result.setFoundKey(Base64.encode(keyAsBytes, encodingFlags));

                // Send back results to server.
                reply = new ServerConnectionTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, result).get();
            } catch (JsonFormatException e) {
                publishProgress(JSON_FORMAT_EXCEPTION);
                stopSelf();
            } catch (InterruptedException e) {
                stopSelf();
            } catch (ExecutionException e) {
                stopSelf();
            }

            if (reply != null && Arrays.equals(reply.key, "TRUE".getBytes()))       // Key found was correct.
            {
                MainActivity.status.setText("Correct real key found:" +
                        "\nKey: 0x" + Long.toHexString(decryptionData.key) +
                        "\nIndex in keyspace: " + String.valueOf(decryptionData.keyspaceIndex) +
                        "\nPlain text:\n" + decryptionData.plainText);
            } else
            {
                MainActivity.status.setText("Key found was not correct according to server.");
            }
        }
	}


    /**
     * Opens a connection with the server and sends a properly formatted JSON String request to it. It should return a
     * properly formatted JSON String from the server as a ServerReply.
     * If the network is found to be not currently connected, it will post a message to the main textview and stop the
     * service.
     */
    protected class ServerConnectionTask extends AsyncTask<ServerRequest, Integer, ServerReply>
    {
        // Further signals used for progress updates.
        private final static int IO_EXCEPTION = 4, NO_NETWORK_CONN = 5;

        private final String SERVER_ADDRESS = "vps01.fit3140hack.org";
        private final int PORT_NUMBER = 6578;

        private Socket clientSocket;
        private DataOutputStream dataOutputStream;
        private DataInputStream dataInputStream;

        @Override
        protected ServerReply doInBackground(ServerRequest... params) {
            ConnectivityManager connMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMan.getActiveNetworkInfo();

            // If no network connection.
            if (networkInfo == null || !networkInfo.isConnected())
            {
                publishProgress(NO_NETWORK_CONN);
                stopSelf();
                return null;
            }
            return sendAndReceive(params[0]);
        }

        /**
         * Attempts a connection with the server and sends @param request and receives reply as ServerReply object.
         * @param request ServerRequest object to send to server.
         * @return ServerReply object received and formatted from server.
         */
        private ServerReply sendAndReceive(ServerRequest request)
        {
            ServerReply serverReply = null;
            String reply;

            try {
                connect();
                dataOutputStream.writeUTF(request.toString());
                dataOutputStream.flush();
                reply = dataInputStream.readUTF();
                disconnect();

                serverReply = JsonHandler.readReply(new StringReader(reply));
            } catch (JsonFormatException e) {
                publishProgress(JSON_FORMAT_EXCEPTION);
                stopSelf();
            } catch (IOException e) {
                publishProgress(IO_EXCEPTION);
                stopSelf();
            }
            return serverReply;
        }

        private void connect() throws IOException
        {
            clientSocket = new Socket(SERVER_ADDRESS, PORT_NUMBER);
            dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
            dataInputStream = new DataInputStream(clientSocket.getInputStream());
        }

        private void disconnect() throws IOException
        {
            clientSocket.close();
            dataOutputStream.close();
            dataInputStream.close();
        }

        @Override
        protected void onProgressUpdate(Integer ... progress)
        {
            switch (progress[0])
            {
                case JSON_FORMAT_EXCEPTION:
                    MainActivity.status.setText("Cannot format valid JSON file to send to server.");
                    break;
                case IO_EXCEPTION:
                    MainActivity.status.setText("Fatal IO error: Cannot connect to server. Try again later.");
                    break;
                case NO_NETWORK_CONN:
                    MainActivity.status.setText("Network connection not current active. Try again later.");
                    break;
            }
        }
    }

	/**
	 * A simple aggregate class to group all data related to found key.
	 */
	private class DecryptionDataAggregate
	{
		private String plainText;
		private long key;
		private int keyspaceIndex;

		private DecryptionDataAggregate(String plainText, long key, int keyspaceIndex)
		{
			this.plainText = plainText;
			this.key = key;
			this.keyspaceIndex = keyspaceIndex;
		}
	}
}
