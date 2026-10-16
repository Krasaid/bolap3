package com.nexbytes.bolap3.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.media.AlbumArtLoader;
import com.nexbytes.bolap3.model.GroupItem;

import java.util.Objects;

public class GroupAdapter extends ListAdapter<GroupItem, GroupAdapter.GroupViewHolder> {

    public interface OnGroupClickListener {
        void onGroupClick(GroupItem group);

        /** Only called for user playlists (the options button is hidden otherwise). */
        void onGroupOptionsClick(GroupItem group);
    }

    private static final DiffUtil.ItemCallback<GroupItem> DIFF = new DiffUtil.ItemCallback<GroupItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull GroupItem oldItem, @NonNull GroupItem newItem) {
            return oldItem.getKey().equals(newItem.getKey());
        }

        @Override
        public boolean areContentsTheSame(@NonNull GroupItem oldItem, @NonNull GroupItem newItem) {
            return oldItem.getTitle().equals(newItem.getTitle())
                    && oldItem.getSongCount() == newItem.getSongCount()
                    && Objects.equals(oldItem.getArtistLine(), newItem.getArtistLine())
                    && Objects.equals(oldItem.getCoverSong(), newItem.getCoverSong());
        }
    };

    private final OnGroupClickListener listener;
    private boolean gridMode = false;

    public GroupAdapter(OnGroupClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Grid of big covers (albums/artists/folders) or the compact list. */
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setGridMode(boolean grid) {
        if (gridMode != grid) {
            gridMode = grid;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return gridMode ? 1 : 0;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 1 ? R.layout.item_group_grid : R.layout.item_library_group;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        GroupItem item = getItem(position);
        Context context = holder.itemView.getContext();

        holder.tvGroupTitle.setText(item.getTitle());
        String count = context.getResources().getQuantityString(R.plurals.song_count,
                item.getSongCount(), item.getSongCount());
        holder.tvGroupSubtitle.setText(item.getArtistLine() != null
                ? context.getString(R.string.group_subtitle_with_artist, item.getArtistLine(), count)
                : count);

        holder.itemView.setOnClickListener(view -> listener.onGroupClick(item));
        if (holder.btnGroupOptions != null) {
            holder.btnGroupOptions.setVisibility(item.isUserPlaylist() ? View.VISIBLE : View.GONE);
            holder.btnGroupOptions.setOnClickListener(view -> listener.onGroupOptionsClick(item));
        }

        boolean smartList = item.isPlaylistLike() && !item.isUserPlaylist();
        if (smartList) {
            AlbumArtLoader.bind(holder.imgGroupIcon, null, null, null, iconFor(item.getType()));
        } else {
            AlbumArtLoader.bind(holder.imgGroupIcon, item.getCoverSong(), item.getKey(), item.getTitle(), iconFor(item.getType()));
        }
    }

    @DrawableRes
    private static int iconFor(String type) {
        switch (type) {
            case GroupItem.TYPE_ARTIST:
                return R.drawable.ic_artist;
            case GroupItem.TYPE_FOLDER:
                return R.drawable.ic_folder;
            case GroupItem.TYPE_PLAYLIST:
                return R.drawable.ic_playlist_play;
            case GroupItem.TYPE_FAVORITES:
                return R.drawable.ic_favorite;
            case GroupItem.TYPE_RECENT:
                return R.drawable.ic_history;
            case GroupItem.TYPE_MOST_PLAYED:
                return R.drawable.ic_trending_up;
            case GroupItem.TYPE_ALBUM:
            default:
                return R.drawable.ic_album;
        }
    }

    public static class GroupViewHolder extends RecyclerView.ViewHolder {

        final ImageView imgGroupIcon;
        final TextView tvGroupTitle;
        final TextView tvGroupSubtitle;
        @androidx.annotation.Nullable
        final ImageButton btnGroupOptions; // list layout only

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            imgGroupIcon = itemView.findViewById(R.id.imgGroupIcon);
            tvGroupTitle = itemView.findViewById(R.id.tvGroupTitle);
            tvGroupSubtitle = itemView.findViewById(R.id.tvGroupSubtitle);
            btnGroupOptions = itemView.findViewById(R.id.btnGroupOptions);
        }
    }
}
