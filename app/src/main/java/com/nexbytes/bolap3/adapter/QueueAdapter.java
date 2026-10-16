package com.nexbytes.bolap3.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.recyclerview.widget.RecyclerView;

import com.nexbytes.bolap3.R;

import java.util.ArrayList;
import java.util.List;

/** Queue rows. Each entry keeps its index in the player's playlist. */
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    public static final class Entry {
        public final int playlistIndex;
        public final MediaItem item;

        public Entry(int playlistIndex, MediaItem item) {
            this.playlistIndex = playlistIndex;
            this.item = item;
        }
    }

    public interface Listener {
        void onEntryClick(Entry entry);

        void onStartDrag(RecyclerView.ViewHolder holder);
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Listener listener;
    private int currentPlaylistIndex = -1;
    private boolean dragEnabled = true;

    public QueueAdapter(Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<Entry> newEntries, int currentIndex, boolean dragEnabled) {
        entries.clear();
        entries.addAll(newEntries);
        currentPlaylistIndex = currentIndex;
        this.dragEnabled = dragEnabled;
        notifyDataSetChanged();
    }

    public Entry getEntry(int position) {
        return entries.get(position);
    }

    /** Visual move during a drag; the player is updated when the drag ends. */
    public void moveVisual(int from, int to) {
        entries.add(to, entries.remove(from));
        notifyItemMoved(from, to);
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue, parent, false);
        return new QueueViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        Entry entry = entries.get(position);
        CharSequence title = entry.item.mediaMetadata.title;
        CharSequence artist = entry.item.mediaMetadata.artist;
        holder.title.setText(title != null ? title : entry.item.mediaId);
        holder.artist.setText(artist != null ? artist : "");
        holder.itemView.setActivated(entry.playlistIndex == currentPlaylistIndex);
        holder.itemView.setOnClickListener(view -> listener.onEntryClick(entry));

        holder.dragHandle.setVisibility(dragEnabled ? View.VISIBLE : View.GONE);
        holder.dragHandle.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                listener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class QueueViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView artist;
        final ImageView dragHandle;

        QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvQueueTitle);
            artist = itemView.findViewById(R.id.tvQueueArtist);
            dragHandle = itemView.findViewById(R.id.imgDragHandle);
        }
    }
}
