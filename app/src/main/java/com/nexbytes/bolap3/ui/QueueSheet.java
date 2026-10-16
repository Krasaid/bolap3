package com.nexbytes.bolap3.ui;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.adapter.QueueAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the play queue in the order songs will play. Tap to jump, drag the handle to reorder
 * (only without shuffle, where list order and play order are the same), swipe to remove.
 */
public final class QueueSheet {

    private QueueSheet() {
    }

    public static void show(Activity activity, Player player) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View root = activity.getLayoutInflater().inflate(R.layout.layout_queue_sheet, null);
        RecyclerView list = root.findViewById(R.id.rvQueue);
        TextView hint = root.findViewById(R.id.tvQueueHint);
        TextView empty = root.findViewById(R.id.tvQueueEmpty);

        list.getLayoutParams().height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.65f);
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        list.setLayoutManager(layoutManager);

        final ItemTouchHelper[] touchHelper = new ItemTouchHelper[1];
        final boolean[] dragging = {false};

        QueueAdapter adapter = new QueueAdapter(new QueueAdapter.Listener() {
            @Override
            public void onEntryClick(QueueAdapter.Entry entry) {
                player.seekToDefaultPosition(entry.playlistIndex);
                player.play();
            }

            @Override
            public void onStartDrag(RecyclerView.ViewHolder holder) {
                touchHelper[0].startDrag(holder);
            }
        });
        list.setAdapter(adapter);

        Runnable refresh = () -> {
            boolean shuffle = player.getShuffleModeEnabled();
            Timeline timeline = player.getCurrentTimeline();
            List<QueueAdapter.Entry> entries = new ArrayList<>();
            if (!timeline.isEmpty()) {
                int index = timeline.getFirstWindowIndex(shuffle);
                while (index != C.INDEX_UNSET && entries.size() < timeline.getWindowCount()) {
                    entries.add(new QueueAdapter.Entry(index, player.getMediaItemAt(index)));
                    index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle);
                }
            }
            hint.setVisibility(shuffle ? View.VISIBLE : View.GONE);
            empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.submit(entries, player.getCurrentMediaItemIndex(), !shuffle);
        };

        touchHelper[0] = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.START | ItemTouchHelper.END) {
            private int dragFrom = -1;
            private int dragTo = -1;

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // drag only from the handle
            }

            @Override
            public int getDragDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                return player.getShuffleModeEnabled() ? 0 : super.getDragDirs(recyclerView, holder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPosition = from.getBindingAdapterPosition();
                int toPosition = to.getBindingAdapterPosition();
                if (dragFrom == -1) {
                    dragFrom = fromPosition;
                }
                dragTo = toPosition;
                dragging[0] = true;
                adapter.moveVisual(fromPosition, toPosition);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(recyclerView, holder);
                if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                    player.moveMediaItem(dragFrom, dragTo);
                }
                dragFrom = -1;
                dragTo = -1;
                dragging[0] = false;
                refresh.run();
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                QueueAdapter.Entry entry = adapter.getEntry(holder.getBindingAdapterPosition());
                player.removeMediaItem(entry.playlistIndex);
                Toast.makeText(activity, R.string.queue_removed, Toast.LENGTH_SHORT).show();
            }
        });
        touchHelper[0].attachToRecyclerView(list);

        Player.Listener listener = new Player.Listener() {
            @Override
            public void onEvents(@NonNull Player p, @NonNull Player.Events events) {
                if (!dragging[0] && events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                    refresh.run();
                }
            }
        };
        player.addListener(listener);
        dialog.setOnDismissListener(d -> player.removeListener(listener));

        refresh.run();
        dialog.setContentView(root);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.show();

        // Start with the current song near the top.
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.getEntry(i).playlistIndex == player.getCurrentMediaItemIndex()) {
                layoutManager.scrollToPositionWithOffset(i, 0);
                break;
            }
        }
    }
}
