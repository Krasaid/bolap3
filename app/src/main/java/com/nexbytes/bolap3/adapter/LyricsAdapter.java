package com.nexbytes.bolap3.adapter;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.tags.LrcParser;

import java.util.ArrayList;
import java.util.List;

public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.LineViewHolder> {

    public interface OnLineClickListener {
        void onLineClick(LrcParser.Line line);
    }

    private final List<LrcParser.Line> lines = new ArrayList<>();
    private final OnLineClickListener listener;
    private boolean synced;
    private int activeIndex = -1;
    /** Artwork accent for the active line, or null for the theme accent. */
    @androidx.annotation.Nullable
    private Integer accentColor;

    public LyricsAdapter(OnLineClickListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(LrcParser.Lyrics lyrics) {
        lines.clear();
        lines.addAll(lyrics.lines);
        synced = lyrics.synced;
        activeIndex = -1;
        notifyDataSetChanged();
    }

    public void setAccentColor(@androidx.annotation.Nullable Integer color) {
        accentColor = color;
        if (activeIndex >= 0) notifyItemChanged(activeIndex);
    }

    /** @return true if the active line changed. */
    public boolean setActiveIndex(int index) {
        if (index == activeIndex) {
            return false;
        }
        int previous = activeIndex;
        activeIndex = index;
        if (previous >= 0) notifyItemChanged(previous);
        if (index >= 0) notifyItemChanged(index);
        return true;
    }

    @NonNull
    @Override
    public LineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lyric_line, parent, false);
        return new LineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LineViewHolder holder, int position) {
        LrcParser.Line line = lines.get(position);
        TextView text = (TextView) holder.itemView;
        text.setText(line.text.isEmpty() ? "♪" : line.text);

        boolean active = synced && position == activeIndex;
        int color = active
                ? (accentColor != null ? accentColor : MaterialColors.getColor(text, androidx.appcompat.R.attr.colorPrimary))
                : text.getContext().getColor(synced ? R.color.app_text_secondary : R.color.app_text_primary);
        text.setTextColor(color);
        text.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        text.setTextSize(synced ? 20 : 17);

        if (synced) {
            text.setOnClickListener(view -> listener.onLineClick(line));
        } else {
            text.setOnClickListener(null);
            text.setClickable(false);
        }
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class LineViewHolder extends RecyclerView.ViewHolder {
        LineViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
