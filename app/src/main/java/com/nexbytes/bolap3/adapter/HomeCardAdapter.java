package com.nexbytes.bolap3.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.MaskableFrameLayout;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.media.AlbumArtLoader;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.HomeSection;
import com.nexbytes.bolap3.model.Song;

/** Cards of one Home carousel (songs or albums/playlists). */
public class HomeCardAdapter extends RecyclerView.Adapter<HomeCardAdapter.CardViewHolder> {

    private final HomeSection section;
    private final HomeAdapter.Listener listener;

    public HomeCardAdapter(HomeSection section, HomeAdapter.Listener listener) {
        this.section = section;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        if (section.isSongs()) {
            Song song = section.songs.get(position);
            holder.title.setText(song.getTitle());
            holder.subtitle.setText(song.getArtist());
            AlbumArtLoader.bindSong(holder.cover, song);
            holder.itemView.setOnClickListener(view ->
                    listener.onSongClick(section, holder.getBindingAdapterPosition()));
            holder.itemView.setOnLongClickListener(view -> {
                listener.onSongLongClick(song);
                return true;
            });
        } else {
            GroupItem group = section.groups.get(position);
            holder.title.setText(group.getTitle());
            String count = context.getResources().getQuantityString(R.plurals.song_count,
                    group.getSongCount(), group.getSongCount());
            holder.subtitle.setText(group.getArtistLine() != null ? group.getArtistLine() : count);
            AlbumArtLoader.bind(holder.cover, group.getCoverSong(), group.getKey(), group.getTitle(),
                    R.drawable.ic_album);
            holder.itemView.setOnClickListener(view -> listener.onGroupClick(group));
            holder.itemView.setOnLongClickListener(null);
        }
        holder.itemView.setContentDescription(holder.title.getText() + ", " + holder.subtitle.getText());
    }

    @Override
    public int getItemCount() {
        return section.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title;
        final TextView subtitle;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.imgCardCover);
            title = itemView.findViewById(R.id.tvCardTitle);
            subtitle = itemView.findViewById(R.id.tvCardSubtitle);
            View texts = itemView.findViewById(R.id.cardTexts);
            // Cards squeezed at the carousel edges fade their text out instead of clipping it.
            ((MaskableFrameLayout) itemView).setOnMaskChangedListener(mask -> {
                float ratio = itemView.getWidth() == 0 ? 1f : mask.width() / itemView.getWidth();
                texts.setAlpha(Math.max(0f, Math.min(1f, (ratio - 0.55f) / 0.35f)));
            });
        }
    }
}
