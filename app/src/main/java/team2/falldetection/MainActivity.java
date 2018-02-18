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
import android.os.Handler;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.util.Log;
import android.view.View;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;



import static android.hardware.Sensor.TYPE_GYROSCOPE;

public class MainActivity extends Activity implements SensorEventListener {

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private FileWriter writer;
    TextView myLabel;
    public UUID uuid_tp = UUID.fromString("54c7001e-263c-4fb6-bfa7-2dfe5fba0f5b");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myLabel = (TextView)findViewById(R.id.label);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(TYPE_GYROSCOPE);

        Button openButton = (Button)findViewById(R.id.open);
        Button closeButton = (Button)findViewById(R.id.close);

        //Open Button
        openButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    findBT();
                    Log.d("bluetooth", "FindBT done");
                    myLabel.setText("FindBT done");
                    openBT();
                    Log.d("bluetooth", "FindBT done");
                    myLabel.setText("OpenBT done");
                }
                catch (IOException ex) { }
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
                }
                catch (IOException ex) { }
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
        Log.d("bluetooth", "pls start");
        // which speed of sensor delay should we use?
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
    }

    public void onStopClick(View view) {
        try
        {
            closeBT();
            Log.d("bluetooth", "closeBT CLOSED");
        }
        catch (IOException ex) {
            Log.d("bluetooth", "error closing BT...");
        }
        mSensorManager.unregisterListener(this);
        Log.d("bluetooth", "Ending activities");
    }


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

        if (writer != null) {
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

        DateFormat df = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        String date = df.format(Calendar.getInstance().getTime());

        // Print log to the android studio console

        String data = date + " " + sensorName + " " + x_str + "," + y_str + "," + z_str;
        Log.d("run1", data + "\n");

        try
        {
            sendData(data);
        }
        catch (IOException ex) {
            Log.d("bluetooth", "error sending data");
        }

    }

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
        Log.d("bluetooth", "Bluetooth Device Found");
        myLabel.setText("Bluetooth Device Found");
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
            Log.d("bluetooth", "Things are not null");
            myLabel.setText("Things are not null");
        }

        beginListenForData();

        Log.d("bluetooth", "Bluetooth Opened");
        myLabel.setText("Bluetooth Opened");
    }

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
                                            myLabel.setText("I'm running");
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

    void sendData(String msg) throws IOException
    {
        if (msg!=null) {
            myLabel.setText("Message not null!");
            byte[] msgBuffer = msg.getBytes();
            try {
                mmOutputStream.write(msg.getBytes());
            } catch (IOException e) {
                Log.d("bluetooth", "Error with writing message!");
            }
            mmOutputStream.flush();
            Log.d("bluetooth", "Data: " + msg);
            myLabel.setText("Data Sent!");
        } else {
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
            Log.d("bluetooth", "Bluetooth Closed");
            myLabel.setText("Bluetooth Closed");
        } catch (IOException e) {
            Log.d("bluetooth", "Error with closing BT");
        }
    }

}
