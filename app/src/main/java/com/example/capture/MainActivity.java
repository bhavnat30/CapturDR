package com.example.capture;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.BasicAWSCredentials;
//import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.http.HttpClient;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.util.IOUtils;


import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final String KEY = "*****";
    private final String SECRET = "*****";


    private AmazonS3Client s3Client;
    private BasicAWSCredentials credentials;

    //track Choosing Image Intent
    private static final int CHOOSING_IMAGE_REQUEST = 1234;

    private TextView tvFileName;
    private TextView txtView;
    private ImageView imageView;
    private EditText edtFileName;

    private Uri fileUri;
    private Bitmap bitmap;

    String fileName;
    StringBuilder req=new StringBuilder("images");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.img_file);

        tvFileName = findViewById(R.id.tv_file_name);
        tvFileName.setText("");
        txtView=findViewById(R.id.txtView);

        findViewById(R.id.btn_choose_file).setOnClickListener(this);
        findViewById(R.id.btn_upload).setOnClickListener(this);
        findViewById(R.id.btn_download).setOnClickListener(this);

        AWSMobileClient.getInstance().initialize(this).execute();

        credentials = new BasicAWSCredentials(KEY, SECRET);
        s3Client = new AmazonS3Client(credentials);
    }
    public void postRequest() throws IOException {
        StringBuilder response = new StringBuilder();
        req.append("/");
        req.append(fileName);
        Log.i("******",req.toString());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String value=null;
                try {
                    URL url = new URL("https://h93lee9m0e.execute-api.eu-west-1.amazonaws.com/PRD_Inference");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    conn.setRequestProperty("Accept","application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    JSONObject jsonParam = new JSONObject();

                    jsonParam.put("S3imgKey","images" + '/' +fileName);
                    jsonParam.put("S3bucket","dataset-dr");

                    Log.i("JSON!!!!!", jsonParam.toString());
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    //os.writeBytes(URLEncoder.encode(jsonParam.toString(), "UTF-8"));
                    os.writeBytes(jsonParam.toString());

                    os.flush();
                    os.close();

                    InputStream inputStream;
                    //if (200 <= responseCode && responseCode <= 299) {
                    inputStream = conn.getInputStream();
//                    } else {
//                        inputStream = conn.getErrorStream();
//                    }

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(
                                    inputStream));


                    String currentLine;
                    StringBuilder response = new StringBuilder();
                    while ((currentLine = in.readLine()) != null)
                        response.append(currentLine);

                    in.close();




                    Log.i("STATUS", String.valueOf(conn.getResponseCode()));
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    Log.i("json response",jsonResponse.toString());
                    if(jsonResponse.has("body")) {
                        Log.i("body:", jsonResponse.getString("body"));
                        txtView.setText(jsonResponse.getString("body"));
                        txtView.setTextColor(Color.rgb(138, 43, 226));
                        txtView.setTextSize(42);
                    }

                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

    }



    public String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(projection[0]);
        String filePath = cursor.getString(columnIndex);
        return cursor.getString(column_index);
    }


    private void uploadFile() {

        if (fileUri != null) {
            fileName=getFileName(fileUri);
            String path = getPath(fileUri);




               // final File file = new File(String.valueOf(fileUri));
           // final File file = new File(MediaStore.Images.Media.EXTERNAL_CONTENT_URI+"/"+ fileName);
            final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+
                    "/Camera/" + fileName);


            //createFile(getApplicationContext(), fileUri, file);

            TransferUtility transferUtility =
                    TransferUtility.builder()
                            .context(getApplicationContext())
                            .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                            .s3Client(s3Client)
                            .build();

//            Log.i("fileUri",fileUri.toString());
//            Log.i("file",file.getAbsolutePath());
           Log.i("path",fileUri.toString());
          // Log.i("directory",getFileStreamPath(fileUri.toString())+"/"+fileName);

            Log.i("fileName:::",fileName);
            TransferObserver uploadObserver =
                    transferUtility.upload("images/"+ fileName, file);

            uploadObserver.setTransferListener(new TransferListener() {

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        Toast.makeText(getApplicationContext(), "Upload Completed!", Toast.LENGTH_SHORT).show();


                    } else if (TransferState.FAILED == state) {

                    }
                }


                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int) percentDonef;

                    tvFileName.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    ex.printStackTrace();
                }

            });
        }
    }
    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);

            }
        }
        return result;
    }


    private void downloadFile() {

        txtView.setVisibility(View.VISIBLE);

        if (fileUri != null) {


            try {
                final File localFile = File.createTempFile("images", getFileExtension(fileUri));

                TransferUtility transferUtility =
                        TransferUtility.builder()
                                .context(getApplicationContext())
                                .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                                .s3Client(s3Client)
                                .build();

                TransferObserver downloadObserver =
                        transferUtility.download("images/" + fileName, localFile);

                downloadObserver.setTransferListener(new TransferListener() {

                    @Override
                    public void onStateChanged(int id, TransferState state) {
                        if (TransferState.COMPLETED == state) {
                            Toast.makeText(getApplicationContext(), "Processing result!", Toast.LENGTH_SHORT).show();

                            tvFileName.setText(fileUri.toString() + "." + getFileExtension(fileUri));
                            Bitmap bmp = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                            imageView.setImageBitmap(bmp);
                        }
                    }

                    @Override
                    public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                        float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                        int percentDone = (int) percentDonef;

                        tvFileName.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                    }

                    @Override
                    public void onError(int id, Exception ex) {
                        ex.printStackTrace();
                    }

                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Upload file before downloading", Toast.LENGTH_LONG).show();
        }


    }

    @Override
    public void onClick(View view) {
        int i = view.getId();

        if (i == R.id.btn_choose_file) {
            showChoosingFile();
        } else if (i == R.id.btn_upload) {
            uploadFile();
            try {
                postRequest();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (i == R.id.btn_download) {
            downloadFile();

        }
    }

    private void showChoosingFile() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), CHOOSING_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CHOOSING_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), fileUri);
                imageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getFileExtension(Uri uri) {

        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        return mime.getExtensionFromMimeType(contentResolver.getType(uri));
    }


//    private void createFile(Context context, Uri srcUri, File dstFile) {
//        try {
//            InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
//            if (inputStream == null) return;
//            OutputStream outputStream = new FileOutputStream(dstFile);
//            IOUtils.copy(inputStream, outputStream);
//            inputStream.close();
//            outputStream.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}