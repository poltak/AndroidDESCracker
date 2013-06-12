package jpsam3hklam9.descrack;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends Activity
{
    /* CONSTANTS */
	private final static String DICT_PATH = "words";
    private final static String KEY_RATE_FORMAT = "%1$, .2f";
    protected final static boolean ENABLE = true;
    protected final static boolean DISABLE = false;
    protected final static boolean INCLUDE_SERVICE_BUTTON = ENABLE;
    protected final static boolean EXCLUDE_SERVICE_BUTTON = DISABLE;

    /* UI WIDGETS & GLOBALS */
	protected static HashSet<String> dictionary;
	protected static TextView status;
	protected static Button serviceButton;
    protected static JsonHandler.RequestType requestType = null;
    protected static ProgressBar progressBar;
    protected static long totalTimeProgressed = 0;

    private static TextView avgSearchRate;
    private static TextView currSearchRate;
    private static TextView estTimeLeft;
    private static Button   ttrueButton;
    private static Button   tfalseButton;
    private static Button   realButton;

	/**
     * Handles setting up the UI and setting of button listeners. Also checks if the DESService is currently running
     * (if it was started in a previous run of the app, but never stopped). If so, the UI is set appropriately.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        instantiateWidgets();

        // If service still running from cracking attempt that wasn't manually stopped.
        if (isServiceRunning(DESService.class.getName()))
        {
            serviceButton.setText(R.string.service_running);
            status.setText(R.string.status_searching);
        } else
        {
            setButtons(DISABLE, INCLUDE_SERVICE_BUTTON);    // Disable buttons until dictionary generates.
            new DictionaryGeneratorTask().execute();        // Generate TextRecogniser dictionary in separate thread.
        }

        setButtonListeners();
	}

    /**
     * Handles instantiating all widgets used in the UI.
     */
    private void instantiateWidgets()
    {
        serviceButton   = (Button) findViewById(R.id.service_button);
        ttrueButton     = (Button) findViewById(R.id.ttrue_button);
        tfalseButton    = (Button) findViewById(R.id.tfalse_button);
        realButton      = (Button) findViewById(R.id.real_button);
        status          = (TextView) findViewById(R.id.status);
        avgSearchRate   = (TextView) findViewById(R.id.avg_search_rate);
        currSearchRate  = (TextView) findViewById(R.id.current_search_rate);
        estTimeLeft     = (TextView) findViewById(R.id.time_est);
        progressBar     = (ProgressBar) findViewById(R.id.progress_bar);
        // Initialise progress bar to keyspace size.
        progressBar.setMax(0x10000);
    }

    /**
     * Sets the onClickListeners for each of the MainActivity Buttons.
     * The checking requestType for null (in each of the request type buttons), is as the service
     * button is originally disabled until the user picks a request type. Upon picking the request
     * type for the first time, the service button is enabled (allowing the service to start).
     */
    private void setButtonListeners()
    {
        serviceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) { handleService(); }
        });

        ttrueButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestType == null)
                    serviceButton.setEnabled(true);
                requestType = JsonHandler.RequestType.TEST_TRUE;
                status.setText("True test request is set.");
            }
        });

        tfalseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestType == null)
                    serviceButton.setEnabled(true);
                requestType = JsonHandler.RequestType.TEST_FALSE;
                status.setText("False test request is set.");
            }
        });

        realButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestType == null)
                    serviceButton.setEnabled(true);
                requestType = JsonHandler.RequestType.REAL;
                status.setText("Real request is set.");
            }
        });
    }

    /**
     * Handles checking whether or not the service is currently running, and either starts or stops
     * it depending on the outcome. Also manages setting widget text depending on whether or not
     * the service is started.
     */
    private void handleService()
    {
        Intent service = new Intent(this, DESService.class);
        boolean setButtonValue;

        if (isServiceRunning(DESService.class.getName()))
        {
            stopService(service);
            serviceButton.setText(R.string.service_stopped);
            status.setText(R.string.status_stopped);
            setButtonValue = ENABLE;
        } else
        {
            totalTimeProgressed = 0;    // Reset total time progressed for each crack attempt.
            startService(service);
            serviceButton.setText(R.string.service_running);
            status.setText(R.string.status_searching);
            setButtonValue = DISABLE;
        }
        // Disable or enable server request buttons depending on whether Service is started or stopped.
        setButtons(setButtonValue, EXCLUDE_SERVICE_BUTTON);
    }

    /**
     * Disables/enables relevant Buttons in the MainActivity.
     * @param setValue Specifies whether to enable or disable Buttons.
     * @param includeServiceButton Specifies whether or not to dis/enable the service Button also.
     */
    protected static void setButtons(boolean setValue, boolean includeServiceButton)
    {
        ttrueButton.setEnabled(setValue);
        tfalseButton.setEnabled(setValue);
        realButton.setEnabled(setValue);
        if (includeServiceButton)    serviceButton.setEnabled(setValue);
    }

	/**
	 * Utility method to check whether or not service with name @param serviceName is running.
	 * @param serviceName The name of a service to check.
	 * @return A boolean value denoting whether or not the service is running.
	 */
	private boolean isServiceRunning(String serviceName)
	{
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        if (runningServices == null)    return false;

        for (RunningServiceInfo service : runningServices)
			if (serviceName.equals(service.service.getClassName()))
				return true;
		return false;
	}

    /**
     * Updates key searching rates and estimated time left with values gotten from brute force checking Task.
     * Called from onProgressUpdate(). Uses our own formulas for calculating key rates (i.e., probably not the best).
     * @param currentIndex The current index that the search is up to in the keyspace.
     * @param timeTaken The amount of time (in milliseconds) that the last key took to check.
     */
    protected static void updateKeyRates(int currentIndex, int timeTaken)
    {
        double timeTakenSec      = (double) timeTaken           / 1000.0;
        double totalTimeTakenSec = (double) totalTimeProgressed / 1000.0;

        totalTimeProgressed += timeTakenSec;
        int keysRemaining = 0x10000 - currentIndex;

        double currentKeyRate       = 1.0           / timeTakenSec;
        double avgKeyRate           = currentIndex  / totalTimeTakenSec;
        double estimatedTimeLeft    = keysRemaining / avgKeyRate;

        // Update appropriate TextViews.
        currSearchRate.setText("Current search rate: "+String.format(KEY_RATE_FORMAT, currentKeyRate)+" keys/s");
        avgSearchRate.setText("Average search rate: "+String.format(KEY_RATE_FORMAT, avgKeyRate)+" keys/s");
        estTimeLeft.setText("Estimated time remaining: "+String.format(KEY_RATE_FORMAT, estimatedTimeLeft)+" s");
    }


	/**
	 * An AsyncTask which allows the dictionary to be generated in a different thread.
	 */
	private class DictionaryGeneratorTask extends AsyncTask<Void, Void, Boolean>
	{
		@Override
		protected Boolean doInBackground(Void... params)
		{
			// Generate the TextRecogniser's dictionary.
			try {
				dictionary = TextRecogniser.generateDictionary(getAssets().open(DICT_PATH));
			} catch (IOException e)	{
				status.setText(R.string.status_dictgen);
				return false;
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			// Enable the service button and set widget text.
			if (result)
			{
				setButtons(ENABLE, EXCLUDE_SERVICE_BUTTON);
				serviceButton.setText(R.string.service_stopped);
				status.setText("Please set request type.");
			}
		}
	}
}
