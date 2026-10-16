package com.nexbytes.bolap3.ui;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nexbytes.bolap3.R;

/**
 * Base for every screen. targetSdk 35+ forces edge-to-edge drawing, so the root view gets
 * padding for the status/navigation bars, display cutout and keyboard on top of the padding
 * declared in XML.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Decide icon colors from the app's own night mode (it can differ from the system's).
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        SystemBarStyle style = night
                ? SystemBarStyle.dark(Color.TRANSPARENT)
                : SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT);
        EdgeToEdge.enable(this, style, style);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        View target = getInsetsTargetId() != View.NO_ID ? findViewById(getInsetsTargetId()) : null;
        if (target == null) {
            ViewGroup content = findViewById(android.R.id.content);
            target = content != null && content.getChildCount() > 0 ? content.getChildAt(0) : null;
        }
        if (target != null) {
            applySystemBarPadding(target);
        }
    }

    /**
     * View that receives the system bar padding. Defaults to the layout root; screens that draw
     * a full-bleed background behind the bars return an inner container instead.
     */
    protected int getInsetsTargetId() {
        return View.NO_ID;
    }

    /** Wires the shared toolbar (layout_toolbar.xml). */
    protected void setupToolbar(@StringRes int title) {
        TextView titleView = findViewById(R.id.tvToolbarTitle);
        titleView.setText(title);
        findViewById(R.id.btnToolbarBack).setOnClickListener(view ->
                getOnBackPressedDispatcher().onBackPressed());
    }

    public static void applySystemBarPadding(View view) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right,
                    bottom + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
