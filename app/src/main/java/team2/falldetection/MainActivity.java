package team2.falldetection;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /** Called when the user taps the START button */
    public void StartRecordingActivity(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, StartRecordingActivity.class);
        startActivity(intent);
    }

}