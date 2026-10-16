package com.nexbytes.bolap3.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.nexbytes.bolap3.LauncherActivity;
import com.nexbytes.bolap3.PlayerActivity;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.playback.PlaybackInfo;
import com.nexbytes.bolap3.playback.RemoteCommands;

/** Home-screen widget with cover, title and previous/play-pause/next buttons. */
public class PlayerWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_COMMAND = "com.nexbytes.bolap3.widget.COMMAND";
    private static final String EXTRA_COMMAND = "command";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        manager.updateAppWidget(appWidgetIds, buildViews(context));
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        if (ACTION_COMMAND.equals(intent.getAction())) {
            String command = intent.getStringExtra(EXTRA_COMMAND);
            RemoteCommands.send(context, command != null ? command : RemoteCommands.PLAY_PAUSE, goAsync());
            return;
        }
        super.onReceive(context, intent);
    }

    /** Redraws every instance from {@link PlaybackInfo}. Called by the playback service. */
    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PlayerWidgetProvider.class));
        if (ids.length > 0) {
            manager.updateAppWidget(ids, buildViews(context));
        }
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_player);

        if (PlaybackInfo.hasMedia) {
            views.setTextViewText(R.id.widgetTitle, PlaybackInfo.title);
            views.setTextViewText(R.id.widgetArtist, PlaybackInfo.artist);
        } else {
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_nothing_playing));
            views.setTextViewText(R.id.widgetArtist, "");
        }

        Bitmap artwork = PlaybackInfo.artwork;
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widgetCover, artwork);
        } else {
            views.setImageViewResource(R.id.widgetCover, R.drawable.ic_album);
        }

        boolean playing = PlaybackInfo.playing;
        views.setImageViewResource(R.id.widgetPlayPause, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        views.setContentDescription(R.id.widgetPlayPause,
                context.getString(playing ? R.string.cd_pause : R.string.cd_play));

        views.setOnClickPendingIntent(R.id.widgetPlayPause, commandIntent(context, RemoteCommands.PLAY_PAUSE, 1));
        views.setOnClickPendingIntent(R.id.widgetNext, commandIntent(context, RemoteCommands.NEXT, 2));
        views.setOnClickPendingIntent(R.id.widgetPrevious, commandIntent(context, RemoteCommands.PREVIOUS, 3));

        Intent open = PlaybackInfo.hasMedia
                ? new Intent(context, PlayerActivity.class)
                : new Intent(context, LauncherActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        views.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return views;
    }

    private static PendingIntent commandIntent(Context context, String command, int requestCode) {
        Intent intent = new Intent(context, PlayerWidgetProvider.class)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_COMMAND, command);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
