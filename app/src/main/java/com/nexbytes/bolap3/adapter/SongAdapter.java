package com.nexbytes.bolap3.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.media.AlbumArtLoader;
import com.nexbytes.bolap3.model.Song;
import com.nexbytes.bolap3.ui.Formatters;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Song rows. Long-press starts multi-selection (taps then toggle songs); in reorder mode a drag
 * handle replaces the duration and menu.
 */
public class SongAdapter extends ListAdapter<Song, SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(Song song, int position);

        void onSongOptionsClick(Song song);

        /** Selection started, changed or ended ({@code count == 0}). */
        default void onSelectionChanged(int count) {
        }

        default void onStartDrag(RecyclerView.ViewHolder holder) {
        }
    }

    private static final DiffUtil.ItemCallback<Song> DIFF = new DiffUtil.ItemCallback<Song>() {
        @Override
        public boolean areItemsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.getUri().equals(newItem.getUri());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.equals(newItem);
        }
    };

    private static final Object PAYLOAD_STATE = new Object();

    private final OnSongClickListener listener;
    @Nullable
    private String currentUri;
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean selectionMode = false;
    private boolean reorderMode = false;

    public SongAdapter(OnSongClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    // ------------------------------------------------------------------ current song

    /** Highlights the song that is currently playing. */
    public void setCurrentUri(@Nullable String uri) {
        if (Objects.equals(currentUri, uri)) {
            return;
        }
        String previous = currentUri;
        currentUri = uri;
        for (int i = 0; i < getItemCount(); i++) {
            String itemUri = getItem(i).getUri();
            if (itemUri.equals(previous) || itemUri.equals(uri)) {
                notifyItemChanged(i, PAYLOAD_STATE);
            }
        }
    }

    // ------------------------------------------------------------------ selection

    public boolean isSelectionMode() {
        return selectionMode;
    }

    /** Selected songs in list order. */
    public List<Song> getSelectedSongs() {
        List<Song> result = new ArrayList<>();
        for (Song song : getCurrentList()) {
            if (selected.contains(song.getUri())) {
                result.add(song);
            }
        }
        return result;
    }

    public void selectAll() {
        for (Song song : getCurrentList()) {
            selected.add(song.getUri());
        }
        selectionMode = true;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_STATE);
        listener.onSelectionChanged(selected.size());
    }

    public void clearSelection() {
        if (!selectionMode && selected.isEmpty()) {
            return;
        }
        selected.clear();
        selectionMode = false;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_STATE);
        listener.onSelectionChanged(0);
    }

    /** Drops selected songs that are no longer in the list (after a rescan or deletion). */
    @Override
    public void onCurrentListChanged(@NonNull List<Song> previousList, @NonNull List<Song> currentList) {
        if (selected.isEmpty()) {
            return;
        }
        Set<String> present = new java.util.HashSet<>();
        for (Song song : currentList) {
            present.add(song.getUri());
        }
        if (selected.retainAll(present)) {
            if (selected.isEmpty()) {
                selectionMode = false;
            }
            listener.onSelectionChanged(selected.size());
        }
    }

    private void toggle(int position) {
        String uri = getItem(position).getUri();
        if (!selected.remove(uri)) {
            selected.add(uri);
        }
        notifyItemChanged(position, PAYLOAD_STATE);
        if (selected.isEmpty()) {
            selectionMode = false;
            notifyItemRangeChanged(0, getItemCount(), PAYLOAD_STATE);
        }
        listener.onSelectionChanged(selected.size());
    }

    // ------------------------------------------------------------------ reordering

    @SuppressLint("NotifyDataSetChanged")
    public void setReorderMode(boolean reorder) {
        if (reorderMode != reorder) {
            reorderMode = reorder;
            clearSelection();
            notifyDataSetChanged();
        }
    }

    public boolean isReorderMode() {
        return reorderMode;
    }

    /** Moves a row while dragging; the new order is saved by the caller when the drag ends. */
    public void moveItem(int from, int to) {
        List<Song> reordered = new ArrayList<>(getCurrentList());
        reordered.add(to, reordered.remove(from));
        submitList(reordered);
    }

    // ------------------------------------------------------------------ binding

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_STATE)) {
            bindState(holder, getItem(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = getItem(position);
        holder.tvSongTitle.setText(song.getTitle());
        holder.tvSongArtist.setText(song.getArtist());
        holder.tvSongDuration.setText(Formatters.duration(song.getDuration(),
                holder.itemView.getContext().getString(R.string.duration_unknown)));
        AlbumArtLoader.bindSong(holder.imgSongCover, song);

        holder.itemView.setOnClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || reorderMode) {
                return;
            }
            if (selectionMode) {
                toggle(adapterPosition);
            } else {
                listener.onSongClick(getItem(adapterPosition), adapterPosition);
            }
        });
        holder.itemView.setOnLongClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || reorderMode) {
                return false;
            }
            if (!selectionMode) {
                selectionMode = true;
                notifyItemRangeChanged(0, getItemCount(), PAYLOAD_STATE);
            }
            toggle(adapterPosition);
            return true;
        });
        holder.btnSongOptions.setOnClickListener(view -> listener.onSongOptionsClick(song));
        holder.imgSongDrag.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                listener.onStartDrag(holder);
            }
            return false;
        });

        bindState(holder, song);
    }

    private void bindState(SongViewHolder holder, Song song) {
        boolean isSelected = selected.contains(song.getUri());
        holder.itemView.setActivated(song.getUri().equals(currentUri));
        holder.itemView.setSelected(isSelected);
        holder.imgSongCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.btnSongOptions.setVisibility(selectionMode || reorderMode ? View.GONE : View.VISIBLE);
        holder.tvSongDuration.setVisibility(reorderMode ? View.GONE : View.VISIBLE);
        holder.imgSongDrag.setVisibility(reorderMode ? View.VISIBLE : View.GONE);
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {

        final ImageView imgSongCover;
        final ImageView imgSongCheck;
        final ImageView imgSongDrag;
        final TextView tvSongTitle;
        final TextView tvSongArtist;
        final TextView tvSongDuration;
        final ImageButton btnSongOptions;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            imgSongCover = itemView.findViewById(R.id.imgSongCover);
            imgSongCheck = itemView.findViewById(R.id.imgSongCheck);
            imgSongDrag = itemView.findViewById(R.id.imgSongDrag);
            tvSongTitle = itemView.findViewById(R.id.tvSongTitle);
            tvSongArtist = itemView.findViewById(R.id.tvSongArtist);
            tvSongDuration = itemView.findViewById(R.id.tvSongDuration);
            btnSongOptions = itemView.findViewById(R.id.btnSongOptions);
        }
    }
}
