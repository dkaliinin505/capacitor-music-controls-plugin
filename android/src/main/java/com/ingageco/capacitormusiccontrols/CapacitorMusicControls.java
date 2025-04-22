package com.ingageco.capacitormusiccontrols;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.content.ContextCompat;

import android.media.session.MediaSession.Token;

import android.util.Log;
import android.app.Activity;
import android.app.Notification;

import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.app.Service;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Build;
import android.R;
import android.content.BroadcastReceiver;
import android.media.AudioManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name="CapacitorMusicControls")
public class CapacitorMusicControls extends Plugin {

	private static final String TAG = "CapacitorMusicControls";

	private MusicControlsBroadcastReceiver mMessageReceiver;
	private MusicControlsNotification notification;
	private MediaSessionCompat mediaSessionCompat;
	private final int notificationID=7824;
	private AudioManager mAudioManager;
	private PendingIntent mediaButtonPendingIntent;
	private boolean mediaButtonAccess=true;
	private android.media.session.MediaSession.Token token;
	private MusicControlsServiceConnection mConnection;
	private long lastKnownPosition = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
	private float originalVolume = 1.0f;

	private MediaSessionCallback mMediaSessionCallback = new MediaSessionCallback(this);


	@PluginMethod()
    public void create(PluginCall call) {
        JSObject options = call.getData();

        final Context context = getActivity().getApplicationContext();
        final Activity activity = getActivity();

        initialize();

        try {
            final MusicControlsInfos infos = new MusicControlsInfos(options);

            final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

            notification.updateNotification(infos);

            // track title
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, infos.track);
            // artists
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, infos.artist);
            // album
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, infos.album);

            if (infos.duration > 0) {
                metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, infos.duration);
            }

            Bitmap art = getBitmapCover(infos.cover);
            if (art != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art);
            }

            mediaSessionCompat.setMetadata(metadataBuilder.build());

            // Initialize with elapsed time if available
            if (infos.isPlaying) {
                // Use the elapsed time from infos if available
                PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
                playbackStateBuilder.setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE |
                    PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                );
                this.lastKnownPosition = infos.elapsed;
                playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, infos.elapsed, 1.0f);
                this.mediaSessionCompat.setPlaybackState(playbackStateBuilder.build());
            } else {
                setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
            }

            call.resolve();

        } catch (JSONException e) {
            call.reject("error in initializing MusicControlsInfos " + e.toString());
        }
    }

	private void registerBroadcaster(MusicControlsBroadcastReceiver mMessageReceiver){
    final Context context = getActivity().getApplicationContext();
    IntentFilter filter = new IntentFilter();
    filter.addAction("music-controls-previous");
    filter.addAction("music-controls-pause");
    filter.addAction("music-controls-play");
    filter.addAction("music-controls-next");
    filter.addAction("music-controls-media-button");
    filter.addAction("music-controls-destroy");
    filter.addAction(Intent.ACTION_HEADSET_PLUG);
    filter.addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);

    ContextCompat.registerReceiver(context, mMessageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
	}

	// Register pendingIntent for broacast
	public void registerMediaButtonEvent(){
		if (this.mediaSessionCompat != null) {
			this.mediaSessionCompat.setMediaButtonReceiver(this.mediaButtonPendingIntent);
		}
	}

	public void unregisterMediaButtonEvent(){
		if (this.mediaSessionCompat != null) {
			this.mediaSessionCompat.setMediaButtonReceiver(null);
			this.mediaSessionCompat.release();
		}
	}

	public void destroyPlayerNotification(){
		if (this.notification != null) {
			try {
				this.notification.destroy();
				this.notification = null;
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
		}
	}


    @PluginMethod()
    public void updateMetadata(PluginCall call) {
        JSObject options = call.getData();

        if (this.notification == null || this.mediaSessionCompat == null) {
            Log.e(TAG, "updateMetadata: notification or mediaSessionCompat is null");
            call.resolve();
            return;
        }

        try {
            // Extract all possible metadata fields
            final String track = options.has("track") ? options.getString("track") : null;
            final String artist = options.has("artist") ? options.getString("artist") : null;
            final String album = options.has("album") ? options.getString("album") : null;
            final String cover = options.has("cover") ? options.getString("cover") : null;
            final long duration = options.has("duration") ? options.getLong("duration") : 0;
            final long elapsed = options.has("elapsed") ? options.getLong("elapsed") : 0;
            final boolean isPlaying = options.has("isPlaying") ? options.getBoolean("isPlaying") : false;
            final boolean dismissable = options.has("dismissable") ? options.getBoolean("dismissable") : true;
            final boolean hasPrev = options.has("hasPrev") ? options.getBoolean("hasPrev") : false;
            final boolean hasNext = options.has("hasNext") ? options.getBoolean("hasNext") : true;
            final boolean hasClose = options.has("hasClose") ? options.getBoolean("hasClose") : false;
            final String ticker = options.has("ticker") ? options.getString("ticker") : null;

            // Update the media session metadata
            final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

            // Update with provided values or keep existing ones
            if (track != null) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track);
            } else if (this.mediaSessionCompat.getController().getMetadata() != null) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                    this.mediaSessionCompat.getController().getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            }

            if (artist != null) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
            } else if (this.mediaSessionCompat.getController().getMetadata() != null) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                    this.mediaSessionCompat.getController().getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            }

            if (album != null) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
            } else if (this.mediaSessionCompat.getController().getMetadata() != null) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM,
                    this.mediaSessionCompat.getController().getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
            }

            // Always update the duration if provided
            if (duration > 0) {
                Log.d(TAG, "Setting duration to " + duration + "ms");
                metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
            } else if (this.mediaSessionCompat.getController().getMetadata() != null) {
                long currentDuration = this.mediaSessionCompat.getController().getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                Log.d(TAG, "Keeping current duration: " + currentDuration + "ms");
                metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration);
            }

            // Handle album art
            if (cover != null && !cover.isEmpty()) {
                Bitmap art = getBitmapCover(cover);
                if (art != null) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art);
                }
            } else if (this.mediaSessionCompat.getController().getMetadata() != null) {
                Bitmap currentArt = this.mediaSessionCompat.getController().getMetadata().getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                if (currentArt != null) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArt);
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, currentArt);
                }
            }

            // Update the media session with new metadata
            this.mediaSessionCompat.setMetadata(metadataBuilder.build());

            // If we have elapsed time, update the playback state
            if (elapsed > 0) {
                this.lastKnownPosition = elapsed;

                // Don't change playing state if not explicitly requested
                int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
                playbackStateBuilder.setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE |
                    PlaybackStateCompat.ACTION_PLAY |
                    PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                );
                playbackStateBuilder.setState(state, elapsed, isPlaying ? 1.0f : 0.0f);
                this.mediaSessionCompat.setPlaybackState(playbackStateBuilder.build());
            }

            // Update notification info using all fields
            MusicControlsInfos infos = null;
            try {
                infos = new MusicControlsInfos(options);
            } catch (JSONException e) {
                // If there's an error with the options, create a new JSObject with all required fields
                Log.d(TAG, "Creating new options for notification update");
                JSObject notificationOptions = new JSObject();

                // Use provided values or existing metadata
                MediaMetadataCompat currentMetadata = this.mediaSessionCompat.getController().getMetadata();

                notificationOptions.put("track", track != null ? track :
                    (currentMetadata != null ? currentMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) : "Unknown"));

                notificationOptions.put("artist", artist != null ? artist :
                    (currentMetadata != null ? currentMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) : "Unknown"));

                notificationOptions.put("album", album != null ? album :
                    (currentMetadata != null ? currentMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) : "Arena Music"));

                notificationOptions.put("cover", cover);
                notificationOptions.put("duration", duration > 0 ? duration :
                    (currentMetadata != null ? currentMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) : 0));

                notificationOptions.put("elapsed", elapsed);
                notificationOptions.put("isPlaying", isPlaying);
                notificationOptions.put("hasNext", hasNext);
                notificationOptions.put("hasPrev", hasPrev);
                notificationOptions.put("hasClose", hasClose);
                notificationOptions.put("dismissable", dismissable);
                notificationOptions.put("ticker", ticker != null ? ticker : "Arena Music");

                infos = new MusicControlsInfos(notificationOptions);
            }

            if (infos != null) {
                // Ensure elapsed time is set
                infos.elapsed = elapsed > 0 ? elapsed : this.lastKnownPosition;

                // Update the notification with the new info
                this.notification.updateNotification(infos);
            }

            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error in updateMetadata: " + e.toString());
            e.printStackTrace();
            call.reject("Error updating metadata: " + e.toString());
        }
    }


    public void initialize() {

    		final Activity activity=getActivity();

    		final Context context=activity.getApplicationContext();

    		final MusicControlsServiceConnection mConnection = new MusicControlsServiceConnection(activity);
    		this.mConnection = mConnection;


    		// avoid spawning multiple receivers
    		if(this.mMessageReceiver != null){

    			try{

    				context.unregisterReceiver(this.mMessageReceiver);

    			} catch(IllegalArgumentException e) {

    				e.printStackTrace();

    			}

    			unregisterMediaButtonEvent();

    		}
    		// end avoid spawn

    		this.mMessageReceiver = new MusicControlsBroadcastReceiver(this);
    		this.registerBroadcaster(this.mMessageReceiver);

    		this.mediaSessionCompat = new MediaSessionCompat(context, "capacitor-music-controls-media-session", null, this.mediaButtonPendingIntent);
    		this.mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

    		MediaSessionCompat.Token _token = this.mediaSessionCompat.getSessionToken();
    		this.token = (android.media.session.MediaSession.Token) _token.getToken();

    		setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

    		this.mediaSessionCompat.setActive(true);
    		this.mediaSessionCompat.setCallback(this.mMediaSessionCallback);

    		this.notification = new MusicControlsNotification(activity, this.notificationID, this.token) {
    			@Override
    			protected void onNotificationUpdated(Notification notification) {
    				mConnection.setNotification(notification, this.infos.isPlaying);
    			}

    			@Override
    			protected void onNotificationDestroyed() {
    				mConnection.setNotification(null, false);
    			}
    		};


    		// Register media (headset) button event receiver
    		try {
    			this.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    			Intent headsetIntent = new Intent("music-controls-media-button");
    			this.mediaButtonPendingIntent = PendingIntent.getBroadcast(
    				context, 0, headsetIntent,
    				Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
    			);
    			this.registerMediaButtonEvent();
    		} catch (Exception e) {
    			this.mediaButtonAccess=false;
    			e.printStackTrace();
    		}

    		Intent startServiceIntent = new Intent(activity, MusicControlsNotificationKiller.class);
    		startServiceIntent.putExtra("notificationID", this.notificationID);
    		activity.bindService(startServiceIntent, this.mConnection, Context.BIND_AUTO_CREATE);

    }

	@PluginMethod()
	public void destroy(PluginCall call) {

		final Activity activity = getActivity();
		final Context context = activity.getApplicationContext();

		this.destroyPlayerNotification();
		this.stopMessageReceiver(context);
		this.unregisterMediaButtonEvent();
		this.stopServiceConnection(activity);


		call.resolve();
	}


	protected void handleOnDestroy() {

		final Activity activity = getActivity();
		final Context context = activity.getApplicationContext();

		this.destroyPlayerNotification();
		this.stopMessageReceiver(context);
		this.unregisterMediaButtonEvent();
		this.stopServiceConnection(activity);

	}

	public void stopMessageReceiver(Context context){

		if(this.mMessageReceiver != null){
        this.mMessageReceiver.stopListening();
        try{
            context.unregisterReceiver(this.mMessageReceiver);
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        }
        this.mMessageReceiver = null;
    }


	}

	public void stopServiceConnection(Activity activity){
		if (this.mConnection != null) {
			Intent stopServiceIntent = new Intent(activity, MusicControlsNotificationKiller.class);
			activity.unbindService(this.mConnection);
			activity.stopService(stopServiceIntent);
			this.mConnection = null;
		}
	}

    @PluginMethod()
    public void updateIsPlaying(PluginCall call) {
        JSObject params = call.getData();

        if (this.notification == null) {
            call.resolve();
            return;
        }

        try {
            final boolean isPlaying = params.getBoolean("isPlaying");
            this.notification.updateIsPlaying(isPlaying);

            // Set the playback state without requesting focus
            if (isPlaying) {
                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
            } else {
                setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
            }

            call.resolve();
        } catch(JSONException e) {
            call.reject("error updateIsPlaying: "+e.toString());
        }
    }

  @PluginMethod()
  public void updateElapsed(PluginCall call) {
      JSObject params = call.getData();

      if (this.notification == null) {
          Log.e(TAG, "updateElapsed: notification is null");
          call.resolve();
          return;
      }

      try {
          final boolean isPlaying = params.getBoolean("isPlaying");
          final long elapsed = params.getLong("elapsed");

          // Store the elapsed time for future reference
          this.lastKnownPosition = elapsed;

          // Update the playing state
          this.notification.updateIsPlaying(isPlaying);

          // Update playback state with elapsed time
          PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
          int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
          float playbackSpeed = isPlaying ? 1.0f : 0.0f;

          if (isPlaying) {
              playbackstateBuilder.setActions(
                  PlaybackStateCompat.ACTION_PLAY_PAUSE |
                  PlaybackStateCompat.ACTION_PAUSE |
                  PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                  PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                  PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                  PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
              );
          } else {
              playbackstateBuilder.setActions(
                  PlaybackStateCompat.ACTION_PLAY_PAUSE |
                  PlaybackStateCompat.ACTION_PLAY |
                  PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                  PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                  PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                  PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
              );
          }

          // Update the state with the actual elapsed time
          playbackstateBuilder.setState(state, elapsed, playbackSpeed);
          this.mediaSessionCompat.setPlaybackState(playbackstateBuilder.build());

          call.resolve();
      } catch(JSONException e) {
          Log.e(TAG, "JSONException in updateElapsed: " + e.toString());
          e.printStackTrace();
          call.reject("error updateElapsed: " + e.toString());
      } catch (NullPointerException e) {
          Log.e(TAG, "NullPointerException in updateElapsed: " + e.toString());
          e.printStackTrace();
          call.reject("NullPointerException in updateElapsed: " + e.toString());
      }
  }

	@PluginMethod()
	public void updateDismissable(PluginCall call) {
		JSObject params = call.getData();
		// final JSONObject params = args.getJSONObject(0);
		try{
			final boolean dismissable = params.getBoolean("dismissable");
			this.notification.updateDismissable(dismissable);
		call.resolve();
		} catch(JSONException e){
			call.reject("error updateDismissable: "+e.toString());
		}

	}

	public void controlsNotification(JSObject ret){

		Log.i(TAG, "controlsNotification fired "  + ret.getString("message"));
		// notifyListeners("controlsNotification", ret);
		this.bridge.triggerJSEvent("controlsNotification", "document", ret.toString());

  }

	private void setMediaPlaybackState(int state) {
        PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
        float playbackSpeed = (state == PlaybackStateCompat.STATE_PLAYING) ? 1.0f : 0.0f;

        if (state == PlaybackStateCompat.STATE_PLAYING) {
            playbackstateBuilder.setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            );
        } else {
            playbackstateBuilder.setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            );
        }

        // Keep using the last known position if it's available
        Log.d(TAG, "setMediaPlaybackState: state=" + state + ", position=" + lastKnownPosition);
        playbackstateBuilder.setState(state, lastKnownPosition, playbackSpeed);

        this.mediaSessionCompat.setPlaybackState(playbackstateBuilder.build());
    }

	// Get image from url
	private Bitmap getBitmapCover(String coverURL){
		try{
			if(coverURL.matches("^(https?|ftp)://.*$"))
				// Remote image
				return this.getBitmapFromURL(coverURL);
			else {
				// Local image
				return this.getBitmapFromLocal(coverURL);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	// get Local image
	private Bitmap getBitmapFromLocal(String localURL){
		try {
			Uri uri = Uri.parse(localURL);
			File file = new File(uri.getPath());
			FileInputStream fileStream = new FileInputStream(file);
			BufferedInputStream buf = new BufferedInputStream(fileStream);
			Bitmap myBitmap = BitmapFactory.decodeStream(buf);
			buf.close();
			return myBitmap;
		} catch (Exception ex) {
			try {
				InputStream fileStream = getActivity().getAssets().open("public/" + localURL);
				BufferedInputStream buf = new BufferedInputStream(fileStream);
				Bitmap myBitmap = BitmapFactory.decodeStream(buf);
				buf.close();
				return myBitmap;
			} catch (Exception ex2) {
				ex.printStackTrace();
				ex2.printStackTrace();
				return null;
			}
		}
	}

	// get Remote image
	private Bitmap getBitmapFromURL(String strURL) {
		try {
			URL url = new URL(strURL);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);
			return myBitmap;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
}
