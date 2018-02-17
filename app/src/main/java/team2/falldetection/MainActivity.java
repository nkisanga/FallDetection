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
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static android.hardware.Sensor.TYPE_GYROSCOPE;

public class MainActivity extends Activity implements SensorEventListener {


    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private FileWriter writer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(TYPE_GYROSCOPE);

        init();
    }

    /** Called when the user taps the START button */
    public void StartRecordingActivity(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, StartRecordingActivity.class);
        startActivity(intent);
    }

    public void onStartClick(View view) {
        // which speed of sensor delay should we use?
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void onStopClick(View view) {
        mSensorManager.unregisterListener(this);
    }

    /*
    public void wrtieFileOnInternalStorage(Context mcoContext, String sFileName, String sBody){
        File file = new File(mcoContext.getFilesDir(),"mydir");
        if(!file.exists()){
            file.mkdir();
        }

        try{
            File gpxfile = new File(file, sFileName);
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(sBody);
            writer.flush();
            writer.close();

        }catch (Exception e){
            e.printStackTrace();

        }
    }

    */

    protected void onResume() {
        super.onResume();

        try {
            writer = new FileWriter("fall_detection_file.txt", true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void onPause() {
        super.onPause();

        if(writer != null) {
            try {
                writer.close();
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

        // Print log to the android studio console

        Log.d("run1", sensorName + " " + x_str+","+y_str+","+z_str+"\n");

        /*

        try {
            writer.write(x_str+","+y_str+","+z_str+"\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        */

    }

    // Code for Bluetooth

    public UUID uuid_tp = UUID.fromString("54c7001e-263c-4fb6-bfa7-2dfe5fba0f5b");

    /*
    public class ConnectThread extends Thread {
        public final BluetoothSocket mmSocket;
        public final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(uuid_tp);
            } catch (IOException e) {
                Log.d("bluetooth", "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run(BluetoothAdapter blueAdapter) {
            // Cancel discovery because it otherwise slows down the connection.
            blueAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.d("bluetooth", "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            // manageMyConnectedSocket(mmSocket);
            Log.d("bluetooth", "idk something works");
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.d("bluetooth", "Could not close the client socket", e);
            }
        }
    }

    */

    private void init() {
        BluetoothAdapter blueAdapter = BluetoothAdapter.getDefaultAdapter();
        if (blueAdapter != null) {
            if (blueAdapter.isEnabled()) {
                Log.d("bluetooth", "works");
                Set<BluetoothDevice> bondedDevices = blueAdapter.getBondedDevices();

                if (bondedDevices.size() > 0) {
                    // There are paired devices. Get the name and address of each paired device.
                    for (BluetoothDevice device : bondedDevices) {
                        String deviceName = device.getName();
                        String deviceHardwareAddress = device.getAddress(); // MAC address
                        Log.d("bluetooth", deviceName + " " + deviceHardwareAddress);
                        //ConnectThread(device);
                        //Log.d("bluetooth", "here1");
                        //ConnectThread.run();
                    }
                }
                Log.e("error", "No appropriate paired devices.");
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                Log.e("error", "Bluetooth is disabled.");
            }
        }
    }

    /*
    public void write(String s) throws IOException {
        outputStream.write(s.getBytes());
    }

    public void run() {
        final int BUFFER_SIZE = 1024;
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytes = 0;
        int b = BUFFER_SIZE;
        while (true) {
            try {
                bytes = inStream.read(buffer, bytes, BUFFER_SIZE - bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    */

}
