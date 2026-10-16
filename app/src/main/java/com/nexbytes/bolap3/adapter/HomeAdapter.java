package com.nexbytes.bolap3.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.HomeSection;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Home tab: a greeting header followed by one carousel per section. */
public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        /** A song card was tapped: play the section's songs starting at {@code index}. */
        void onSongClick(HomeSection section, int index);

        void onSongLongClick(Song song);

        void onGroupClick(GroupItem group);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SECTION = 1;

    private final Listener listener;
    private final RecyclerView.RecycledViewPool cardPool = new RecyclerView.RecycledViewPool();
    private final List<HomeSection> sections = new ArrayList<>();
    private int librarySize;

    public HomeAdapter(Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<HomeSection> newSections, int libraryCount) {
        sections.clear();
        sections.addAll(newSections);
        librarySize = libraryCount;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_SECTION;
    }

    @Override
    public int getItemCount() {
        return 1 + sections.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_home_header, parent, false));
        }
        SectionViewHolder holder = new SectionViewHolder(inflater.inflate(R.layout.item_home_section, parent, false));
        holder.carousel.setLayoutManager(new CarouselLayoutManager());
        holder.carousel.setRecycledViewPool(cardPool);
        new CarouselSnapHelper().attachToRecyclerView(holder.carousel);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder header = (HeaderViewHolder) holder;
            Context context = header.itemView.getContext();
            header.greeting.setText(greeting());
            String songs = context.getResources().getQuantityString(R.plurals.song_count, librarySize, librarySize);
            header.summary.setText(context.getString(R.string.home_library_summary, songs));
            return;
        }
        SectionViewHolder sectionHolder = (SectionViewHolder) holder;
        HomeSection section = sections.get(position - 1);
        sectionHolder.title.setText(section.title);
        sectionHolder.carousel.setAdapter(new HomeCardAdapter(section, listener));
    }

    private static int greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) {
            return R.string.home_greeting_morning;
        }
        if (hour >= 12 && hour < 19) {
            return R.string.home_greeting_afternoon;
        }
        return R.string.home_greeting_night;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView greeting;
        final TextView summary;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            greeting = itemView.findViewById(R.id.tvHomeGreeting);
            summary = itemView.findViewById(R.id.tvHomeSummary);
        }
    }

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final RecyclerView carousel;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvSectionTitle);
            carousel = itemView.findViewById(R.id.rvCarousel);
        }
    }
}
