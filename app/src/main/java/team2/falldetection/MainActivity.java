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
import java.util.Set;
import java.util.UUID;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import static android.hardware.Sensor.TYPE_GRAVITY;
import static android.hardware.Sensor.TYPE_MAGNETIC_FIELD;

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
    private Sensor mLinearAccelerometer;
    private Sensor mGravity;
    private Sensor mMagneticField;
    TextView myLabel;
    public UUID uuid_tp = UUID.fromString("54c7001e-263c-4fb6-bfa7-2dfe5fba0f5b");
    private float[] gravityValues = null;
    private float[] magneticValues = null;
    private int file_num;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myLabel = (TextView)findViewById(R.id.label);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLinearAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGravity= mSensorManager.getDefaultSensor(TYPE_GRAVITY);
        mMagneticField = mSensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD);;

        file_num = 0;

        Button openButton = (Button)findViewById(R.id.open);
        Button closeButton = (Button)findViewById(R.id.close);

        // checks if external storage is available
        checkExternalMedia();

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

    // starts projecting values on screen
    public void StartRecordingActivity(View view) {
        Intent intent = new Intent(this, StartRecordingActivity.class);
        startActivity(intent);
    }

    // enables bluetooth
    public void onStartClick(View view) {
        Log.d("bluetooth", "pls start bluetooth");
        is_bluetooth_on = true;
        mSensorManager.registerListener(this, mLinearAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_GAME);


    }
    
    // stops sending data to bluetooth
    public void onStopClick(View view) {
        is_bluetooth_on = false;
        mSensorManager.unregisterListener(this);
        myLabel.setText("Stopped sending data to BT");
    }

    // begins listening for sensor data
    // creates file on external storage
    public void onStartLogClick(View view) {
        is_logging_on = true;
        mSensorManager.registerListener(this, mLinearAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_GAME);
        myLabel.setText("Start collecting logs");

        // creating a file on external storage
        File root = android.os.Environment.getExternalStorageDirectory();
        Log.d("external_storage", "\nExternal file system root: "+root);

        File dir = new File (root.getAbsolutePath());
        dir.mkdirs();
        File file = new File(dir, "fall_detection_data_" + file_num + ".txt" );

        try {
            f_outputStream = new FileOutputStream(file, false);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.d("external_storage", "File not found");
            myLabel.setText("File not found");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d("external_storage","File written to "+file);
    }

    // stops logging values and closes file output stream
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

        file_num +=1;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    // stores or sends sensor data every time sensor registers a change
    public void onSensorChanged(SensorEvent event) {

        String sensorName = event.sensor.getName();

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        String data_accel;
        String data_real_accel;

        String x_str = String.valueOf(x);
        String y_str = String.valueOf(y);
        String z_str = String.valueOf(z);

        DateFormat df = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        String date = df.format(new Date(event.timestamp / 1000000));
        //String date = df.format(Calendar.getInstance().getTime());

        data_accel= date + " " + sensorName + " " + x_str + "," + y_str + "," + z_str;


        // calculates absolute acceleration using magnetic/gravity data

        // https://stackoverflow.com/questions/11578636/acceleration-from-devices-coordinate-system-
        // into-absolute-coordinate-system/36477630


        if ((gravityValues != null) && (magneticValues != null)
                && (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION)) {

            float[] deviceRelativeAcceleration = new float[4];
            deviceRelativeAcceleration[0] = event.values[0];
            deviceRelativeAcceleration[1] = event.values[1];
            deviceRelativeAcceleration[2] = event.values[2];
            deviceRelativeAcceleration[3] = 0;

            // Change the device relative acceleration values to earth relative values
            // X axis -> East
            // Y axis -> North Pole
            // Z axis -> Sky

            float[] R = new float[16], I = new float[16], earthAcc = new float[16];

            SensorManager.getRotationMatrix(R, I, gravityValues, magneticValues);

            float[] inv = new float[16];

            android.opengl.Matrix.invertM(inv, 0, R, 0);
            android.opengl.Matrix.multiplyMV(earthAcc, 0, inv, 0, deviceRelativeAcceleration, 0);
            Log.v("gravity", "Real Accel Values: (" + earthAcc[0] + ", " + earthAcc[1] + ", " + earthAcc[2] + ")");

            data_real_accel = date + " " + "Absolute Accelerometer" + " " + earthAcc[0] + "," + earthAcc[1] + "," + earthAcc[2];

            // writes data to file if logging is on
            if (is_logging_on) {
                try {
                    myLabel.setText("Writing data to file number " + file_num);
                    data_accel = data_accel + "\n";
                    f_outputStream.write(data_accel.getBytes());
                    if (data_real_accel != null) {
                        data_real_accel = data_real_accel + "\n";
                        f_outputStream.write(data_real_accel.getBytes());
                    }
                    f_outputStream.flush();
                } catch (Exception e) {
                    myLabel.setText("Error writing data to file number " + file_num);
                    e.printStackTrace();
                }
            }

            // sends data through bluetooth if bluetooth is on
            if (is_bluetooth_on) {
                try
                {
                    myLabel.setText("Sending data!");
                    sendData(data_accel );
                    sendData(data_real_accel);
                }
                catch (IOException ex) {
                    Log.d("bluetooth", "Error sending data");
                    myLabel.setText("Error sending data");
                }
            }


        } else if (event.sensor.getType() == TYPE_GRAVITY) {
            gravityValues = event.values;
            Log.v("gravity", "gravity Values: " + gravityValues[0]);
        } else if (event.sensor.getType() == TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values;
            Log.v("gravity", "magnetic Values: " + magneticValues[0]);
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

    // ------------------------------ Code for Bluetooth ---------------------------------

    // finds bluetooth connection if abailable
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

    // opens bluetooth connection
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

    // listens for data from the laptop
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


    // sends data over bluetooth if bluetooth is enabled
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

    // closes bluetooth connection
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