package com.dudeyy.scrolltrack.ui.components;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.dudeyy.scrolltrack.ui.Theme;

/**
 * Programmatic builders from the Apple design.md: grouped cards, SF type
 * scale (Inter), capsule primary buttons, native-style segmented controls,
 * hairline dividers, grouped setting rows. No hardcoded colors or sizes —
 * everything reads Theme tokens.
 */
public final class UiKit {
    private UiKit() {}

    public static GradientDrawable roundedBg(Context ctx, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(Theme.dp(ctx, radiusDp));
        return d;
    }

    /** Grouped card: 18dp radius, no border, no shadow. */
    public static GradientDrawable cardBg(Context ctx) {
        return roundedBg(ctx, Theme.surface(ctx), Theme.RADIUS_CARD);
    }

    public static TextView tv(Context ctx, CharSequence text, float sp, int color, Typeface tf) {
        TextView v = new TextView(ctx);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (tf != null) v.setTypeface(tf);
        v.setLineSpacing(0, 1.15f);
        return v;
    }

    /** Screen title: Title 1, semibold. */
    public static TextView screenTitle(Context ctx, CharSequence text) {
        TextView v = tv(ctx, text, Theme.SIZE_TITLE_1, Theme.text(ctx), Theme.display(ctx));
        v.setTypeface(Theme.display(ctx), Typeface.BOLD);
        v.setLetterSpacing(Theme.TRACKING_HEADLINE);
        return v;
    }

    /** Card section title: Headline, semibold. */
    public static TextView cardTitle(Context ctx, CharSequence text) {
        TextView v = tv(ctx, text, Theme.SIZE_HEADLINE, Theme.text(ctx), Theme.body(ctx));
        v.setTypeface(Theme.body(ctx), Typeface.BOLD);
        v.setLetterSpacing(Theme.TRACKING_HEADLINE);
        return v;
    }

    /** Hero number: big numeric data, in the accent color. */
    public static TextView heroNumber(Context ctx, String text) {
        TextView v = tv(ctx, text, 64, Theme.lime(ctx), Theme.display(ctx));
        v.setTypeface(Theme.display(ctx), Typeface.BOLD);
        v.setLetterSpacing(Theme.TRACKING_HEADLINE);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** Footnote label: 12sp, secondary, sentence case. */
    public static TextView caption(Context ctx, CharSequence text) {
        TextView v = tv(ctx, text, Theme.SIZE_CAPTION, Theme.text2(ctx), Theme.body(ctx));
        v.setLetterSpacing(Theme.TRACKING_CAPTION);
        return v;
    }

    public static LinearLayout card(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(cardBg(ctx));
        int p = Theme.dp(ctx, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout row(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View divider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Theme.outline(ctx));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(ctx, 1)));
        return v;
    }

    /** Hairline inset 16dp from the leading edge, for grouped list rows. */
    public static View dividerInset(Context ctx) {
        View v = divider(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(ctx, 1));
        lp.leftMargin = Theme.dp(ctx, 16);
        v.setLayoutParams(lp);
        return v;
    }

    public static Space spacer(Context ctx, int dp) {
        Space s = new Space(ctx);
        s.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(ctx, dp), Theme.dp(ctx, dp)));
        return s;
    }

    public static View vspace(Context ctx, int dp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(ctx, dp)));
        return v;
    }

    /** Primary action: capsule, accent fill, dark text. */
    public static TextView primaryButton(Context ctx, String text, View.OnClickListener onClick) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextSize(15);
        b.setTypeface(Theme.body(ctx), Typeface.BOLD);
        b.setTextColor(Theme.onLime(ctx));
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundedBg(ctx, Theme.limeFill(ctx), Theme.RADIUS_PILL));
        b.setMinHeight(Theme.dp(ctx, 48));
        int hp = Theme.dp(ctx, 20), vp = Theme.dp(ctx, 12);
        b.setPadding(hp, vp, hp, vp);
        b.setOnClickListener(onClick);
        return b;
    }

    /** Quiet button: elevated background, normal text, 12dp radius. */
    public static TextView quietButton(Context ctx, String text, View.OnClickListener onClick) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextSize(14);
        b.setTypeface(Theme.body(ctx), Typeface.BOLD);
        b.setTextColor(Theme.text(ctx));
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundedBg(ctx, Theme.surface2(ctx), Theme.RADIUS_BUTTON));
        b.setMinHeight(Theme.dp(ctx, 48));
        int hp = Theme.dp(ctx, 16), vp = Theme.dp(ctx, 12);
        b.setPadding(hp, vp, hp, vp);
        b.setOnClickListener(onClick);
        return b;
    }

    /** Secondary button: transparent, 1px accent border, accent text. */
    public static TextView outlineButton(Context ctx, String text, View.OnClickListener onClick) {
        TextView b = quietButton(ctx, text, onClick);
        GradientDrawable d = roundedBg(ctx, 0x00000000, Theme.RADIUS_BUTTON);
        d.setStroke(Theme.dp(ctx, 1), Theme.lime(ctx));
        b.setBackground(d);
        b.setTextColor(Theme.lime(ctx));
        return b;
    }

    public interface SegmentCallback { void onSelected(int index); }

    /**
     * Native-style segmented control: the selected segment is a raised card,
     * not a coloured pill. The container is the control background; segments
     * share the width equally.
     */
    public static LinearLayout segmentedControl(Context ctx, String[] labels, int selected,
                                                SegmentCallback cb) {
        LinearLayout track = new LinearLayout(ctx);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(roundedBg(ctx, Theme.surface2(ctx), Theme.RADIUS_SEGMENT));
        int p = Theme.dp(ctx, 2);
        track.setPadding(p, p, p, p);
        final TextView[] segs = new TextView[labels.length];
        final int[] sel = {selected};
        Runnable update = () -> {
            for (int i = 0; i < segs.length; i++) {
                boolean on = i == sel[0];
                segs[i].setTypeface(Theme.body(ctx), on ? Typeface.BOLD : Typeface.NORMAL);
                segs[i].setTextColor(on ? Theme.text(ctx) : Theme.text2(ctx));
                segs[i].setBackground(on
                        ? roundedBg(ctx, Theme.surface(ctx), Theme.RADIUS_SEGMENT - 2)
                        : null);
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = tv(ctx, labels[i], 13, Theme.text2(ctx), Theme.body(ctx));
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setMinHeight(Theme.dp(ctx, 28));
            int hp = Theme.dp(ctx, 8), vp = Theme.dp(ctx, 6);
            t.setPadding(hp, vp, hp, vp);
            t.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            t.setOnClickListener(v -> {
                if (sel[0] != idx) {
                    sel[0] = idx;
                    update.run();
                    if (cb != null) cb.onSelected(idx);
                }
            });
            segs[i] = t;
            track.addView(t);
        }
        update.run();
        return track;
    }

    /** A labeled row with a trailing view (toggle, chevron, value). */
    public static LinearLayout settingRow(Context ctx, CharSequence title, CharSequence subtitle,
                                          View trailing, View.OnClickListener onClick) {
        return settingRow(ctx, title, subtitle, trailing, onClick, Theme.text(ctx));
    }

    /** Same row, with a custom title color (e.g. red for destructive rows). */
    public static LinearLayout settingRow(Context ctx, CharSequence title, CharSequence subtitle,
                                          View trailing, View.OnClickListener onClick, int titleColor) {
        LinearLayout r = row(ctx);
        r.setPadding(0, Theme.dp(ctx, 12), 0, Theme.dp(ctx, 12));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(lp);
        TextView t = tv(ctx, title, 15, titleColor, Theme.body(ctx));
        t.setTypeface(Theme.body(ctx), Typeface.BOLD);
        texts.addView(t);
        if (subtitle != null && subtitle.length() > 0) {
            TextView s = tv(ctx, subtitle, 13, Theme.text2(ctx), Theme.body(ctx));
            texts.addView(s);
        }
        r.addView(texts);
        if (trailing != null) r.addView(trailing);
        if (onClick != null) {
            r.setOnClickListener(onClick);
            r.setBackground(roundedBg(ctx, 0x00000000, 12));
            r.setClickable(true);
            r.setFocusable(true);
        }
        return r;
    }

    public static TextView chevron(Context ctx) {
        TextView v = tv(ctx, "›", 22, Theme.text3(ctx), Theme.body(ctx));
        v.setPadding(Theme.dp(ctx, 8), 0, 0, 0);
        return v;
    }

    public static AlertDialog.Builder dialogBuilder(Context ctx) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        return b;
    }

    /** Simple number input dialog. */
    public static void numberDialog(Context ctx, String title, String hint, int initial,
                                    NumberCallback cb) {
        EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(hint);
        input.setText(String.valueOf(initial));
        input.setTextColor(Theme.text(ctx));
        input.setHintTextColor(Theme.text3(ctx));
        int p = Theme.dp(ctx, 20);
        input.setPadding(p, Theme.dp(ctx, 12), p, Theme.dp(ctx, 12));
        AlertDialog d = dialogBuilder(ctx)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Save", (di, w) -> {
                    try {
                        cb.onNumber(Integer.parseInt(input.getText().toString().trim()));
                    } catch (NumberFormatException ignored) { }
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        styleDialogButtons(ctx, d);
    }

    public interface NumberCallback { void onNumber(int value); }

    public static void confirmDialog(Context ctx, String title, String message,
                                     String positive, Runnable onConfirm) {
        AlertDialog d = dialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (di, w) -> onConfirm.run())
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        styleDialogButtons(ctx, d);
    }

    public static void infoDialog(Context ctx, String title, String message) {
        AlertDialog d = dialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Got it", null)
                .create();
        d.show();
        styleDialogButtons(ctx, d);
    }

    public static void styleDialogButtons(Context ctx, AlertDialog d) {
        int lime = Theme.lime(ctx);
        if (d.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(lime);
        if (d.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Theme.text2(ctx));
    }

    /** Small status pill: colored dot + label. */
    public static LinearLayout statusPill(Context ctx, int dotColor, String label) {
        LinearLayout p = row(ctx);
        p.setBackground(roundedBg(ctx, Theme.surface2(ctx), 999));
        int hp = Theme.dp(ctx, 10), vp = Theme.dp(ctx, 6);
        p.setPadding(hp, vp, hp, vp);
        View dot = new View(ctx);
        int s = Theme.dp(ctx, 8);
        dot.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(dotColor);
        dot.setBackground(d);
        p.addView(dot);
        Space sp = new Space(ctx);
        sp.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(ctx, 6), 1));
        p.addView(sp);
        TextView t = tv(ctx, label, 12, Theme.text2(ctx), Theme.body(ctx));
        t.setTypeface(Theme.body(ctx), Typeface.BOLD);
        p.addView(t);
        return p;
    }

    public static int statusColor(Context ctx, com.dudeyy.scrolltrack.tracking.TrackingState s) {
        switch (s) {
            case ACTIVE: return Theme.lime(ctx);
            case WAITING: return Theme.amber(ctx);
            case PAUSED: return Theme.text3(ctx);
            case PERMISSION_REQUIRED: return Theme.red(ctx);
            default: return Theme.text3(ctx);
        }
    }

    public static String statusLabel(com.dudeyy.scrolltrack.tracking.TrackingState s) {
        switch (s) {
            case ACTIVE: return "Tracking";
            case WAITING: return "Waiting";
            case PAUSED: return "Paused";
            case PERMISSION_REQUIRED: return "Setup needed";
            default: return "Unavailable";
        }
    }
}
