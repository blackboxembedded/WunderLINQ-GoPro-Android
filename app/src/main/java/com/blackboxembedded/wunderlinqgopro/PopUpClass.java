package com.blackboxembedded.wunderlinqgopro;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

public class PopUpClass {

    //PopupWindow display method
    private static final boolean USE_TEXTURE_VIEW = false;
    private static final boolean ENABLE_SUBTITLES = false;
    private static final String STREAM_URL = "udp://@0.0.0.0:8554";
    private VLCVideoLayout mVideoLayout = null;
    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;


    public void showPopupWindow(final View view) {

        //Create a View object yourself through inflater
        LayoutInflater inflater = (LayoutInflater) view.getContext().getSystemService(view.getContext().LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.camera_preview_activity, null);

        //Specify the length and width through constants
        int width = LinearLayout.LayoutParams.MATCH_PARENT;
        int height = LinearLayout.LayoutParams.MATCH_PARENT;

        //Make Inactive Items Outside Of PopupWindow
        boolean focusable = true;

        //Create a window with our parameters
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
        popupWindow.setBackgroundDrawable(new ColorDrawable());
        //Set the location of the window on the screen
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
        popupWindow.getContentView().setFocusableInTouchMode(true);

        //Initialize the elements of our window, install the handler

        final ArrayList<String> args = new ArrayList<>();
        //args.add("-vvv");
        args.add("");
        mLibVLC = new LibVLC(popupView.getContext(), args);
        mMediaPlayer = new MediaPlayer(mLibVLC);
        mVideoLayout = popupView.findViewById(R.id.video_layout);
        mMediaPlayer.attachViews(mVideoLayout, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW);
        Uri streamUri = Uri.parse(STREAM_URL);
        final Media media = new Media(mLibVLC, streamUri);
        mMediaPlayer.setMedia(media);
        media.release();
        mMediaPlayer.setVolume(0);
        mMediaPlayer.play();


        //Handler for clicking on the inactive zone of the window

        popupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                //Close the window when clicked
                mMediaPlayer.release();
                mLibVLC.release();
                popupWindow.dismiss();
                return true;
            }
        });

        popupView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                //Close the window when clicked
                mMediaPlayer.release();
                mLibVLC.release();
                popupWindow.dismiss();
                return true;
            }

        });
    }

}