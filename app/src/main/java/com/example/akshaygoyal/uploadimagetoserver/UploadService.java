package com.example.akshaygoyal.uploadimagetoserver;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * Created by akshaygoyal on 10/13/15.
 */
public class UploadService extends IntentService {

    private static final String LOG_TAG = UploadService.class.getSimpleName();

    private SharedPreferences mSharedPrefs;

    private static final String SERVER_URL = "http://23.239.29.151/test-upload.php";
    private int serverResponseCode = 0;
    private String srcImagePath;
    private Set<String> srcImagePathSet;

    public UploadService() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mSharedPrefs = getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE);

        if (mSharedPrefs.contains(MainActivity.IMAGE_PATH_SET)) {

            srcImagePathSet = mSharedPrefs.getStringSet(MainActivity.IMAGE_PATH_SET, null);

            Object[] arrPath = srcImagePathSet.toArray();

            for (Object anArrPath : arrPath) {
                srcImagePath = anArrPath.toString();

                doFileUpload();
            }
        }
    }


    public int doFileUpload() {

        try {

            String fileName = srcImagePath.substring(0, srcImagePath.indexOf("$?"));
            String size = srcImagePath.substring(srcImagePath.indexOf("$?"));
            int width = Integer.valueOf(size.substring(2, size.indexOf("X")));
            int height = Integer.valueOf(size.substring(size.indexOf("X") + 1));


            Intent intent = new Intent(MainActivity.INTENT_STATUS);
            Log.i(LOG_TAG, "Started processing image" + srcImagePath);

            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****";
            int bytesRead, bytesAvailable, bufferSize;
            byte[] buffer;
            int maxBufferSize = 1024 * 1024;
            File sourceFile = new File(fileName);
            byte[] imageByteArray = loadImageFromStorage(fileName, width, height);

            if (!sourceFile.isFile()) {
                Log.e("uploadFile", "Source File not exist :" + srcImagePath);
                return 0;

            } else {
                try {

                    // open a URL connection to the Servlet
                    ByteArrayInputStream bais = new ByteArrayInputStream(imageByteArray);
                    URL url = new URL(SERVER_URL);

                    // Open a HTTP  connection to  the URL
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true); // Allow Inputs
                    conn.setDoOutput(true); // Allow Outputs
                    conn.setUseCaches(false); // Don't use a Cached Copy
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                    conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                    conn.setRequestProperty("uploaded_file", fileName);

                    dos = new DataOutputStream(conn.getOutputStream());

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\";filename=\"" + fileName + "\"" + lineEnd);


                    dos.writeBytes(lineEnd);

                    // create a buffer of  maximum size
                    bytesAvailable = bais.available();

                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    buffer = new byte[bufferSize];

                    // read file and write it into form...
                    bytesRead = bais.read(buffer, 0, bufferSize);

                    while (bytesRead > 0) {
                        dos.write(buffer, 0, bufferSize);
                        bytesAvailable = bais.available();
                        bufferSize = Math.min(bytesAvailable, maxBufferSize);
                        bytesRead = bais.read(buffer, 0, bufferSize);
                    }

                    // send multipart form data necessary after file data...
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                    // Responses from the server (code and message)
                    serverResponseCode = conn.getResponseCode();
                    String serverResponseMessage = conn.getResponseMessage();

                    Log.i("uploadFile", "HTTP Response is : "
                            + serverResponseMessage + ": " + serverResponseCode);

                    if (serverResponseCode == 200) {
                        Set<String> srcImagePathSet = mSharedPrefs.getStringSet(MainActivity.IMAGE_PATH_SET, null);
                        srcImagePathSet.remove(srcImagePath);
                        SharedPreferences.Editor editor = getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE).edit();
                        editor.putStringSet(MainActivity.IMAGE_PATH_SET, srcImagePathSet);
                        editor.apply();
                        Log.i(LOG_TAG, "File Upload Complete." + fileName);

                        intent.putExtra(MainActivity.INTENT_MESSAGE, "DONE");
                        sendBroadcast(intent);
                    }

                    //close the streams //
                    bais.close();
                    dos.flush();
                    dos.close();

                } catch (MalformedURLException ex) {

                    ex.printStackTrace();
                    Log.e("Upload file to server", "error: " + ex.getMessage(), ex);
                    intent.putExtra(MainActivity.INTENT_MESSAGE, "ERROR");
                    sendBroadcast(intent);
                } catch (Exception e) {

                    e.printStackTrace();
                    Log.e("Upload Exception", "" + e.getMessage(), e);
                    intent.putExtra(MainActivity.INTENT_MESSAGE, "ERROR");
                    sendBroadcast(intent);
                }
                return serverResponseCode;

            } // End else block
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private byte[] loadImageFromStorage(String path, int width, int height) {

        try {
            File f = new File(path);
            Bitmap b = BitmapFactory.decodeStream(new FileInputStream(f));
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            b = Bitmap.createScaledBitmap(b, width, height, false);
            b.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static class ConnectionMonitor extends BroadcastReceiver {

        private final String LOG_TAG = ConnectionMonitor.class.getSimpleName();


        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(LOG_TAG, "starting onReceive");
            String action = intent.getAction();
            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
                return;

            boolean noConnectivity = intent.getBooleanExtra(
                    ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

            NetworkInfo aNetworkInfo = intent
                    .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

            Intent sendIntent = new Intent(context, UploadService.class);

            if (!noConnectivity) {
                if ((aNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE)
                        || (aNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
                    Log.i(LOG_TAG, "Starting service from BroadcastReceiver");
                    context.startService(sendIntent);

                }
            } else {
                if ((aNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE)
                        || (aNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
                    // stop your service stuff here
                }
            }
        }
    }

}
