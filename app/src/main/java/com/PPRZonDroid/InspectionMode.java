
/*
	Inspection mode here has been created to allow for a video stream and nudge controls when the
	drone is close to an object and needs to get a better view

	Currently, launching inspection mode restarts the telemetry processes since a broadcast reciever
	or more complex intent has not been implemented. As soon as the buttons are pressed for the first
	time, the app will begin to send RC_4CH commands through mode 1 -- auto1 which has been set to
	AC_MODE_ATTITUDE_RC_CLIMB
		(NOTE: this is not the same mode that paparazzi documentation discusses. We made a slight
		modification such that this mode now also has hover horizontal guidance rather than direct
		horizontal control so that the drone will stay in place and respond to all types of directional
		commands)

	Pressing the return button drops a pin at the current location, waits a little bit to let that
	command register, and then switches mode to auto 2. A timer must then wait a little before ending the
	RC_4CH commands so that the drone does not trigger safe mode and touch down.

	There are some areas here that need to be fixed. The button UI is not very obvious, the timers
	used in the return button cause a large delay after pressing it, there is some lag in the buttons,
	there is a drop in altitude when switching from main to inspection, the pause pin drop is imperfect,
	and the TCP implementation is sloppy and potentially causing some of the lag. Some of these issues,
	such as the button lag, likely cannot be fixed and are problems from paparazzi.

	--6/1/17


 */

package com.PPRZonDroid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class InspectionMode extends Activity implements IVideoPlayer {

	private static final String TAG = InspectionMode.class.getSimpleName();


	private int mVideoHeight;
	private int mVideoWidth;
	private int mVideoVisibleHeight;
	private int mVideoVisibleWidth;
	private int mSarNum;
	private int mSarDen;

	private TextView FlightTimeInspect, BatteryInspect, AltitudeInspect;
	private ImageView BatteryImage;
	private SurfaceView mSurfaceView;
	private FrameLayout mSurfaceFrame;
	private SurfaceHolder mSurfaceHolder;
	private Surface mSurface = null;

	private LibVLC mLibVLC;

	private String mMediaUrl;
	private String[] temp_options;
	private String[] new_options;
	private ReadTelemetry TelemetryAsyncTask;
	boolean isTaskRunning;
	private Thread mTCPthread;

	RelativeLayout thumbPad_left, thumbPad_right;
	ThumbPad leftPad, rightPad;
	public Telemetry AC_DATA;

	boolean DEBUG=false;
	boolean TcpSettingsChanged;
	boolean UdpSettingsChanged;
	boolean lowBatteryUnread = true;
	boolean emptyBatteryUnread = true;
	boolean isClicked = false;
	String AppPassword;

	public int yaw, pitch, roll = 0;
	public int throttle = 63;
	public int mode = 2;
	public int percent = 100;
	public int AcId = 31;

	int RIGHT = 1;
	int UP = 2;
	int LEFT = 3;
	int DOWN = 4;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_inspection_mode);

		mSurfaceView = (SurfaceView) findViewById(R.id.player_surface);
		mSurfaceHolder = mSurfaceView.getHolder();

		//health and status information
		FlightTimeInspect = (TextView) findViewById(R.id.Flight_Time_On_Map_Inspect);
		BatteryInspect = (TextView) findViewById(R.id.Bat_Vol_On_Map_Inspect);
		AltitudeInspect = (TextView) findViewById(R.id.Alt_On_Map_Inspect);
		BatteryImage = (ImageView) findViewById(R.id.batteryImageViewInspect);
		BatteryImage.setBackgroundResource(R.drawable.battery_image_empty);

		mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame);
		mMediaUrl = getIntent().getExtras().getString("videoUrl");
		try {
			mLibVLC = new LibVLC();
			mLibVLC.setAout(mLibVLC.AOUT_AUDIOTRACK);
			mLibVLC.setVout(mLibVLC.VOUT_ANDROID_SURFACE);
			mLibVLC.setHardwareAcceleration(LibVLC.HW_ACCELERATION_AUTOMATIC);
			mLibVLC.setChroma("YV12");

			mLibVLC.init(getApplicationContext());
		} catch (LibVlcException e){
			Log.e(TAG, e.toString());
		}

		mSurface = mSurfaceHolder.getSurface();

		mLibVLC.attachSurface(mSurface, InspectionMode.this);

		temp_options = mLibVLC.getMediaOptions(0);
		List<String> options_list = new ArrayList<String>(Arrays.asList(temp_options));


		options_list.add(":file-caching=2000");
		options_list.add(":network-caching=1");
		options_list.add(":clock-jitter=0");
		options_list.add("--clock-synchro=1");
		new_options = options_list.toArray(new String[options_list.size()]);

		mLibVLC.playMRL(mMediaUrl,new_options);
		setup_app();
		setup_telemetry_class();

		TelemetryAsyncTask = new ReadTelemetry();
		TelemetryAsyncTask.execute();

		//send paparazzi method to indicate switching to hover mode

	}

	protected void onDestroy() {
		super.onDestroy();

		// MediaCodec opaque direct rendering should not be used anymore since there is no surface to attach.
		mLibVLC.stop();
	}

	public void eventHardwareAccelerationError() {
		Log.e(TAG, "eventHardwareAccelerationError()!");
		return;
	}

	@Override
	public void setSurfaceLayout(final int width, final int height, int visible_width, int visible_height, final int sar_num, int sar_den){
		Log.d(TAG, "setSurfaceSize -- START");
		if (width * height == 0)
			return;

		// store video size
		mVideoHeight = height;
		mVideoWidth = width;
		mVideoVisibleHeight = visible_height;
		mVideoVisibleWidth = visible_width;
		mSarNum = sar_num;
		mSarDen = sar_den;

		Log.d(TAG, "setSurfaceSize -- mMediaUrl: " + mMediaUrl + " mVideoHeight: " + mVideoHeight + " mVideoWidth: " + mVideoWidth + " mVideoVisibleHeight: " + mVideoVisibleHeight + " mVideoVisibleWidth: " + mVideoVisibleWidth + " mSarNum: " + mSarNum + " mSarDen: " + mSarDen);
	}
	@Override
	public int configureSurface(android.view.Surface surface, int i, int i1, int i2){
		return -1;
	}



	private void setup_telemetry_class() {

		//Create com.hal.manualmocap.Telemetry class
		AC_DATA = new Telemetry();

		//sub in values
		AC_DATA.ServerIp = "192.168.50.10";
		AC_DATA.ServerTcpPort = 5010;
		AC_DATA.UdpListenPort = 5005;

		//must prepare class in order to parse udp strings into the aircraft object
		AC_DATA.prepare_class();
		AC_DATA.unopened = false;
		AC_DATA.setup_udp();
	}

	public void setup_app(){
		AppPassword = "1234";

		thumbPad_left = (RelativeLayout)findViewById(R.id.joystick_left);
		thumbPad_right = (RelativeLayout)findViewById(R.id.joystick_right);

		leftPad = new ThumbPad(thumbPad_left);
		rightPad = new ThumbPad(thumbPad_right);

		thumbPad_left.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				AC_DATA.inspecting = true;
				if(event.getAction() == MotionEvent.ACTION_DOWN){
					mode = 1;
					if(leftPad.getRegion(event) == RIGHT){
						yaw = 10;
					}
					else if(leftPad.getRegion(event) == LEFT){
						yaw = -10;
					}
					else if(leftPad.getRegion(event) == UP){
						throttle = 84;
					}
					else if(leftPad.getRegion(event) == DOWN){
						throttle = 42;
					}
				}
				else if(event.getAction()== MotionEvent.ACTION_UP) {
					yaw = 0;
					throttle = 63;
					new CountDownTimer(1000, 100) {
						@Override
						public void onTick(long l) {
						}

						@Override
						public void onFinish() {
							float Altitude = Float.parseFloat(AC_DATA.AircraftData[0].RawAltitude);
							AC_DATA.SendToTcp = AppPassword + "PPRZonDroid MOVE_WAYPOINT " + AcId  + " 4 " +
									AC_DATA.AircraftData[0].Position.latitude + " " +
									AC_DATA.AircraftData[0].Position.longitude + " " + Altitude;
						}
					}.start();
				}
					return true;
			}
		});

		thumbPad_right.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				AC_DATA.inspecting = true;
				if(event.getAction() == MotionEvent.ACTION_DOWN){
					mode = 1;
					if(rightPad.getRegion(event) == RIGHT){
						roll = 15;
					}
					else if(rightPad.getRegion(event) == LEFT){
						roll = -15;
					}
					else if(rightPad.getRegion(event) == UP){
						pitch = -15;
					}
					else if(rightPad.getRegion(event) == DOWN){
						pitch = 15;
					}
				}
				else if(event.getAction()== MotionEvent.ACTION_UP){
					pitch = 0;
					roll = 0;
					new CountDownTimer(1000, 100) {
						@Override
						public void onTick(long l) {
						}

						@Override
						public void onFinish() {
							float Altitude = Float.parseFloat(AC_DATA.AircraftData[0].RawAltitude);
							AC_DATA.SendToTcp = AppPassword + "PPRZonDroid MOVE_WAYPOINT " + AcId  + " 4 " +
									AC_DATA.AircraftData[0].Position.latitude + " " +
									AC_DATA.AircraftData[0].Position.longitude + " " + Altitude;
						}
					}.start();
				}
				return true;
			}
		});

		Button returnButton = (Button) findViewById(R.id.Return);
		returnButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//yes this nested timer is not pretty but you need a) time for the pause block to go
				//through so the drone doesn't revert to its original location and then b) time for
				//the joystick mode to adjust back to auto2 or the drone triggers safe landing
				if(!isClicked) {
					isClicked = true;
					AC_DATA.empty = false;
					new CountDownTimer(500, 100) {
						@Override
						public void onTick(long l) {
						}

						@Override
						public void onFinish() {
							mode = 2;
							new CountDownTimer(1000, 1000) {
								@Override
								public void onTick(long l) {
								}

								@Override
								public void onFinish() {
									AC_DATA.inspecting = false;
									AC_DATA.mTcpClient.sendMessage("removeme");
									//TelemetryAsyncTask.isCancelled();
									AC_DATA.mTcpClient.stopClient();
									isTaskRunning = false;
									finish();
								}
							}.start();
						}
					}.start();
				}
			}
		});
	}

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

				if(AC_DATA.inspecting) {
					AC_DATA.mTcpClient.sendMessage("joyinfo" + " " + mode + " " + throttle + " "
							+ roll + " " + pitch + " " + yaw);
				}

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
					AC_DATA.empty = true;
				}

				//3 try to read & parse udp data
				AC_DATA.read_udp_data(MainActivity.sSocket);

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

			if(AC_DATA.AircraftData[0] != null && !AC_DATA.AircraftData[0].AC_Enabled){
				AC_DATA.AircraftData[0].AC_Enabled = true;
			}

			if (AC_DATA.AircraftData[0].ApStatusChanged) {

				Bitmap bitmap = Bitmap.createBitmap(
						55, // Width
						110, // Height
						Bitmap.Config.ARGB_8888 // Config
				);
				Canvas canvas = new Canvas(bitmap);
				//canvas.drawColor(Color.BLACK);
				Paint paint = new Paint();
				paint.setStyle(Paint.Style.FILL);
				paint.setAntiAlias(true);
				double battery_double = Double.parseDouble(AC_DATA.AircraftData[0].Battery);
				double battery_width = (12.5 - battery_double) / (.027);
				int val = (int) battery_width;


				int newPercent = (int) (((battery_double - 9.8)/(10.9-9.8)) * 100);
				if(newPercent >= 100 && percent >= 100){
					BatteryInspect.setText("" + percent + " %");
				}
				if(newPercent < percent && newPercent >= 0) {
					BatteryInspect.setText("" + newPercent + " %");
					percent = newPercent;
				}


				if (percent> 66) {
					paint.setColor(Color.parseColor("#18A347"));
				}
				if (66 >= percent && percent >= 33) {
					paint.setColor(Color.YELLOW);

				}
				if (33 > percent && percent > 10) {
					paint.setColor(Color.parseColor("#B0090E"));
					if(lowBatteryUnread) {
						Toast.makeText(getApplicationContext(), "Warning: Low Battery", Toast.LENGTH_SHORT).show();
						lowBatteryUnread = false;
					}
				}
				if (percent <= 10) {
					if(emptyBatteryUnread) {
						Toast.makeText(getApplicationContext(), "No battery remaining. Land immediately", Toast.LENGTH_SHORT).show();
						emptyBatteryUnread = false;
					}
				}
				int padding = 10;
				Rect rectangle = new Rect(
						padding, // Left
						100 - (int) (90*((double) percent *.01)), // Top
						canvas.getWidth() - padding , // Right
						canvas.getHeight() - padding // Bottom
				);

				canvas.drawRect(rectangle, paint);
				BatteryImage.setImageBitmap(bitmap);


				FlightTimeInspect.setText(AC_DATA.AircraftData[0].FlightTime + " s");
				AC_DATA.AircraftData[0].ApStatusChanged = false;
			}

			if (AC_DATA.AircraftData[0].Altitude_Changed) {
				AltitudeInspect.setText(AC_DATA.AircraftData[0].Altitude);

				AC_DATA.AircraftData[0].Altitude_Changed = false;

			}
		}
	}

	class ClientThread implements Runnable {


		@Override
		public void run() {

			AC_DATA.mTcpClient = new TCPClient(new TCPClient.OnMessageReceived() {
				@Override
				//here the messageReceived method is implemented
				public void messageReceived(String message) {
					//this method calls the onProgressUpdate
					//publishProgress(message);
					//Log.d("TCPParse", "Begin TCP parse");
					AC_DATA.parse_tcp_string(message);

				}
			});
			AC_DATA.mTcpClient.SERVERIP = AC_DATA.ServerIp;
			AC_DATA.mTcpClient.SERVERPORT= AC_DATA.ServerTcpPort;
			AC_DATA.mTcpClient.run();

		}

	}
}
