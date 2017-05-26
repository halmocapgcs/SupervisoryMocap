/*
 * Copyright (C) 2014 Savas Sen - ENAC UAV Lab
 *
 * This file is part of paparazzi..
 *
 * paparazzi is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * paparazzi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with paparazzi; see the file COPYING.  If not, write to
 * the Free Software Foundation, 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 */

/*
 * This is the main Activity class
 *
 * NOTE: The additions made for HAL's supervisory variant are as follow
 *     --ability to add waypoints
 *     --mapping of latitudes onto a ground overlay of the room
 *     --showing only certain waypoints
 *     --creating a looping feature for the Next Waypoint! block to execute flight plan
 *     --custom dialogs for wp removal and altitude adjustment
 *     --timer has been shortened from 3000 ms to 900 ms
 *     --lines connecting waypoints
 *     --icon now displays altitude rather than title
 *
 *     The block ID and waypoint ID relevant to the looping through the added waypoints uses BlocId
 *     7 and waypoint 7. These have been hard coded into the activity and are flight plan specific
 *     to the ARdrone2 vicon_rotorcraft as of 5/24/17. If that flight plan is modified, the relevant
 *     ID numbers MUST be recalculated. The same is true for the selected visible waypoints.
 */

package com.PPRZonDroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import static java.lang.Double.parseDouble;


public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

  //TODO ! FLAG MUST BE 'FALSE' FOR PLAY STORE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  boolean DEBUG=false;

  //Application Settings
  public static final String SERVER_IP_ADDRESS = "server_ip_adress_text";
  public static final String SERVER_PORT_ADDRESS = "server_port_number_text";
  public static final String LOCAL_PORT_ADDRESS = "local_port_number_text";
  public static final String MIN_AIRSPEED = "minimum_air_speed";
  public static final String USE_GPS = "use_gps_checkbox";
  public static final String Control_Pass = "app_password";
  public static final String BLOCK_C_TIMEOUT = "block_change_timeout";
  public static final String DISABLE_SCREEN_DIM = "disable_screen_dim";

  public Telemetry AC_DATA;                       //Class to hold&proces AC Telemetry Data
  boolean AcLocked = false;
  TextView DialogTextWpName;
  Button DialogButtonConfirm;
  Button Alt_DialogButtonConfirm;
  Button Alt_ResToDlAlt;
  Button Alt_ResToDesAlt;
  EditText DialogWpAltitude;
  TextView DialogTextWpLat;
  TextView DialogTextWpLon;
  Button DialogAltUp;
  Button DialogAltDown;
  boolean ShowOnlySelected = true;
  String AppPassword;
  //AP_STATUS
  TextView TextViewApMode;
  TextView TextViewGpsMode;
  TextView TextViewStateFilterMode;
  TextView TextViewFlightTime;
  TextView TextViewBattery;
  TextView TextViewSpeed;
  TextView TextViewAirspeed;
  //AC Blocks
  ArrayList<BlockModel> BlList = new ArrayList<BlockModel>();
  BlockListAdapter mBlListAdapter;
  ListView BlListView;
  SharedPreferences AppSettings;                  //App Settings Data
  Float MapZoomLevel = 14.0f;
  //Ac Names
  ArrayList<Model> AcList = new ArrayList<Model>();
  AcListAdapter mAcListAdapter;
  ListView AcListView;
  boolean TcpSettingsChanged;
  boolean UdpSettingsChanged;
  int DialogAcId;
  int DialogWpId;
  //UI components (needs to be bound onCreate
  private GoogleMap mMap;
  private TextView MapAlt;
  private TextView MapThrottle;
  private ImageView Pfd;

  private Button Button_ConnectToServer;
  private ToggleButton ChangeVisibleAcButon;
  private DrawerLayout mDrawerLayout;
  //Dialog components
  private Dialog WpDialog;
  private Dialog AltDialog;
  private Dialog RemoveDialog;
  //Position descriptions >> in future this needs to be an array or struct
  private LatLng AC_Pos = new LatLng(43.563958, 1.481391);
  private String SendStringBuf;
  private boolean AppStarted = false;             //App started indicator
  private CharSequence mTitle;

  //variables for adding marker feature and for connecting said markers
  public LinkedList<Marker> mMarkerHead = new LinkedList<Marker>();
  public LinkedList<LatLng> pathPoints = new LinkedList<LatLng>();
  public LinkedList<LatLng> pastPoints = new LinkedList<LatLng>();
  public int mrkIndex = 0;
  public Polyline path;
  public Polyline pastPath;
  public boolean firstRun = true;
  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //Background task to read and write telemery msgs
  private boolean isTaskRunning;

  private boolean DisableScreenDim;

  private CountDownTimer BL_CountDown;
  private int BL_CountDownTimerValue;
  private int JumpToBlock;
  private int BL_CountDownTimerDuration;

  private ArrayList<Model> generateDataAc() {
    AcList = new ArrayList<Model>();
    return AcList;
  }

  private ArrayList<BlockModel> generateDataBl() {
    BlList = new ArrayList<BlockModel>();
    return BlList;
  }

  private Thread mTCPthread;

  private NumberPicker mNumberPickerThus,mNumberPickerHuns, mNumberPickerTens,mNumberPickerOnes;

  /**
   * Setup TCP and UDP connections of Telemetry class
   */
  private void setup_telemetry_class() {

    //Create Telemetry class
    AC_DATA = new Telemetry();

    //Read & setup Telemetry class
    AC_DATA.ServerIp = AppSettings.getString(SERVER_IP_ADDRESS, getString(R.string.pref_ip_address_default));
    AC_DATA.ServerTcpPort = Integer.parseInt(AppSettings.getString(SERVER_PORT_ADDRESS, getString(R.string.pref_port_number_default)));
    AC_DATA.UdpListenPort = Integer.parseInt(AppSettings.getString(LOCAL_PORT_ADDRESS, getString(R.string.pref_local_port_number_default)));
    AC_DATA.AirSpeedMinSetting = parseDouble(AppSettings.getString(MIN_AIRSPEED, "10"));
    AC_DATA.DEBUG=DEBUG;

    AC_DATA.GraphicsScaleFactor = getResources().getDisplayMetrics().density;
    AC_DATA.prepare_class();

    //AC_DATA.tcp_connection();
    //AC_DATA.mTcpClient.setup_tcp();
    AC_DATA.setup_udp();
  }

  /**
   * Bound UI items
   */
  private void set_up_app() {



    //if ( Debug.isDebuggerConnected() ) DEBUG= true;
    //else DEBUG=false;

      //Get app settings
    AppSettings = PreferenceManager.getDefaultSharedPreferences(this);
    AppSettings.registerOnSharedPreferenceChangeListener(this);

    AppPassword = (AppSettings.getString("app_password", ""));

    DisableScreenDim = AppSettings.getBoolean("disable_screen_dim", true);

    /* Setup waypoint dialog */
    WpDialog = new Dialog(this);
    WpDialog.setContentView(R.layout.wp_modified);

    DialogTextWpName = (TextView) WpDialog.findViewById(R.id.textViewWpName);
    DialogButtonConfirm = (Button) WpDialog.findViewById(R.id.buttonConfirm);
    DialogWpAltitude = (EditText) WpDialog.findViewById(R.id.editTextAltitude);
    DialogTextWpLat = (TextView) WpDialog.findViewById(R.id.textViewLatitude);
    DialogTextWpLon = (TextView) WpDialog.findViewById(R.id.textViewLongitude);
    DialogAltUp = (Button) WpDialog.findViewById(R.id.buttonplust);
    DialogAltDown = (Button) WpDialog.findViewById(R.id.buttonmint);

    DialogAltDown.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Double alt = parseDouble(DialogWpAltitude.getText().toString()) - 10;
        DialogWpAltitude.setText(alt.toString());
      }
    });

    DialogAltUp.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Double alt = parseDouble(DialogWpAltitude.getText().toString()) + 10;
        DialogWpAltitude.setText(alt.toString());
      }
    });

    DialogButtonConfirm.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {

        SendStringBuf = "PPRZonDroid MOVE_WAYPOINT " + DialogAcId + " " + DialogWpId +
                " " + DialogTextWpLat.getText() + " " + DialogTextWpLon.getText() + " " + DialogWpAltitude.getText();
        send_to_server(SendStringBuf, true);
        WpDialog.dismiss();
      }
    });

    /* Setup altitude dialog */
    AltDialog = new Dialog(this);
    AltDialog.setContentView(R.layout.alt_modified);

    Alt_DialogButtonConfirm= (Button) AltDialog.findViewById(R.id.buttonConfAlt);

      Alt_DialogButtonConfirm.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {

              String DlAlt ="";

              DlAlt= DlAlt + mNumberPickerThus.getValue();
              DlAlt= DlAlt + mNumberPickerHuns.getValue();
              DlAlt= DlAlt + mNumberPickerTens.getValue();
              DlAlt= DlAlt + mNumberPickerOnes.getValue();
              SendStringBuf = "dl DL_SETTING " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_AltID +  " " + DlAlt;
              //Log.d("PPRZ_info", SendStringBuf );
              send_to_server(SendStringBuf, true);

              AltDialog.dismiss();
          }
      });

    Alt_ResToDlAlt =  (Button) AltDialog.findViewById(R.id.buttonResToDlAlt);
      Alt_ResToDlAlt.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {

             fill_alt(AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_DlAlt);

          }
      });


    Alt_ResToDesAlt =  (Button) AltDialog.findViewById(R.id.buttonResToAcAlt);
      Alt_ResToDesAlt.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {

              fill_alt(AC_DATA.AircraftData[AC_DATA.SelAcInd].Altitude);

          }
      });

    mNumberPickerThus = (NumberPicker) AltDialog.findViewById(R.id.numberPickerThus);
    mNumberPickerThus.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    mNumberPickerThus.setMinValue(0);
    mNumberPickerThus.setMaxValue(9);

    mNumberPickerHuns = (NumberPicker) AltDialog.findViewById(R.id.numberPickerHuns);
    mNumberPickerHuns.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    mNumberPickerHuns.setMinValue(0);
    mNumberPickerHuns.setMaxValue(9);

    mNumberPickerTens = (NumberPicker) AltDialog.findViewById(R.id.numberPickerTens);
    mNumberPickerTens.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    mNumberPickerTens.setMinValue(0);
    mNumberPickerTens.setMaxValue(9);

    mNumberPickerOnes = (NumberPicker) AltDialog.findViewById(R.id.numberPickerOnes);
    mNumberPickerOnes.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    mNumberPickerOnes.setMinValue(0);
    mNumberPickerOnes.setMaxValue(9);

    //Setup left drawer
    mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

    //Setup AC List
    setup_ac_list();


    //Setup Block list
    setup_block_list();



    //Set map zoom level variable (if any);
    if (AppSettings.contains("MapZoomLevel")) {
      MapZoomLevel = AppSettings.getFloat("MapZoomLevel", 17.0f);
    }

    //continue to bound UI items
    MapAlt = (TextView) findViewById(R.id.Alt_On_Map);
    MapThrottle = (TextView) findViewById(R.id.ThrottleText);
    Pfd = (ImageView) findViewById(R.id.imageView_Pfd);
    //mToolTip = (ImageView) findViewById(R.id.imageFeedBack );

    Button_ConnectToServer = (Button) findViewById(R.id.Button_ConnectToServer);
    setup_map_ifneeded();


    ChangeVisibleAcButon = (ToggleButton) findViewById(R.id.toggleButtonVisibleAc);
    ChangeVisibleAcButon.setSelected(false);

    TextViewApMode = (TextView) findViewById(R.id.Ap_Status_On_Map);
    TextViewGpsMode = (TextView) findViewById(R.id.Gps_Status_On_Map);
    TextViewFlightTime = (TextView) findViewById(R.id.Flight_Time_On_Map);
    TextViewBattery = (TextView) findViewById(R.id.Bat_Vol_On_Map);
    TextViewStateFilterMode = (TextView) findViewById(R.id.State_Filter_On_Map);
    TextViewSpeed = (TextView) findViewById(R.id.SpeedText);
    TextViewAirspeed = (TextView) findViewById(R.id.AirSpeed_On_Map);
    TextViewAirspeed.setVisibility(View.INVISIBLE);

  }

  private  void setup_block_list() {

      //Block Listview
      setup_counter();

      mBlListAdapter = new BlockListAdapter(this, generateDataBl());

      //
      BlListView = (ListView) findViewById(R.id.BlocksList);
      View FlightControls = getLayoutInflater().inflate(R.layout.flight_controls, null);
      BlListView.addHeaderView(FlightControls);
      BlListView.setAdapter(mBlListAdapter);
      BlListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

              BL_CountDown.cancel();
              BL_CountDownTimerValue=BL_CountDownTimerDuration;
              mBlListAdapter.ClickedInd = position-1;
              JumpToBlock= position-1;

              BL_CountDown.start();
              mBlListAdapter.notifyDataSetChanged();


          }
      });



  }

  private void setup_counter() {
      //Get timeout from appsettings
      BL_CountDownTimerDuration = Integer.parseInt(AppSettings.getString("block_change_timeout", "3")) *300;
      BL_CountDownTimerValue =BL_CountDownTimerDuration;
      //Setup timer for progressbar of clicked block
      BL_CountDown = new CountDownTimer(BL_CountDownTimerDuration, 100 ) {
          @Override
          public void onTick(long l) {

              if (BL_CountDownTimerDuration>0) {
                  BL_CountDownTimerValue= BL_CountDownTimerValue - 100;
                  mBlListAdapter.BlProgress.setProgress(((BL_CountDownTimerValue*100) /BL_CountDownTimerDuration)) ;
                  //if (DEBUG) Log.d("PPRZ_info", "Counter value: " + process_val );
              }
          }

          @Override
          public void onFinish() {
              if (BL_CountDownTimerDuration>0) {

                  BL_CountDownTimerValue= BL_CountDownTimerValue - 100;
                  mBlListAdapter.BlProgress.setProgress(((BL_CountDownTimerValue*100) /BL_CountDownTimerDuration)) ;
                  BL_CountDownTimerValue = BL_CountDownTimerDuration;
                  mBlListAdapter.ClickedInd = -1;
                  set_selected_block(JumpToBlock, false);
                  mBlListAdapter.notifyDataSetChanged();

              }
          }

      };
  }

  private void setup_ac_list() {
      mAcListAdapter = new AcListAdapter(this, generateDataAc());

      // if extending Activity 2. Get ListView from activity_main.xml
      AcListView = (ListView) findViewById(R.id.AcList);
      View AppControls = getLayoutInflater().inflate(R.layout.appcontrols, null);

      //First item will be app controls
      //This workaround applied to for app controls sliding to top if user slides them.
      AcListView.addHeaderView(AppControls);

      // 3. setListAdapter
      AcListView.setAdapter(mAcListAdapter);
      //Create onclick listener
      AcListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
          if (position >= 1) {
           BL_CountDown.cancel();
           BL_CountDownTimerValue=BL_CountDownTimerDuration;
           mBlListAdapter.ClickedInd=-1;
           mBlListAdapter.notifyDataSetChanged();

           view.setSelected(true);
           set_selected_ac(position - 1,true);
           mDrawerLayout.closeDrawers();
           }

            }
        });

  }

   /**
   * Set Selected Block
   *
   * @param BlocId
   */
  private void set_selected_block(int BlocId,boolean ReqFromServer) {

    //AC_DATA.AircraftData[AC_DATA.SelAcInd].SelectedBlock = BlocId;

    if (ReqFromServer){
        mBlListAdapter.SelectedInd = BlocId + 1;
        mBlListAdapter.notifyDataSetChanged();

        if(BlocId == 8){

            Marker popped = mMarkerHead.removeFirst();
            popped.setIcon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                    "grey", popped.getTitle(), AC_DATA.GraphicsScaleFactor)));

            /* current implementation simply removes the connecting polyline from previous wp
             * and then adds gray polyline to indicate that a wp has been reached */

            pathPoints.removeFirst();
            adjust_marker_lines();


            /* code below removes the waypoint upon arrival, unused in current implementation */

            //popped.remove();
            //mrkIndex--;
            //adjust_marker_titles();

            //timer used to pause between wps to give the user some breathing room
            new CountDownTimer(500, 100){
                @Override
                public void onTick(long l) {
                }
                @Override
                public void onFinish() {
                }
            };

            //continues the loop
            set_selected_block(7, false);
        }
    }
    //added feature here to loop through the Next Waypoint! block, which is block number 7
    else if(BlocId == 7 && mMarkerHead.peek() != null){
        Marker newNEXT = mMarkerHead.peek();
        LatLng coordNEXT = convert_to_google(newNEXT.getPosition());
        String altitude = newNEXT.getSnippet();
        //Note below that 31 is the AC id for our ardrone and 7 is the waypoint number for NEXT
        SendStringBuf = "PPRZonDroid MOVE_WAYPOINT " + 31 + " " + 7 +
                " " + coordNEXT.latitude + " " + coordNEXT.longitude + " " + altitude;
        send_to_server(SendStringBuf, true);

        //timer is needed to ensure paparazzi system corrects waypoint first
        new CountDownTimer(1000, 1000){
            @Override
            public void onTick(long l) {}

            @Override
            public void onFinish() {
                send_to_server("PPRZonDroid JUMP_TO_BLOCK " + 31 + " " + 7, true);
            }
        }.start();
    }
    else {
        //Notify server
        send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + BlocId, true);

    }
  }

  /**
   * Set Selected airplane
   *
   * @param AcInd
   * @param centerAC : want to center AC?
   */
  private void set_selected_ac(int AcInd,boolean centerAC) {

    AC_DATA.SelAcInd = AcInd;
    //Set Title;
    setTitle(AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name);

    refresh_block_list();
    set_marker_visibility();

    //set airspeed visibility
    if (AC_DATA.AircraftData[AC_DATA.SelAcInd].AirspeedEnabled) {
        TextViewAirspeed.setVisibility(View.VISIBLE);
        TextViewAirspeed.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].AirSpeed + " m/s");
    }
      else
    {
        TextViewAirspeed.setVisibility(View.INVISIBLE);
    }

    for (int i = 0; i <= AC_DATA.IndexEnd; i++) {
      //Is AC ready to show on ui?
      //Check if ac i visible and its position is changed
      if (AC_DATA.AircraftData[i].AC_Enabled && AC_DATA.AircraftData[i].isVisible && AC_DATA.AircraftData[i].AC_Marker != null) {

        AC_DATA.AircraftData[i].AC_Logo = AC_DATA.muiGraphics.create_ac_icon(AC_DATA.AircraftData[i].AC_Type, AC_DATA.AircraftData[i].AC_Color, AC_DATA.GraphicsScaleFactor, (i == AC_DATA.SelAcInd));
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[i].AC_Logo);
        AC_DATA.AircraftData[i].AC_Marker.setIcon(bitmapDescriptor);
        //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(AC_DATA.AircraftData[AC_DATA.SelAcInd].Position, MapZoomLevel), 1500, null);
          if (centerAC)
          {
              //center_aircraft();
          }

      }

    }

      mAcListAdapter.SelectedInd = AcInd;
      mAcListAdapter.notifyDataSetChanged();
      refresh_ac_list();

  }

  /**
   * Refresh block lisst on right
   */
  private void refresh_block_list() {

    int i;
    BlList.clear();

    for (i = 0; i < AC_DATA.AircraftData[AC_DATA.SelAcInd].BlockCount; i++) {
      BlList.add(new BlockModel(AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Blocks[i].BlName));
    }
    mBlListAdapter.BlColor = AC_DATA.muiGraphics.get_color(AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Color);
    mBlListAdapter.SelectedInd = AC_DATA.AircraftData[AC_DATA.SelAcInd].SelectedBlock;


      AnimationSet set = new AnimationSet(true);

      Animation animation = new AlphaAnimation(0.0f, 1.0f);

      animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, +1.0f,
              Animation.RELATIVE_TO_SELF, 0.0f,
              Animation.RELATIVE_TO_SELF, 0.0f,
              Animation.RELATIVE_TO_SELF, 0.0f
      );
      animation.setDuration(250);
      set.addAnimation(animation);

      LayoutAnimationController controller =
              new LayoutAnimationController(set, 0.25f);
      BlListView.setLayoutAnimation(controller);


    mBlListAdapter.notifyDataSetChanged();
  }

  //Bound map (if not bounded already)
  private void setup_map_ifneeded() {
    // Do a null check to confirm that we have not already instantiated the map.
    if (mMap == null) {
      // Try to obtain the map from the SupportMapFragment.
      mMap = ((MapFragment) getFragmentManager()
              .findFragmentById(R.id.map)).getMap();
      // Check if we were successful in obtaining the map.
      if (mMap != null) {
        setup_map();
      }
    }
  }

  //Setup map & zoom listener
  private void setup_map() {

    GoogleMapOptions mMapOptions = new GoogleMapOptions();

    mMap.getUiSettings().setTiltGesturesEnabled(false);
    //Read device settings for Gps usage.
    mMap.setMyLocationEnabled(AppSettings.getBoolean("use_gps_checkbox", true));

    //Set map type
    mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

    //Set zoom level and the position of the lab
      LatLng labOrigin = new LatLng(36.005417, -78.940984);
      mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(labOrigin, 50));
      CameraPosition rotated = new CameraPosition.Builder()
              .target(labOrigin)
              .zoom(50)
              .bearing(90.0f)
              .build();
      mMap.moveCamera(CameraUpdateFactory.newCameraPosition(rotated));

      //Disable zoom and gestures to lock the image in place
      mMap.getUiSettings().setAllGesturesEnabled(false);
      mMap.getUiSettings().setZoomGesturesEnabled(false);

      //Create the ground overlay
      BitmapDescriptor labImage = BitmapDescriptorFactory.fromResource(R.drawable.realroomtemp);
      GroundOverlay trueMap = mMap.addGroundOverlay(new GroundOverlayOptions()
              .image(labImage)
              .position(labOrigin, (float) 77.15)
              .bearing(90.0f));


    //Setup markers drag listeners that update alt and polylines when moved
    mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {

      @Override
      public void onMarkerDragStart(Marker marker) {
          int index = mMarkerHead.indexOf(marker);
          pathPoints.set(index + 1, marker.getPosition());
          path.setPoints(pathPoints);
      }

      @Override
      public void onMarkerDragEnd(Marker marker) {
          if(!mMarkerHead.contains(marker)) {
              waypoint_changed(marker.getId(), marker.getPosition(), "Waypoint changed");
          }
          //else statement ensures we do not send a paparazzi message for a waypoint that doesn't exist
          else{
			  launch_altitude_dialog(marker, "OLD");
          }
      }

      @Override
      public void onMarkerDrag(Marker marker) {
          int index = mMarkerHead.indexOf(marker);
          pathPoints.set(index + 1, marker.getPosition());
          path.setPoints(pathPoints);
      }
    });

    //listener to add in functionality of adding a waypoint and adding to data structure for
    //path execution
    mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
        @Override
        public void onMapLongClick(LatLng latLng) {
            Marker newMarker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .draggable(true)
                .anchor(0.5f, 0.378f)
                .title(Integer.toString(mrkIndex + 1))
                .snippet(Float.toString(1.0f))
                .icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                    "red", "?", AC_DATA.GraphicsScaleFactor))));
            launch_altitude_dialog(newMarker, "NEW");

            mMarkerHead.add(newMarker);
			pathPoints.add(newMarker.getPosition());
            pastPoints.add(newMarker.getPosition());
			if((mrkIndex == 0)){
                pathPoints.addFirst(convert_to_lab(new LatLng(36.005417, -78.940984)));
				path = mMap.addPolyline(new PolylineOptions()
                        .addAll(pathPoints)
						.width(4)
						.color(Color.RED));
                //this lays the anchor point for the later used grey polylines
                if(firstRun){
                    pastPath = mMap.addPolyline(new PolylineOptions()
                        .addAll(pastPoints)
                        .width(4)
                        .color(Color.GRAY));
                    firstRun = false;
                }
			}
			else{
				path.setPoints(pathPoints);
			}
            mrkIndex++;
        }
    });

    //listener to add in remove on click functionality and altitude control
    mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
        public boolean onMarkerClick(final Marker marker){
            AlertDialog adjustDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Adjust Waypoint")
                    .setMessage("Click below to adjust the altitude or remove the waypoint")
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Altitude", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i){
                            launch_altitude_dialog(marker, "OLD");
                        }
                    })
                    .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i){
                            remove_waypoint_dialog(marker);
                        }
                    })
                    .create();
            adjustDialog.show();
            return true;
        }
    });

  }

  /**
   * Check if everthink is ok with modified wp
   *
   * @param WpID
   * @return
   */
  private boolean is_ac_marker(String WpID) {

    for (int AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {     //Find waypoint which is changed!

      if ((null == AC_DATA.AircraftData[AcInd].AC_Marker))
        continue; //we dont have data for this wp yet

      if (AC_DATA.AircraftData[AcInd].AC_Marker.getId().equals(WpID)) {
        set_selected_ac(AcInd,true);
        Toast.makeText(getApplicationContext(), "Selected A/C =  " + AC_DATA.AircraftData[AcInd].AC_Name, Toast.LENGTH_SHORT).show();
        return true;

      }


    }
    return false;

  }

  /**
   * Send string to server
   *
   * @param StrToSend
   * @param ControlString Whether String is to control AC or request Ac data.If string is a control string then app pass will be send also
   */
  private void send_to_server(String StrToSend, boolean ControlString) {
    //Is it a control string ? else ->Data request
    if (ControlString) {
      AC_DATA.SendToTcp = AppPassword + " " + StrToSend;
    } else {
      AC_DATA.SendToTcp = StrToSend;
    }
  }

  /**
   * Play warning sound if airspeed goes below the selected value
   *
   * @param context
   * @throws IllegalArgumentException
   * @throws SecurityException
   * @throws IllegalStateException
   * @throws IOException
   */
  public void play_sound(Context context) throws IllegalArgumentException,
          SecurityException,
          IllegalStateException,
          IOException {

    Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    MediaPlayer mMediaPlayer = new MediaPlayer();
    mMediaPlayer.setDataSource(context, soundUri);
    final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    //Set volume max!!!
    audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, audioManager.getStreamMaxVolume(audioManager.STREAM_SYSTEM), 0);

    if (audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) != 0) {
      mMediaPlayer.setAudioStreamType(AudioManager.STREAM_SYSTEM);
      mMediaPlayer.setLooping(true);
      mMediaPlayer.prepare();
      mMediaPlayer.start();
    }
  }

  private void refresh_ac_list() {


    //Create or edit aircraft list
    int i;
    for (i = 0; i <= AC_DATA.IndexEnd; i++) {


      if (AC_DATA.AircraftData[i].AC_Enabled) {
        AcList.set(i, new Model(AC_DATA.AircraftData[i].AC_Logo, AC_DATA.AircraftData[i].AC_Name, AC_DATA.AircraftData[i].Battery));

      } else {
        if (AC_DATA.AircraftData[i].AcReady) {
          AcList.add(new Model(AC_DATA.AircraftData[i].AC_Logo, AC_DATA.AircraftData[i].AC_Name, AC_DATA.AircraftData[i].Battery));
          AC_DATA.AircraftData[i].AC_Enabled = true;
        } else {
          //AC data is not ready yet this should be
          return;
        }
      }
    }

  }

//  private void refresh_map_lines(int AcInd) {
//
//    //Create the polyline object of ac if it hasn't been created before
//    if (null == AC_DATA.AircraftData[AcInd].Ac_PolLine) {
//          PolylineOptions mPolylineOptions = new PolylineOptions()
//                  .addAll(AC_DATA.AircraftData[AcInd].AC_Path)
//                  .width(2 * AC_DATA.GraphicsScaleFactor)
//                  .color(AC_DATA.muiGraphics.get_color(AC_DATA.AircraftData[AcInd].AC_Color))
//                  .geodesic(false);
//          AC_DATA.AircraftData[AcInd].Ac_PolLine = mMap.addPolyline(mPolylineOptions);
//     }
//
//    //Refresh polyline with new values
//    AC_DATA.AircraftData[AcInd].Ac_PolLine.setPoints(AC_DATA.AircraftData[AcInd].AC_Path);
//
//    //Clean the flags
//    if (AcInd == AC_DATA.SelAcInd || ShowOnlySelected == false) {
//      AC_DATA.AircraftData[AcInd].Ac_PolLine.setVisible(true);
//    } else {
//      AC_DATA.AircraftData[AcInd].Ac_PolLine.setVisible(false);
//    }
//
//  }

  /**
   * Amm markers of given AcIndex to map. AcIndex is not Ac ID!!!
   *
   * @param AcIndex
   */
  private void add_markers_2_map(int AcIndex) {


    if (AC_DATA.AircraftData[AcIndex].isVisible && AC_DATA.AircraftData[AcIndex].AC_Enabled) {
      AC_DATA.AircraftData[AcIndex].AC_Marker = mMap.addMarker(new MarkerOptions()
                      .position(convert_to_lab(AC_DATA.AircraftData[AcIndex].Position))
                      .anchor((float) 0.5, (float) 0.5)
                      .flat(true).rotation(Float.parseFloat(AC_DATA.AircraftData[AcIndex].Heading))
                      .title(AC_DATA.AircraftData[AcIndex].AC_Name)
                      .draggable(false)
                      .icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[AcIndex].AC_Logo))
      );

      AC_DATA.AircraftData[AcIndex].AC_Carrot_Marker = mMap.addMarker(new MarkerOptions()
                      .position(convert_to_lab(AC_DATA.AircraftData[AcIndex].AC_Carrot_Position))
                      .anchor((float) 0.5, (float) 0.5)
                      .draggable(false)
                      .icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[AcIndex].AC_Carrot_Logo))
      );
    }
  }

  //REfresh markers
  private void refresh_markers() {

    //Method below is better..
    //
    int i;

    for (i = 0; i <= AC_DATA.IndexEnd; i++) {

      //Is AC ready to show on ui?
      //if (!AC_DATA.AircraftData[i].AC_Enabled)  return;

      if (null == AC_DATA.AircraftData[i].AC_Marker) {
        add_markers_2_map(i);
      }

      //Check if ac i visible and its position is changed
      if (AC_DATA.AircraftData[i].AC_Enabled && AC_DATA.AircraftData[i].isVisible && AC_DATA.AircraftData[i].AC_Position_Changed) {
        AC_DATA.AircraftData[i].AC_Marker.setPosition(convert_to_lab(AC_DATA.AircraftData[i].Position));
        AC_DATA.AircraftData[i].AC_Marker.setRotation(Float.parseFloat(AC_DATA.AircraftData[i].Heading));
        AC_DATA.AircraftData[i].AC_Carrot_Marker.setPosition(convert_to_lab(AC_DATA.AircraftData[i].AC_Carrot_Position));
        AC_DATA.AircraftData[i].AC_Position_Changed = false;
      }

    }


    //Check markers

    if (AC_DATA.NewMarkerAdded)   //Did we add any markers?
    {
      int AcInd;
      for (AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {     //Search thru all aircrafts and check if they have marker added flag

        if (AC_DATA.AircraftData[AcInd].NewMarkerAdded) {   //Does this aircraft has an added marker data?
          int MarkerInd = 1;
          //Log.d("PPRZ_info", "trying to show ac markers of "+AcInd);
          //Search aircraft markers which has name but doesn't have marker
          for (MarkerInd = 1; (MarkerInd < AC_DATA.AircraftData[AcInd].NumbOfWps - 1); MarkerInd++) {

              if ((AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpPosition == null) || (AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker != null))
                  continue; //we dont have data for this wp yet

              if (DEBUG) Log.d("PPRZ_info", "New marker added for Ac id: " + AcInd + " wpind:" + MarkerInd);

              //this if statement is used to specify what waypoints are shown on the map
              //in this case, just the origin. This number is variable depending on the flight plan
              //and will need to be updated if changes are made
              if(MarkerInd == 8){
                  AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker = mMap.addMarker(new MarkerOptions()
                    .position(convert_to_lab(AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpPosition))
                    .title(AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpName)
                    .draggable(false)
                    .anchor(0.5f,0.378f)
                    .icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarkerIcon)));
                  AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker.setVisible(AcMarkerVisible(AcInd));


              if ("_".equals(AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpName.substring(0, 1))) {
                  AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker.setVisible(false);
              }}



            AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].MarkerModified = false;
          }


          AC_DATA.AircraftData[AcInd].NewMarkerAdded = false;
        }
      }

      AC_DATA.NewMarkerAdded = false;
    }


    //Handle marker modified msg

    if (AC_DATA.MarkerModified)   //
    {

      //Log.d("PPRZ_info", "Marker modified for whom?");
      int AcInd;
      for (AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {     //Search thru all aircrafts and check if they have marker added flag

        if (AC_DATA.AircraftData[AcInd].MarkerModified) {   //Does this aircraft has an added marker data?
          if (DEBUG) Log.d("PPRZ_info", "Marker modified for AC= " + AcInd);
          int MarkerInd = 1;
          //if (DEBUG) Log.d("PPRZ_info", "trying to show ac markers of "+AcInd);
          //Search aircraft markers which has name but doesn't have marker
          for (MarkerInd = 1; (MarkerInd < AC_DATA.AircraftData[AcInd].NumbOfWps - 1); MarkerInd++) {
            //Log.d("PPRZ_info", "Searching Marker for AC= " + AcInd + " wpind:" + MarkerInd);
            if ((AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd] == null) || !(AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].MarkerModified) || (null == AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker))
              continue; //we dont have data for this wp yet

            //Set new position for marker
            AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker.setPosition(convert_to_lab(AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpPosition));

            //Clean MarkerModified flag
            AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].MarkerModified = false;
            if (DEBUG) Log.d("PPRZ_info", "Marker modified for acid: " + AcInd + " wpind:" + MarkerInd);

          }
          //Clean AC MarkerModified flag
          AC_DATA.AircraftData[AcInd].MarkerModified = false;

        }
      }
      //Clean Class MarkerModified flag
      AC_DATA.MarkerModified = false;

    }

    AC_DATA.ViewChanged = false;
  }

  /**
   * @param WpID
   * @param NewPosition
   * @param DialogTitle
   */
  private void waypoint_changed(String WpID, LatLng NewPosition, String DialogTitle) {

    //Find the marker

    int AcInd;
    int MarkerInd = 1;
    for (AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {     //Find waypoint which is changed!


      for (MarkerInd = 1; (MarkerInd < AC_DATA.AircraftData[AcInd].NumbOfWps - 1); MarkerInd++) {

        if ((null == AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker))
          continue; //we dont have data for this wp yet
        //Log.d("PPRZ_info", "Marker drop!!  Searching for AC= " + AcInd + " wpind:" + MarkerInd + " mid: "+AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker.getId());
        //Search the marker

        if (AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker.getId().equals(WpID)) {
          //Log.d("PPRZ_info", "Marker found AC= " + AcInd + " wpind:" + MarkerInd);
          NewPosition =  convert_to_google(NewPosition);
          AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpPosition = NewPosition;
          //show_dialog_menu(String DialogTitle,String WpName,String AirCraftId,String WayPointId,String Lat,String Long,String Alt)
          show_dialog_menu(DialogTitle, AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpName, AC_DATA.AircraftData[AcInd].AC_Id, MarkerInd, String.valueOf(NewPosition.latitude), String.valueOf(NewPosition.longitude), AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpAltitude);

          return;
        }

      }
    }

  }

  private boolean checkReady() {
    if (mMap == null) {
      Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
      return false;
    }
    return true;
  }

  public void onResetMap(View view) {
    if (!checkReady()) {
      return;
    }
    // Clear the map because we don't want duplicates of the markers.
    mMap.clear();
    //addMarkersToMap();
  }

  //Function to center aircraft on map (can be integrated with Center_AC funct. )
  private void center_aircraft() {
    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(AC_DATA.AircraftData[AC_DATA.SelAcInd].Position, MapZoomLevel), 1500, null);
  }

  @Override
  public void setTitle(CharSequence title) {
    mTitle = title;
    getActionBar().setTitle(mTitle);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {

    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;

  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    switch (item.getItemId()) {
      case R.id.action_settings:

        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);

        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onStop() {
    super.onStop();

    // We need an Editor object to make preference changes.
    // All objects are from android.context.Context

    SharedPreferences.Editor editor = AppSettings.edit();

    editor.putFloat("MapZoomLevel", MapZoomLevel);

    // Commit the edits!
    editor.commit();
    AC_DATA.mTcpClient.sendMessage("removeme");
    //TelemetryAsyncTask.isCancelled();
    //AC_DATA.mTcpClient.stopClient();
    isTaskRunning= false;
    //TelemetryAsyncTask.cancel(true);

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

  }

    @Override
    protected void onRestart() {
        super.onRestart();

        //Force to reconnect
        //TcpSettingsChanged = true;
        TelemetryAsyncTask = new ReadTelemetry();
        TelemetryAsyncTask.execute();

        if (DisableScreenDim) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //enable screen dim


    }

  @Override
  protected void onStart() {
    super.onStop();

    AppStarted = true;
    MapZoomLevel = AppSettings.getFloat("MapZoomLevel", 16.0f);

  }

    @Override
    protected void onPause() {
        super.onPause();
        // The following call pauses the rendering thread.
        // If your OpenGL application is memory intensive,
        // you should consider de-allocating objects that
        // consume significant memory here.
       // mGLView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The following call resumes a paused rendering thread.
        // If you de-allocated graphic objects for onPause()
        // this is a good place to re-allocate them.
        //mGLView.onResume();
    }


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);



    set_up_app();

    if (DisableScreenDim) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


    setup_telemetry_class();
    TelemetryAsyncTask = new ReadTelemetry();
    TelemetryAsyncTask.execute();
  }

  private ReadTelemetry TelemetryAsyncTask;

/* >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
* START OF UI FUNCTIONS >>>>>> START OF UI FUNCTIONS >>>>>> START OF UI FUNCTIONS >>>>>> START OF UI FUNCTIONS >>>>>>
>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>*/

  //Settings change listener
  @Override
  public void onSharedPreferenceChanged(SharedPreferences AppSettings, String key) {

    //Changed settings will be applied on nex iteration of async task

    if (key.equals(SERVER_IP_ADDRESS)) {
      AC_DATA.ServerIp = AppSettings.getString(SERVER_IP_ADDRESS, getString(R.string.pref_ip_address_default));
      if (DEBUG) Log.d("PPRZ_info", "IP changed to: " + AppSettings.getString(SERVER_IP_ADDRESS, getString(R.string.pref_ip_address_default)));
      TcpSettingsChanged = true;

    }

    if (key.equals(SERVER_PORT_ADDRESS)) {
      AC_DATA.ServerTcpPort = Integer.parseInt(AppSettings.getString(SERVER_PORT_ADDRESS, getString(R.string.pref_port_number_default)));
      TcpSettingsChanged = true;
      if (DEBUG) Log.d("PPRZ_info", "Server Port changed to: " + AppSettings.getString(SERVER_PORT_ADDRESS, getString(R.string.pref_port_number_default)));

    }

    if (key.equals(LOCAL_PORT_ADDRESS)) {

      AC_DATA.UdpListenPort = Integer.parseInt(AppSettings.getString(LOCAL_PORT_ADDRESS, getString(R.string.pref_local_port_number_default)));
      UdpSettingsChanged = true;
      if (DEBUG) Log.d("PPRZ_info", "Local Listen Port changed to: " + AppSettings.getString(LOCAL_PORT_ADDRESS, getString(R.string.pref_local_port_number_default)));

    }

    if (key.equals(MIN_AIRSPEED)) {

      AC_DATA.AirSpeedMinSetting = parseDouble(AppSettings.getString(MIN_AIRSPEED, "10"));
      if (DEBUG) Log.d("PPRZ_info", "Local Listen Port changed to: " + AppSettings.getString(MIN_AIRSPEED, "10"));
    }

    if (key.equals(USE_GPS)) {
      mMap.setMyLocationEnabled(AppSettings.getBoolean(USE_GPS, true));
      if (DEBUG) Log.d("PPRZ_info", "GPS Usage changed to: " + AppSettings.getBoolean(USE_GPS, true));
    }

    if (key.equals(Control_Pass)) {
      AppPassword = AppSettings.getString(Control_Pass, "");
      if (DEBUG) Log.d("PPRZ_info", "App_password changed : " + AppSettings.getString(Control_Pass , ""));
    }

    if (key.equals(BLOCK_C_TIMEOUT)) {
      BL_CountDownTimerDuration = Integer.parseInt(AppSettings.getString(BLOCK_C_TIMEOUT, "3"))*1000;
      setup_counter();
      if (DEBUG) Log.d("PPRZ_info", "Clock change timeout changed : " + AppSettings.getString(BLOCK_C_TIMEOUT , ""));
    }

    if (key.equals(DISABLE_SCREEN_DIM)) {
      DisableScreenDim = AppSettings.getBoolean(DISABLE_SCREEN_DIM, true);

      if (DisableScreenDim) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

      if (DEBUG) Log.d("PPRZ_info", "Screen dim settings changed : " + DisableScreenDim);
    }

  }

  private void show_dialog_menu(String DialogTitle, String WpName, int AirCraftId, int WayPointId, String Lat, String Long, String Alt) {

    // set dialog components
    WpDialog.setTitle(DialogTitle);

    DialogTextWpName.setText(WpName);

    DialogAcId = AirCraftId;
    DialogWpId = WayPointId;

    DialogWpAltitude.setText(Alt);

    DialogTextWpLat.setText(Lat);
    DialogTextWpLon.setText(Long);

    WpDialog.show();

  }

  public void clear_ac_track(View mView) {

    if (AC_DATA.SelAcInd < 0) {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
      return;
    }

    if (ShowOnlySelected) {
      AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Path.clear();
    }
    else {
      for (int AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {
        AC_DATA.AircraftData[AcInd].AC_Path.clear();
      }
    }
    mDrawerLayout.closeDrawers();

  }

    public void confirm_bl_change(View mView) {
        //Cancel timer & clear BlockList
        BL_CountDown.cancel();
        BL_CountDownTimerValue=BL_CountDownTimerDuration;
        mBlListAdapter.ClickedInd=-1;
        mBlListAdapter.notifyDataSetChanged();
        //Notify app_server for changes
        set_selected_block(JumpToBlock,false);

    }

    public void cancel_bl_change(View mView) {
        //Cancel timer & clear BlockList
        BL_CountDown.cancel();
        BL_CountDownTimerValue=BL_CountDownTimerDuration;
        mBlListAdapter.ClickedInd=-1;
        mBlListAdapter.notifyDataSetChanged();
        Toast.makeText(getApplicationContext(), "Block change cancelled.", Toast.LENGTH_SHORT).show();


    }

  //Function called when toggleButton_ConnectToServer (in left fragment) is pressed
  public void connect_to_server(View mView) {

    Toast.makeText(getApplicationContext(), "Trying to re-connect to server..", Toast.LENGTH_SHORT).show();
    //Force to reconnect
    TcpSettingsChanged = true;
    UdpSettingsChanged = true;

  }

  //Launch function
  public void launch_ac(View mView) {

    if (AC_DATA.SelAcInd >= 0) {

      send_to_server("dl DL_SETTING " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_LaunchID + " 1.000000", true);
      //dl DL_SETTING 5 8 1.000000
    } else {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
    }
  }

  //Kill throttle function
  public void kill_ac(View mView) {

    if (AC_DATA.SelAcInd >= 0) {

      AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
      // Setting Dialog Title
      alertDialog.setTitle("Kill Throttle");

      // Setting Dialog Message
      alertDialog.setMessage("Kill throttle of A/C " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name + "?");

      // Setting Icon to Dialog
      alertDialog.setIcon(R.drawable.kill);

      // Setting Positive "Yes" Button
      alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          // User pressed YES button. Send kill string
          send_to_server("dl DL_SETTING " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_KillID + " 1.000000", true);
          Toast.makeText(getApplicationContext(), AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name + " ,mayday, kill mode!", Toast.LENGTH_SHORT).show();
          MapThrottle.setTextColor(Color.RED);
        }
      });

      alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {

        }
      });

      // Showing Alert Message
      alertDialog.show();

    } else {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
    }
  }

  //Resurrect function
  public void resurrect_ac(View mView) {
    //dl DL_SETTING 5 9 0.000000
    if (AC_DATA.SelAcInd >= 0) {
      send_to_server("dl DL_SETTING " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_KillID + " 0.000000", true);
      MapThrottle.setTextColor(Color.WHITE);
    } else {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
    }

  }


  public void change_visible_acdata(View mView) {

    //Return if no AC data
    if (AC_DATA.SelAcInd < 0) {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
      return;
    }

    if (ChangeVisibleAcButon.isChecked()) {
      Toast.makeText(getApplicationContext(), "Showing only " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name + " markers", Toast.LENGTH_SHORT).show();
      ShowOnlySelected = true;
    } else {
      Toast.makeText(getApplicationContext(), "Showing all markers", Toast.LENGTH_SHORT).show();
      ShowOnlySelected = false;
    }

    set_marker_visibility();
    mDrawerLayout.closeDrawers();

  }

  //Shows only selected ac markers & hide others
  private void set_marker_visibility() {

    if (ShowOnlySelected) {
      show_only_selected_ac();
    } else {
      show_all_acs();
    }


  }

  private void show_only_selected_ac() {


    for (int AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {

      for (int WpId = 1; (AC_DATA.AircraftData[AcInd].AC_Enabled && (WpId < AC_DATA.AircraftData[AcInd].NumbOfWps - 1)); WpId++) {

        if ((null == AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker)) continue;

        if (AcInd == AC_DATA.SelAcInd) {
          AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(true);

          if ("_".equals(AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpName.substring(0, 1))) {
            AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(false);
          }

        } else {
          AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(false);
        }
      }

    }

  }

  //Show all markers
  private void show_all_acs() {


    for (int AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {

      for (int WpId = 1; (AC_DATA.AircraftData[AcInd].AC_Enabled && (WpId < AC_DATA.AircraftData[AcInd].NumbOfWps - 1)); WpId++) {

        if ((null == AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker)) continue;

        AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(true);

        if ("_".equals(AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpName.substring(0, 1))) {
          AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(false);
        }

      }

    }

  }

  private boolean AcMarkerVisible(int AcInd) {

    if (AcInd == AC_DATA.SelAcInd) {
      return true;
    } else if (ShowOnlySelected) {
      return false;
    }
    return true;

  }

  public void show_alt_dialog(View mView) {
      // set dialog components
      if (AC_DATA.SelAcInd < 0) {
          Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
          return;
      }


      fill_alt(AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_DlAlt);
      AltDialog.setTitle("Set Altitude");


      AltDialog.show();

  }

  public void fill_alt(String AltStr){

      //Clean AltStr
      if (AltStr.contains(" ")) {
          AltStr=AltStr.substring(0,AltStr.indexOf(" "));
      }

      if (AltStr.length() >=4 ) {
          mNumberPickerThus.setValue(Integer.parseInt(AltStr.substring(0,1)));
          mNumberPickerHuns.setValue(Integer.parseInt(AltStr.substring(1,2)));
          mNumberPickerTens.setValue(Integer.parseInt(AltStr.substring(2,3)));
          mNumberPickerOnes.setValue(Integer.parseInt(AltStr.substring(3,4)));
          return;
      }
      else {
          mNumberPickerThus.setValue(0);
      }

      if (AltStr.length() >=3 ) {
          mNumberPickerHuns.setValue(Integer.parseInt(AltStr.substring(0,1)));
          mNumberPickerTens.setValue(Integer.parseInt(AltStr.substring(1,2)));
          mNumberPickerOnes.setValue(Integer.parseInt(AltStr.substring(2,3)));
          return;
      }
      else {
          mNumberPickerHuns.setValue(0);
      }

      if (AltStr.length() >=2 ) {
          mNumberPickerTens.setValue(Integer.parseInt(AltStr.substring(0,1)));
          mNumberPickerOnes.setValue(Integer.parseInt(AltStr.substring(1,2)));
          return;
      }
      else {
          mNumberPickerTens.setValue(0);
      }

      if (AltStr.length() >=1 ) {
          mNumberPickerOnes.setValue(Integer.parseInt(AltStr.substring(0,1)));
          return;
      }
      else {
          mNumberPickerOnes.setValue(0);
      }

  }

  /**
   * background thread to read & write comm strings. Only changed UI items should be refreshed for smoother UI
   * Check telemetry class for whole UI change flags
   */
  class ReadTelemetry extends AsyncTask<String, String, String> {

    @Override
    protected void onPreExecute() {
      super.onPreExecute();

      isTaskRunning = true;
      if (DEBUG) Log.d("PPRZ_info", "ReadTelemetry() function started.");
    }

    @Override
    protected String doInBackground(String... strings) {

        mTCPthread =  new Thread(new ClientThread());
        mTCPthread.start();

        while (isTaskRunning) {

            //Check if settings changed
            if (TcpSettingsChanged) {
                AC_DATA.mTcpClient.stopClient();
                try {
                    Thread.sleep(200);
                    //AC_DATA.mTcpClient.SERVERIP= AC_DATA.ServerIp;
                    //AC_DATA.mTcpClient.SERVERPORT= AC_DATA.ServerTcpPort;
                    mTCPthread =  new Thread(new ClientThread());
                    mTCPthread.start();
                    TcpSettingsChanged=false;
                    if (DEBUG) Log.d("PPRZ_info", "TcpSettingsChanged applied");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (UdpSettingsChanged) {
                AC_DATA.setup_udp();
                //AC_DATA.tcp_connection();
                UdpSettingsChanged = false;
                if (DEBUG) Log.d("PPRZ_info", "UdpSettingsChanged applied");
            }

            // Log.e("PPRZ_info", "3");
            //1 check if any string waiting to be send to tcp
            if (!(null == AC_DATA.SendToTcp)) {
                AC_DATA.mTcpClient.sendMessage(AC_DATA.SendToTcp);
                AC_DATA.SendToTcp = null;

            }

        //3 try to read & parse udp data
        AC_DATA.read_udp_data();

        //4 check ui changes
        if (AC_DATA.ViewChanged) {
          publishProgress("ee");
          AC_DATA.ViewChanged = false;
        }




      }

      if (DEBUG) Log.d("PPRZ_info", "Stopping AsyncTask ..");
      return null;
    }

    @Override
    protected void onProgressUpdate(String... value) {
      super.onProgressUpdate(value);

      try {



        if (AC_DATA.SelAcInd < 0) {
          //no selected aircrafts yet! Return.
          return;
        }


        if (AC_DATA.AircraftData[AC_DATA.SelAcInd].ApStatusChanged) {
          TextViewApMode.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].ApMode);
          TextViewGpsMode.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].GpsMode);
          TextViewFlightTime.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].FlightTime);
          TextViewBattery.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].Battery + "v");
          TextViewStateFilterMode.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].StateFilterMode);
          TextViewSpeed.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].Speed + "m/s");
          AC_DATA.AircraftData[AC_DATA.SelAcInd].ApStatusChanged = false;
        }

        if (AC_DATA.AirspeedWarning) {
          try {
            play_sound(getApplicationContext());
          } catch (IOException e) {
            e.printStackTrace();
          }
        }


        if (AC_DATA.BlockChanged) {
          //Block changed for selected aircraft
          if (DEBUG) Log.d("PPRZ_info", "Block Changed for selected AC.");

          set_selected_block((AC_DATA.AircraftData[AC_DATA.SelAcInd].SelectedBlock-1),true);
          AC_DATA.BlockChanged = false;

        }


        if (AC_DATA.NewAcAdded) {
          //new ac addedBattery value for an ac is changed

          set_selected_ac(AC_DATA.SelAcInd,false);
          AC_DATA.NewAcAdded = false;
        }

          if (AC_DATA.BatteryChanged) {
              //new ac addedBattery value for an ac is changed
              refresh_ac_list();
              mAcListAdapter.notifyDataSetChanged();
              AC_DATA.BatteryChanged = false;
          }

        //For a smooth gui we need refresh only changed gui controls
        refresh_markers();

        //Check  engine Status
        if (AC_DATA.AircraftData[AC_DATA.SelAcInd].EngineStatusChanged) {
          MapThrottle.setText("%" + AC_DATA.AircraftData[AC_DATA.SelAcInd].Throttle);
          AC_DATA.AircraftData[AC_DATA.SelAcInd].EngineStatusChanged = false;
        }

        if (AC_DATA.AircraftData[AC_DATA.SelAcInd].Altitude_Changed) {
          MapAlt.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].Altitude);

            AC_DATA.AircraftData[AC_DATA.SelAcInd].Altitude_Changed = false;

            Pfd.setImageBitmap(AC_DATA.AcPfd);

        }


        if (AC_DATA.AircraftData[AC_DATA.SelAcInd].AirspeedEnabled && AC_DATA.AircraftData[AC_DATA.SelAcInd].AirspeedChanged) {
          //Airspeed Enabled
          TextViewAirspeed.setVisibility(View.VISIBLE);
          TextViewAirspeed.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].AirSpeed + " m/s");
        }

        //No error handling right now.
        Button_ConnectToServer.setText("Connected!");
        //}

      } catch (Exception ex) {
          if (DEBUG) Log.d("Pprz_info", "Exception occured: " + ex.toString());

      }

    }

    @Override
    protected void onPostExecute(String s) {
      super.onPostExecute(s);

    }

  }

  class ClientThread implements Runnable {


        @Override
        public void run() {

            if (DEBUG) Log.d("PPRZ_info", "ClientThread started");

            AC_DATA.mTcpClient = new TCPClient(new TCPClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    //publishProgress(message);
                    //AC_DATA.parse_tcp_string(message);
                    AC_DATA.parse_tcp_string(message);

                }
            });
            AC_DATA.mTcpClient.SERVERIP = AC_DATA.ServerIp;
            AC_DATA.mTcpClient.SERVERPORT= AC_DATA.ServerTcpPort;
            AC_DATA.mTcpClient.run();

        }

    }



/* <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
* END OF UI FUNCTIONS >>>>>> END OF UI FUNCTIONS >>>>>> END OF UI FUNCTIONS >>>>>> END OF UI FUNCTIONS >>>>>>
<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<*/

    //Here are our new methods

      //this method converts the google latitudes to the corresponding points on the transposed image
      //use this method to draw the markers in the right spots
      public LatLng convert_to_lab(LatLng position){
          double oldLat = position.latitude;
          double oldLong = position.longitude;

          double newLat = 5.1875*oldLat - 150.772646;
          double newLong = 4.9722*oldLong + 313.569393;

          LatLng newPosition = new LatLng(newLat, newLong);
          return newPosition;
      }

      //this method converts the fake latitudes back to the actual google values
      //use this for any information that paparazzi needs about where to actually send the drone
      public LatLng convert_to_google(LatLng position){
          double oldLat = position.latitude;
          double oldLong = position.longitude;

          double newLat = (oldLat + 150.772646)/5.1875;
          double newLong = (oldLong - 313.569393)/4.9722;

          LatLng newPosition = new LatLng(newLat, newLong);
          return newPosition;
      }

    //fix the icons every time a waypoint is either reached or removed, currently unused in
    //experimental design
    public void adjust_marker_titles(){
        int index = 0;
        while(index < mMarkerHead.size()){
            Marker marker = mMarkerHead.get(index);
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                    "red", Integer.toString(index + 1), AC_DATA.GraphicsScaleFactor)));
            marker.setTitle(Integer.toString(index + 1));
            index++;
        }
    }

    //adjust the connecting lines between markers when one is deleted
    public void adjust_marker_lines(){
        path.setPoints(pathPoints);
    }

    //dialog to bring up removal control for a waypoint
    public void remove_waypoint_dialog(Marker marker){
        final Marker rMarker = marker;
        AlertDialog removeDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Remove?")
                .setMessage("Click OK to remove this marker")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        rMarker.remove();
                        if(mrkIndex>0) mrkIndex--;
                        mMarkerHead.remove(rMarker);
                        pathPoints.remove(rMarker.getPosition());
                        //adjust_marker_titles();
                        adjust_marker_lines();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        removeDialog.show();
    }

    //dialog to bring up altitude control when a waypoint is either created or moved
    public void launch_altitude_dialog(Marker marker, String flag){
        final Marker altMarker = marker;
        final String mFlag = flag;
        LinearLayout altLayout = new LinearLayout(this);
        altLayout.setOrientation(LinearLayout.VERTICAL);

        //create text view
        final TextView altVal = new TextView(this);
        altVal.setGravity(Gravity.CENTER);

        //create altitude adjustment and set default accordingly
        SeekBar altSeek = new SeekBar(this);
        altSeek.setMax(25);
        if(flag.equals("NEW")) {
            altSeek.setProgress(10);
        }
        else if(flag.equals("OLD")){
            altSeek.setProgress((int)(10*Double.parseDouble(altMarker.getSnippet())));
        }
        altVal.setText(String.valueOf((double) (altSeek.getProgress())/10));
        altSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                altVal.setText(String.valueOf((double) i/10));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        altLayout.addView(altVal);
        altLayout.addView(altSeek);

        //create the actual dialog
        AlertDialog.Builder altDialog = new AlertDialog.Builder(MainActivity.this);
        altDialog.setView(altLayout);
        altDialog.setTitle("Change Altitude")
                .setMessage("Click Adjust to confirm new altitude. Clicking Cancel will set the altitude to be the same " +
                        "as the previous waypoint")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(mFlag.equals("NEW")){
                            if(mrkIndex > 1){
                                altMarker.setSnippet(mMarkerHead.get(mrkIndex-2).getSnippet());
                            }
                            else{
                                altMarker.setSnippet("1.0");
                            }
                        }
                        altMarker.setIcon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                                "red", altMarker.getSnippet(), AC_DATA.GraphicsScaleFactor)));
                    }
                })
                .setPositiveButton("Adjust", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        altMarker.setSnippet(altVal.getText().toString());
                        altMarker.setIcon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                                "red", altMarker.getSnippet(), AC_DATA.GraphicsScaleFactor)));
                    }
                }).create();
        if(flag.equals("OLD")) altDialog.setMessage("Click Adjust to confirm new altitude. Clicking Cancel " +
                "will keep the altitude as it had been set.");
        altDialog.show();
    }


}


