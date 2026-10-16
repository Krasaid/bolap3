package com.nexbytes.bolap3.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nexbytes.bolap3.R;

/** Bottom sheet with a title and a list of icon + text options. */
public final class OptionsSheet {

    private final BottomSheetDialog dialog;
    private final LinearLayout container;
    private final LayoutInflater inflater;

    public OptionsSheet(Activity activity, CharSequence title, @Nullable CharSequence subtitle) {
        dialog = new BottomSheetDialog(activity);
        inflater = activity.getLayoutInflater();
        View root = inflater.inflate(R.layout.layout_sheet, null);
        container = root.findViewById(R.id.sheetContainer);
        ((TextView) root.findViewById(R.id.tvSheetTitle)).setText(title);
        TextView subtitleView = root.findViewById(R.id.tvSheetSubtitle);
        if (subtitle == null || subtitle.length() == 0) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
        }
        dialog.setContentView(root);
    }

    /** Adds an option; the sheet closes before {@code action} runs. Returns the label view. */
    public TextView add(@DrawableRes int icon, CharSequence text, Runnable action) {
        return add(icon, text, null, action);
    }

    public TextView add(@DrawableRes int icon, CharSequence text, @Nullable @ColorInt Integer tint, Runnable action) {
        View row = inflater.inflate(R.layout.item_sheet_option, container, false);
        ImageView iconView = row.findViewById(R.id.imgOptionIcon);
        TextView textView = row.findViewById(R.id.tvOptionText);
        iconView.setImageResource(icon);
        if (tint != null) {
            iconView.setImageTintList(ColorStateList.valueOf(tint));
            textView.setTextColor(tint);
        }
        textView.setText(text);
        row.setOnClickListener(view -> {
            dialog.dismiss();
            action.run();
        });
        container.addView(row);
        return textView;
    }

    public void show() {
        dialog.show();
    }
}
