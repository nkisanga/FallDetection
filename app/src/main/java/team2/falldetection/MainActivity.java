package team2.falldetection;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Button;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Set;
import java.util.UUID;
import java.util.Calendar;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import static android.hardware.Sensor.TYPE_GYROSCOPE;

public class MainActivity extends Activity implements SensorEventListener {

    public static boolean is_bluetooth_on = false;
    public static boolean is_logging_on = false;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    FileOutputStream f_outputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    TextView myLabel;
    public UUID uuid_tp = UUID.fromString("54c7001e-263c-4fb6-bfa7-2dfe5fba0f5b");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myLabel = (TextView)findViewById(R.id.label);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGyroscope = mSensorManager.getDefaultSensor(TYPE_GYROSCOPE);

        Button openButton = (Button)findViewById(R.id.open);
        Button closeButton = (Button)findViewById(R.id.close);

        // checks if external storage is available
        checkExternalMedia();

        // creating a file on external storage
        File root = android.os.Environment.getExternalStorageDirectory();
        Log.d("external_storage", "\nExternal file system root: "+root);

        File dir = new File (root.getAbsolutePath());
        dir.mkdirs();
        File file = new File(dir, "fall_detection_data.txt");

        try {
            f_outputStream = new FileOutputStream(file, false);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.d("external_storage", "File not found");
            myLabel.setText("File not found");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d("external_storage","File written to "+file);;

        //Open Button
        openButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    findBT();
                    Log.d("bluetooth", "FindBT done");
                    myLabel.setText("Bluetooth device found");
                    openBT();
                    Log.d("bluetooth", "OpenBT done");
                    myLabel.setText("Bluetooth device connected");
                }
                catch (IOException ex) {
                    myLabel.setText("Error finding/connecting bluetooth");
                }
            }
        });

        //Close button
        closeButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    closeBT();
                    Log.d("bluetooth", "Bluetooth connection closed");
                    myLabel.setText("Bluetooth connection closed");
                }
                catch (IOException ex) {
                    myLabel.setText("Error closing bluetooth");
                }
            }
        });

    }

    /**
     * Called when the user taps the START button
     */
    public void StartRecordingActivity(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, StartRecordingActivity.class);
        startActivity(intent);
    }

    public void onStartClick(View view) {
        Log.d("bluetooth", "pls start bluetooth");
        // which speed of sensor delay should we use?
        is_bluetooth_on = true;
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);

    }

    public void onStopClick(View view) {
        is_bluetooth_on = false;
        mSensorManager.unregisterListener(this);
        myLabel.setText("Stopped sending data to BT");
    }

    public void onStartLogClick(View view) {
        is_logging_on = true;
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
        myLabel.setText("Start collecting logs");
    }

    public void onStopLogClick(View view) {
        is_logging_on = false;
        mSensorManager.unregisterListener(this);
        myLabel.setText("Stopped collecting logs");
        if(f_outputStream!= null) {
            try {
                f_outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        String sensorName = event.sensor.getName();

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        String x_str = String.valueOf(x);
        String y_str = String.valueOf(y);
        String z_str = String.valueOf(z);

        DateFormat df = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        String date = df.format(new Date(event.timestamp / 1000000));
        //String date = df.format(Calendar.getInstance().getTime());

        // Print log to the android studio console

        String data = date + " " + sensorName + " " + x_str + "," + y_str + "," + z_str;
        Log.d("run", data + "\n");

        // write data to file
        if (is_logging_on) {
            try {
                myLabel.setText("Writing data!");
                data = data + "\n";
                f_outputStream.write(data.getBytes());
                f_outputStream.flush();
            } catch (Exception e) {
                myLabel.setText("Error writing data");
                e.printStackTrace();
            }
        }

        if (is_bluetooth_on) {
            try
            {
                myLabel.setText("Sending data!");
                sendData(data);
            }
            catch (IOException ex) {
                Log.d("bluetooth", "Error sending data");
                myLabel.setText("Error sending data");
            }
        }

    }

    // code for accessing external storage
    // https://stackoverflow.com/questions/8330276/write-a-file-in-external-storage-in-android

    private void checkExternalMedia(){
        boolean mExternalStorageAvailable = false;
        boolean mExternalStorageWriteable = false;
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // Can read and write the media
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // Can only read the media
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            // Can't read or write
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }
        Log.d("external_storage", "\n\nExternal Media: readable="
                +mExternalStorageAvailable+" writable="+mExternalStorageWriteable);

    }
    /*
    private void writeToSDFile(){

        // Find the root of the external storage.
        // http://developer.android.com/guide/topics/data/data-  storage.html#filesExternal

        File root = android.os.Environment.getExternalStorageDirectory();
        Log.d("external_storage", "\nExternal file system root: "+root);

        // See http://stackoverflow.com/questions/3551821/android-write-to-sd-card-folder

        File dir = new File (root.getAbsolutePath());
        dir.mkdirs();
        File file = new File(dir, "fall_detection_data.txt");

        try {
            FileOutputStream f = new FileOutputStream(file);
            PrintWriter pw = new PrintWriter(f);
            pw.println("Plz work");
            pw.println("pretty plz");
            pw.flush();
            pw.close();
            f.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.d("external_storage", "******* File not found. Did you" +
                    " add a WRITE_EXTERNAL_STORAGE permission to the   manifest?");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("external_storage","File written to "+file);

    } */


    // Code for Bluetooth

    void findBT()
    {
        Log.d("bluetooth", "in findBT");
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            Log.d("bluetooth", "No bluetooth adapter available");
            myLabel.setText("No bluetooth adapter available");
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("user-ThinkPad-T430"))
                {
                    mmDevice = device;
                    Log.d("bluetooth", "It's your laptop!");
                    break;
                }
            }
        }
    }

    void openBT() throws IOException
    {
        Log.d("bluetooth", "in openBT with " + uuid_tp.toString());
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid_tp);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        if (mmOutputStream == null) {
            Log.d("bluetooth", "Looks like the outputStream is null...");
            if (mmInputStream == null) {
                Log.d("bluetooth", "And input stream is null, are you even connected to the device?");
            } else {
                Log.d("bluetooth", "Although strangely, input stream has been set.");
            }
        } else {
            Log.d("bluetooth", "Input/Output are not null");
            myLabel.setText("Input/Output are not null");
        }

        beginListenForData();
    }

    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = '\n';  //10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];

        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {

                            Log.d("bluetooth", "here2.8");
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {

                                Log.d("bluetooth", "here3");
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {

                                    Log.d("bluetooth", "here4");
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            Log.d("bluetooth", data);
                                            myLabel.setText(data);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        Log.d("bluetooth", "UMMMM");
                        stopWorker = true;
                    }
                }
            }
        });

        Log.d("bluetooth", "WHAT");
        workerThread.start();
    }

    /*
    public void beginListenForData() {
        byte[] buffer = new byte[1024];  // buffer store for the stream

        int bytes; // bytes returned from read()

        // Keep listening to the InputStream until an exception occurs
        while (true) {
            // Read from the InputStream
            try {
                bytes = mmInputStream.read(buffer);
                final String incomingMessage = new String(buffer, 0, bytes);
                Log.d("bluetooth", "InputStream: " + incomingMessage);

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        myLabel.setText(incomingMessage);
                    }
                });


            } catch (IOException e) {
                Log.e("bluetooth", "write: Error reading Input Stream. " + e.getMessage());
                break;
            }
        }
    }
    */

    /*
    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            Log.d("bluetooth", data);
                                            myLabel.setText(data);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }
    */

    void sendData(String msg) throws IOException
    {
        if (msg!=null) {
            byte[] msgBuffer = msg.getBytes();
            try {
                mmOutputStream.write(msg.getBytes());
            } catch (IOException e) {
                Log.d("bluetooth", "Error with writing message!");
            }
            myLabel.setText("Data Sent!");
            mmOutputStream.flush();
        } else {
            Log.d("bluetooth", "ERROR: Message is null!");
            myLabel.setText("ERROR: Message is null!");
        }
    }

    void closeBT() throws IOException
    {
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
        } catch (IOException e) {
            Log.d("bluetooth", "Error with closing BT");
        }
    }
}