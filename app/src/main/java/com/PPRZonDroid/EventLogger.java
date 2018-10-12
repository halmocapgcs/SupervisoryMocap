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
    public static final String WAYPOINT_ALTITUDE_ADJUST = "Waypoint Altitude Adjusted";

    private static final String SD_PATH = "/storage/79CA-1EE6/Android/data/com.PPRZonDroid/files/";

    private FileWriter writer;

    public EventLogger(String filename){
        try {
            File path = new File(SD_PATH);
            if(!path.exists()){
                path.mkdir();
            }
            writer = new FileWriter(
                    new File(SD_PATH + filename));
            buildFileHeaders();
        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to initialize the file writer.");
            e.printStackTrace();
        }
    }


    private void buildFileHeaders(){
        try {
            writer.append("Current Time (s),");
            writer.append("Flight Time (s),");
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
            Telemetry.AirCraft aircraft,
            String event,
            float eventParameter) {
        try {
            writer.append(Long.toString(System.currentTimeMillis()/1000));
            writer.append(",");
            writer.append(aircraft.RawFlightTime);
            writer.append(",");
            writer.append(aircraft.RawAltitude);
            writer.append(",");
            writer.append(Double.toString(aircraft.Position.latitude));
            writer.append(",");
            writer.append(Double.toString(aircraft.Position.longitude));
            writer.append(",");
            writer.append(event);
            writer.append(",");
            writer.append(Float.toString(eventParameter));
            writer.append("\n");
            writer.flush();

        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to append a new line of data.");
            e.printStackTrace();
        }
    }

    protected void closeLogger(){
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to close the file writer.");
            e.printStackTrace();
        }
    }
}
