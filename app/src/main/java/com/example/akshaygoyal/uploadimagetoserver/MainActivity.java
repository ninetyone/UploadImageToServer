package com.example.akshaygoyal.uploadimagetoserver;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int SELECT_PHOTO = 100;
    public static final String MY_PREFS_NAME = "MyPrefsFile";
    private SharedPreferences mSharedPrefs;
    public static final String IMAGE_PATH_SET = "srcImagePathSet";

    public static final String INTENT_STATUS = "UPLOAD_STATUS";
    public static final String INTENT_MESSAGE = "MESSAGE";
    private String srcImagePath = null;
    private ProgressDialog dialog = null;
    private TextView selectImageTextView;
    private BroadcastReceiver receiver;
    private Spinner mResSpinner;
    private Button mUploadButton;
    private Bitmap mSelectedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        dialog = new ProgressDialog(this);
        dialog.setTitle("Uploading");
        dialog.setMessage("Please wait...");
        dialog.setCancelable(false);

        selectImageTextView = (TextView) findViewById(R.id.select_image_text);
        mResSpinner = (Spinner) findViewById(R.id.res_spinner);
        mUploadButton = (Button) findViewById(R.id.uplaod_button);

        mUploadButton.setVisibility(View.GONE);
        mResSpinner.setVisibility(View.GONE);

        mSharedPrefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_STATUS);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //do something based on the intent's action
                dialog.dismiss();
                String message = intent.getStringExtra(INTENT_MESSAGE);
                if (message.equals("DONE")) {
                    Toast.makeText(MainActivity.this, "Image uploaded successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Error uploading image", Toast.LENGTH_SHORT).show();
                }
            }
        };
        registerReceiver(receiver, filter);

    }


    public void loadImageFromGallery(View view) {
        // Create intent to Open Image applications like Gallery, Google Photos
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // Start the Intent
        startActivityForResult(galleryIntent, SELECT_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            if (requestCode == SELECT_PHOTO && resultCode == RESULT_OK && null != data) {
                Uri selectedImage = data.getData();
                srcImagePath = getRealPathFromURI(selectedImage);

                mSelectedBitmap = decodeUri(selectedImage);

                ImageView imgView = (ImageView) findViewById(R.id.selected_image);
                imgView.setImageBitmap(mSelectedBitmap);
                mUploadButton.setVisibility(View.VISIBLE);
                mResSpinner.setVisibility(View.VISIBLE);
                selectImageTextView.setVisibility(View.GONE);
                populateSpinner();
            } else {
                srcImagePath = null;
                Toast.makeText(this, "You haven't picked Image", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            srcImagePath = null;
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_LONG).show();
        }
    }

    private void populateSpinner() {
        List<String> spinnerArray = new ArrayList<>();

        int height = mSelectedBitmap.getHeight();
        int width = mSelectedBitmap.getWidth();

        spinnerArray.add("Original = " + height + "px X " + width + "px");
        spinnerArray.add("Three Quarters = " + (height * 3) / 4 + "px X " + (width * 3) / 4 + "px");
        spinnerArray.add("Half = " + height / 2 + "px X " + width / 2 + "px");
        spinnerArray.add("Quarter = " + height / 4 + "px X " + width / 4 + "px");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mResSpinner.setAdapter(adapter);
    }

    private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException {

        // Decode image size
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, options);
        // The new size we want to scale to
        final int REQUIRED_SIZE = 540;

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = options.outWidth, height_tmp = options.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp / 2 < REQUIRED_SIZE || height_tmp / 2 < REQUIRED_SIZE) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options options1 = new BitmapFactory.Options();
        options1.inSampleSize = scale;
        return BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, options1);

    }

    public String getRealPathFromURI(Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // When Upload button is clicked
    public void uploadImage(View v) {
        if (srcImagePath != null) {
            SharedPreferences.Editor editor = getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE).edit();
            Set<String> srcImagePathSet;

            int resPosition = mResSpinner.getSelectedItemPosition();
            int height = mSelectedBitmap.getHeight();
            int width = mSelectedBitmap.getWidth();
            int newHeight = height, newWidth = width;
            switch (resPosition) {
                case 0:
                    // Do Nothing
                    break;
                case 1:
                    newHeight = (height * 3) / 4;
                    newWidth = (width * 3) / 4;
                    mSelectedBitmap = Bitmap.createScaledBitmap(mSelectedBitmap, newWidth, newHeight, false);
                    break;
                case 2:
                    newHeight = height / 2;
                    newWidth = width / 2;
                    mSelectedBitmap = Bitmap.createScaledBitmap(mSelectedBitmap, newWidth, newHeight, false);
                    break;
                case 3:
                    newHeight = height / 4;
                    newWidth = width / 4;
                    mSelectedBitmap = Bitmap.createScaledBitmap(mSelectedBitmap, newWidth, newHeight, false);
                    break;
                default:
                    break;
            }

            srcImagePath = srcImagePath.concat("$?" + newWidth + "X" + newHeight);

            if (mSharedPrefs.contains(IMAGE_PATH_SET)) {
                srcImagePathSet = mSharedPrefs.getStringSet(IMAGE_PATH_SET, null);
            } else {
                srcImagePathSet = new HashSet<>();
            }
            srcImagePathSet.add(srcImagePath);
            editor.putStringSet(IMAGE_PATH_SET, srcImagePathSet);
            editor.apply();
            Intent intent = new Intent(this, UploadService.class);
            if (isConnectingToInternet()) {
                dialog.show();
                startService(intent);
            } else
                Snackbar.make(findViewById(R.id.uplaod_button), "No internet! Image will be uploaded when there is internet connectivity.", Snackbar.LENGTH_LONG).show();

        } else {
            Toast.makeText(this, "Select image to upload", Toast.LENGTH_SHORT).show();
        }
    }


    public boolean isConnectingToInternet() {
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivity.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }*/

}