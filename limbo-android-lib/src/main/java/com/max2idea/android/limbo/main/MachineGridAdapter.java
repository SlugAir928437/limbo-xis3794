package com.max2idea.android.limbo.main;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.limbo.emu.lib.R;

import java.util.ArrayList;
import java.util.List;

/** Two-column staggered grid of saved machines. */
public class MachineGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final class Item {
        public final String name;
        public final String meta;
        public final String arch;
        public final String iconUri;
        public final boolean running;

        public Item(String name, String meta, String arch, String iconUri, boolean running) {
            this.name = name;
            this.meta = meta;
            this.arch = arch;
            this.iconUri = iconUri;
            this.running = running;
        }
    }

    public interface Listener {
        void onOpen(Item item);

        void onMore(Item item);

        void onAdd();
    }

    private static final int TYPE_MACHINE = 0;
    private static final int TYPE_ADD = 1;

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;
    private final LruCache<String, Bitmap> iconCache = new LruCache<>(24);

    public MachineGridAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public int machineCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position < items.size() ? TYPE_MACHINE : TYPE_ADD;
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MACHINE) {
            return new MachineHolder(inflater.inflate(R.layout.item_machine, parent, false));
        }
        return new AddHolder(inflater.inflate(R.layout.item_machine_add, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddHolder) {
            ((AddHolder) holder).root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onAdd();
                }
            });
            return;
        }
        final Item item = items.get(position);
        MachineHolder h = (MachineHolder) holder;
        h.icon.setClipToOutline(true);
        h.name.setText(item.name);
        h.meta.setText(item.meta);
        if (item.arch == null || item.arch.isEmpty()) {
            h.arch.setVisibility(View.GONE);
        } else {
            h.arch.setVisibility(View.VISIBLE);
            h.arch.setText(item.arch);
        }
        h.dot.setVisibility(item.running ? View.VISIBLE : View.GONE);

        Bitmap icon = null;
        if (item.iconUri != null) {
            icon = iconCache.get(item.iconUri);
            if (icon == null) {
                icon = UiUtils.loadSampled(h.icon.getContext(), Uri.parse(item.iconUri), 120);
                if (icon != null) {
                    iconCache.put(item.iconUri, icon);
                }
            }
        }
        if (icon != null) {
            h.icon.setImageBitmap(icon);
            h.icon.setVisibility(View.VISIBLE);
            h.letter.setVisibility(View.GONE);
        } else {
            h.icon.setVisibility(View.GONE);
            h.letter.setVisibility(View.VISIBLE);
            h.letter.setText(UiUtils.firstLetter(item.name));
        }

        h.root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpen(item);
            }
        });
        h.root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onMore(item);
                return true;
            }
        });
        h.more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onMore(item);
            }
        });
    }

    static class MachineHolder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView icon;
        final TextView letter;
        final TextView name;
        final TextView meta;
        final TextView arch;
        final View dot;
        final ImageButton more;

        MachineHolder(View view) {
            super(view);
            root = view.findViewById(R.id.machine_card_root);
            icon = view.findViewById(R.id.machine_icon);
            letter = view.findViewById(R.id.machine_letter);
            name = view.findViewById(R.id.machine_name);
            meta = view.findViewById(R.id.machine_meta);
            arch = view.findViewById(R.id.machine_arch);
            dot = view.findViewById(R.id.machine_dot);
            more = view.findViewById(R.id.machine_more);
        }
    }

    static class AddHolder extends RecyclerView.ViewHolder {
        final View root;

        AddHolder(View view) {
            super(view);
            root = view.findViewById(R.id.add_card_root);
        }
    }
}
