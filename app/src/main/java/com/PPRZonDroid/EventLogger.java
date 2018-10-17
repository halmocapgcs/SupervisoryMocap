package com.PPRZonDroid;

import android.os.Environment;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

/**
 * Created by bwelton on 9/28/18.
 */

public class EventLogger implements Serializable{
    public static final int TAKEOFF = 0;
    public static final int LANDING = 1;
    public static final int EXECUTE = 2;
    public static final int PAUSE = 3;
    public static final int INSPECTION_LAUNCH = 4;
    public static final int INSPECTION_CLOSE = 5;
    public static final int INSPECTION_COMMAND_START = 6;
    public static final int INSPECTION_COMMAND_END = 7;
    public static final int WAYPOINT_CREATE = 8;
    public static final int WAYPOINT_DELETE = 9;
    public static final int WAYPOINT_MOVE = 10;
    public static final int WAYPOINT_ALTITUDE_ADJUST = 11;

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
            writer.append("Manual Command,");
            writer.append("Waypoint Start X,");
            writer.append("Waypoint Start Y,");
            writer.append("Waypoint Start Altitude,");
            writer.append("Waypoint End X,");
            writer.append("Waypoint End Y,");
            writer.append("Waypoint End Altitude\n");

        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to initialize the headers");
            e.printStackTrace();
        }
    }

    protected void logEvent(
            Telemetry.AirCraft aircraft,
            int event,
            float manualCommand) {
        try {
            printRequiredInformation(aircraft, event, manualCommand);
            writer.append("-,"); //waypoint start x
            writer.append("-,"); //waypoint start y
            writer.append("-,"); //waypoint start alt
            writer.append("-,"); //waypoint end x
            writer.append("-,"); //waypoint end y
            writer.append("-,"); //waypoint end alt
            writer.flush();

        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to append a new line of data.");
            e.printStackTrace();
        }
    }

    protected void logWaypointEvent(
            Telemetry.AirCraft aircraft,
            int event,
            float manualCommand,
            @Nullable LatLng startPosition,
            @Nullable LatLng endPosition,
            @Nullable String startAltitude,
            @Nullable String endAltitude) {
        try {
            printRequiredInformation(aircraft, event, manualCommand);

            if(startPosition != null) {
                writer.append(Double.toString(startPosition.latitude));
                writer.append(",");
                writer.append(Double.toString(startPosition.longitude));
                writer.append(",");
            }else{
                writer.append("-,");
                writer.append("-,");
            }
            writer.append(startAltitude != null ? startAltitude : "-,");
            writer.append(",");

            if(endPosition != null) {
                writer.append(Double.toString(endPosition.latitude));
                writer.append(",");
                writer.append(Double.toString(endPosition.longitude));
                writer.append(",");
            }else{
                writer.append("-,");
                writer.append("-,");
            }
            writer.append(endAltitude != null ? endAltitude : "-");
            writer.append("\n");

            writer.flush();

        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to append a new line of data.");
            e.printStackTrace();
        }
    }

    private void printRequiredInformation(
            Telemetry.AirCraft aircraft,
            int event,
            float manualCommand) {
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
            writer.append(Integer.toString(event));
            writer.append(",");
            writer.append(Float.toString(manualCommand));
            writer.append("\n");

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
