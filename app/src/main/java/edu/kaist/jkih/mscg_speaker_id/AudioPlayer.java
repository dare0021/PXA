package edu.kaist.jkih.mscg_speaker_id;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.net.URI;

/**
 * Created by jkih on 2017-04-24.
 *
 * testing class to see if the recording function works
 */

public class AudioPlayer implements SimpleExoPlayer.EventListener
{
    private Context callerContext;
    private SimpleExoPlayer player;

    public AudioPlayer(Context callerContext)
    {
        this.callerContext = callerContext;
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);
        player = ExoPlayerFactory.newSimpleInstance(callerContext, trackSelector);
        player.addListener(this);
        player.setPlayWhenReady(true);
    }

    public void play(String path)
    {
        Log.d("OUT", "Attempt loading " + path);
        // Produces DataSource instances through which media data is loaded.
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(callerContext, Util.getUserAgent(callerContext, "yourApplicationName"));
        // Produces Extractor instances for parsing the media data.
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        // This is the MediaSource representing the media to be played.
        Uri uri = Uri.fromFile(new File(path));
        ExoPlayer.EventListener el = this;
        MediaSource source = new ExtractorMediaSource(uri, dataSourceFactory, extractorsFactory, null, null);
        // Prepare the player with the source.
        player.prepare(source);
        // autoplay is on
        // TODO: auto release
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest)
    {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections)
    {

    }

    @Override
    public void onLoadingChanged(boolean isLoading)
    {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState)
    {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error)
    {
        Log.d("ERR", error.getMessage());
    }

    /**
     * On seek or other abrupt changes in time, e.g. slow network
     */
    @Override
    public void onPositionDiscontinuity()
    {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters)
    {

    }
}