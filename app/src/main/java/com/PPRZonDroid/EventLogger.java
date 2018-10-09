package com.PPRZonDroid;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

/**
 * Created by bwelton on 9/28/18.
 */

public class EventLogger implements Serializable{
    public static final String TAKEOFF = "Takeoff";
    public static final String LANDING = "Land";
    public static final String EXECUTE = "Flightplan Executed";
    public static final String PAUSE = "Flightplan Paused";
    public static final String INSPECTION_LAUNCH = "Inspection Mode Launched";
    public static final String INSPECTION_CLOSE = "Inspection Mode Closed";
    public static final String INSPECTION_COMMAND_START = "Inspection Command Started";
    public static final String INSPECTION_COMMAND_END = "Inspection Command Ended";
    public static final String WAYPOINT_CREATE = "Waypoint Created";
    public static final String WAYPOINT_DELETE = "Waypoint Deleted";
    public static final String WAYPOINT_MOVE = "Waypoint Move";
    public static final String WAYPOIN_ALTITUDE_MOVE = "Waypoint Altitude Adjusted";

    private FileWriter writer;

    public EventLogger(){
        String filename = "FILLERNAME"; //TODO make sure user is selecting the correct filename
        File root = Environment.getExternalStorageDirectory();
        File logFile = new File(root + filename);
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

    protected void logEvent(
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

    protected void closeLogger(){
        try {
            writer.close();
        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to close the file writer.");
            e.printStackTrace();
        }
    }
}
