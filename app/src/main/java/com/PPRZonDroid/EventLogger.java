package com.PPRZonDroid;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by bwelton on 9/28/18.
 */

public class EventLogger {
    private File logFile;
    private FileWriter writer;

    public EventLogger(){
        String filename = "FILLERNAME"; //TODO make sure user is selecting the correct filename
        File root = Environment.getExternalStorageDirectory();
        logFile = new File(root + filename);
        try {
            writer = new FileWriter(logFile);
            buildFileHeaders();
        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to initialize the file writer.");
            e.printStackTrace();
        }
    }


    private void buildFileHeaders(){
        try {
            writer.append("Flight Time,");
            writer.append("Time,");
            writer.append("Altitude (m),");
            writer.append("X-Position (m),");
            writer.append("Y-Position (m),");
            writer.append("Event,");
            writer.append("Event Parameter\n");

        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to initialize the headers");
            e.printStackTrace();
        }
    }

    public void logEvent(
            float flightTime,
            float currentTime,
            float altitude,
            float x,
            float y,
            String event,
            float eventParameter) {
        try {
            writer.append(Float.toString(flightTime));
            writer.append(",");
            writer.append(Float.toString(currentTime));
            writer.append(",");
            writer.append(Float.toString(altitude));
            writer.append(",");
            writer.append(Float.toString(x));
            writer.append(",");
            writer.append(Float.toString(y));
            writer.append(",");
            writer.append(event);
            writer.append(",");
            writer.append(Float.toString(eventParameter));
            writer.append("\n");

        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to append a new line of data.");
            e.printStackTrace();
        }
    }

    public void closeLogger(){
        try {
            writer.close();
        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to close the file writer.");
            e.printStackTrace();
        }
    }
}
