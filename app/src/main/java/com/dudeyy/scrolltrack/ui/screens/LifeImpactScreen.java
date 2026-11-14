package com.dudeyy.scrolltrack.ui.screens;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dudeyy.scrolltrack.data.repo.StatsRepository;
import com.dudeyy.scrolltrack.ui.ScreenHost;
import com.dudeyy.scrolltrack.ui.Theme;
import com.dudeyy.scrolltrack.ui.components.ProportionBarView;
import com.dudeyy.scrolltrack.ui.components.UiKit;
import com.dudeyy.scrolltrack.ui.components.YearDotsView;
import com.dudeyy.scrolltrack.util.TimeUtils;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Life Impact: what recorded scrolling adds up to per year and across a
 * lifetime, projected from real recorded durations only.
 *
 * Two bases, always labelled:
 *  - Instagram time: every recorded minute with Instagram in the foreground.
 *  - Scrolling sessions: measured time on the Reels surface with recent
 *    activity (recorded since the v2 database); days recorded before that
 *    fall back to time in sessions where swipes were detected (an upper
 *    estimate, since sessions can include pauses).
 *
 * Projections need at least {@link #MIN_DAYS} days of recorded use; below that
 * the screen says so instead of inventing numbers.
 */
public class LifeImpactScreen extends ScrollView {

    private static final int BASIS_INSTAGRAM = 0;
    private static final int BASIS_SCROLLING = 1;
    private static final int[] HORIZONS = {10, 20, 30, 40, 50};
    private static final int MIN_DAYS = 7;
    private static final double DAYS_PER_YEAR = 365.25;
    private static final double MS_PER_HOUR = 3_600_000d;
    private static final double MS_PER_DAY = 86_400_000d;

    private final ScreenHost host;
    private final StatsRepository repo;
    private LinearLayout content;

    private StatsRepository.ImpactData data;
    private boolean loaded;
    private boolean loadFailed;
    private int basis = BASIS_INSTAGRAM;
    private int selectedYears = 30;

    private ValueAnimator heroAnimator;
    private double heroShownYears = 0;
    private boolean animateNextRender;
    private boolean hasRenderedFull;

    public LifeImpactScreen(Context ctx, ScreenHost host) {
        super(ctx);
        this.host = host;
        this.repo = StatsRepository.get(ctx);
        setFillViewport(true);
        build();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        if (heroAnimator != null) heroAnimator.cancel();
        super.onDetachedFromWindow();
    }

    public void refresh() {
        repo.async(repo::loadImpact, d -> {
            loaded = true;
            if (d == null) {
                loadFailed = true;
            } else {
                loadFailed = false;
                data = d;
            }
            render(false);
        });
    }

    // ---------- layout ----------

    private void build() {
        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int hp = Theme.dp(ctx, Theme.MARGIN_H);
        root.setPadding(hp, Theme.dp(ctx, 16), hp, Theme.dp(ctx, Theme.BOTTOM_CLEARANCE));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = UiKit.screenTitle(ctx, "Life Impact");
        root.addView(title);
        root.addView(UiKit.tv(ctx, "What your scrolling adds up to — projected from what ScrollTrack has actually recorded.",
                13, Theme.text2(ctx), Theme.body(ctx)));
        root.addView(UiKit.vspace(ctx, 16));

        content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        render(false);
    }

    private void render(boolean animate) {
        // The first full render animates in; later data reloads settle instantly,
        // while taps on the selector / basis animate on request.
        animateNextRender = animate || !hasRenderedFull;
        Context ctx = getContext();
        content.removeAllViews();
        if (!loaded) {
            content.addView(stateText(ctx, "Loading your history…"));
            return;
        }
        if (loadFailed || data == null) {
            renderUnavailable(ctx);
            return;
        }
        if (data.daysTracked <= 0 || data.totalInstagramMs <= 0) {
            renderEmpty(ctx);
            return;
        }
        if (data.daysTracked < MIN_DAYS) {
            renderInsufficient(ctx);
            return;
        }
        renderFull(ctx);
        hasRenderedFull = true;
    }

    // ---------- states ----------

    private TextView stateText(Context ctx, String text) {
        TextView t = UiKit.tv(ctx, text, 14, Theme.text2(ctx), Theme.body(ctx));
        t.setPadding(0, Theme.dp(ctx, 32), 0, Theme.dp(ctx, 32));
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private void renderUnavailable(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        card.addView(UiKit.caption(ctx, "Unavailable"));
        card.addView(UiKit.vspace(ctx, 8));
        card.addView(UiKit.tv(ctx, "Your recorded history couldn't be read just now. Your data is still stored on this phone — try again.",
                14, Theme.text2(ctx), Theme.body(ctx)));
        card.addView(UiKit.vspace(ctx, 14));
        card.addView(UiKit.quietButton(ctx, "Try again", v -> refresh()));
        content.addView(card);
    }

    private void renderEmpty(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        card.addView(UiKit.caption(ctx, "Nothing to project yet"));
        card.addView(UiKit.vspace(ctx, 8));
        card.addView(UiKit.tv(ctx, "No Instagram use has been recorded so far. Once ScrollTrack has tracked your sessions, this page will show what they add up to over a year and across 10–50 years.",
                14, Theme.text2(ctx), Theme.body(ctx)));
        content.addView(card);
    }

    private void renderInsufficient(Context ctx) {
        double totalHours = data.totalInstagramMs / MS_PER_HOUR;
        LinearLayout card = UiKit.card(ctx);
        card.addView(UiKit.caption(ctx, "Not enough history yet"));
        card.addView(UiKit.vspace(ctx, 10));
        card.addView(UiKit.tv(ctx,
                "Long-term projections need at least " + MIN_DAYS + " days of recorded use to mean anything — "
                        + "a projection from less would just be a guess, so ScrollTrack won't show one yet.",
                14, Theme.text2(ctx), Theme.body(ctx)));
        card.addView(UiKit.vspace(ctx, 16));

        ProportionBarView bar = new ProportionBarView(ctx);
        bar.setFraction(data.daysTracked / (double) MIN_DAYS, false);
        card.addView(bar);
        card.addView(UiKit.vspace(ctx, 8));
        card.addView(UiKit.tv(ctx,
                data.daysTracked + " of " + MIN_DAYS + " days recorded · " + fmtHours(totalHours) + " hours recorded so far",
                13, Theme.text3(ctx), Theme.body(ctx)));
        content.addView(card);
    }

    // ---------- full ----------

    private void renderFull(Context ctx) {
        long basisMs = basis == BASIS_INSTAGRAM ? data.totalInstagramMs : data.scrollingMs;

        content.addView(buildBasisControl(ctx));
        content.addView(UiKit.vspace(ctx, 8));
        TextView expl = UiKit.tv(ctx, basisExplanation(), 13, Theme.text2(ctx), Theme.body(ctx));
        content.addView(expl);
        content.addView(UiKit.vspace(ctx, 18));

        if (basisMs <= 0) {
            LinearLayout card = UiKit.card(ctx);
            card.addView(UiKit.caption(ctx, "No scrolling sessions yet"));
            card.addView(UiKit.vspace(ctx, 8));
            card.addView(UiKit.tv(ctx, "ScrollTrack hasn't recorded any sessions with detected swiping. "
                            + "Swipe counting needs the accessibility service — check Settings → Tracking.",
                    14, Theme.text2(ctx), Theme.body(ctx)));
            content.addView(card);
            return;
        }

        double avgDailyMs = basisMs / (double) data.daysTracked;
        content.addView(buildHero(ctx, avgDailyMs));
        content.addView(UiKit.vspace(ctx, 16));
        content.addView(buildAnnual(ctx, avgDailyMs));
        content.addView(UiKit.vspace(ctx, 22));
        content.addView(buildLadder(ctx, avgDailyMs));
        content.addView(UiKit.vspace(ctx, 20));
        content.addView(buildNote(ctx, basisMs));
    }

    private String basisExplanation() {
        if (basis == BASIS_INSTAGRAM) {
            return "All recorded time with Instagram in the foreground — Reels, feed, messages, everything.";
        }
        return "Measured time on the Reels surface with recent activity. Days recorded before this update use the session estimate instead, which can include pauses.";
    }

    private View buildBasisControl(Context ctx) {
        return UiKit.segmentedControl(ctx,
                new String[]{"Instagram time", "Scrolling sessions"},
                basis == BASIS_INSTAGRAM ? 0 : 1, idx -> {
                    int next = idx == 0 ? BASIS_INSTAGRAM : BASIS_SCROLLING;
                    if (basis != next) {
                        basis = next;
                        render(true);
                    }
                });
    }

    private View buildHero(Context ctx, double avgDailyMs) {
        double lifeMs = avgDailyMs * DAYS_PER_YEAR * selectedYears;
        double lifeHours = lifeMs / MS_PER_HOUR;
        double lifeDays = lifeMs / MS_PER_DAY;
        final double lifeYears = lifeDays / DAYS_PER_YEAR;
        double share = avgDailyMs / MS_PER_DAY;

        LinearLayout card = UiKit.card(ctx);
        card.addView(UiKit.caption(ctx, "If nothing changes"));
        card.addView(UiKit.vspace(ctx, 14));

        // Horizon selector: "Next [10|20|30|40|50] years"
        LinearLayout selRow = UiKit.row(ctx);
        selRow.setGravity(Gravity.CENTER_VERTICAL);
        selRow.addView(UiKit.tv(ctx, "Next", 14, Theme.text2(ctx), Theme.body(ctx)));
        selRow.addView(UiKit.spacer(ctx, 10));
        int selIdx = 0;
        for (int i = 0; i < HORIZONS.length; i++) if (HORIZONS[i] == selectedYears) selIdx = i;
        LinearLayout track = UiKit.segmentedControl(ctx,
                new String[]{"10", "20", "30", "40", "50"}, selIdx, idx -> {
                    if (selectedYears != HORIZONS[idx]) {
                        selectedYears = HORIZONS[idx];
                        render(true);
                    }
                });
        selRow.addView(track, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        selRow.addView(UiKit.spacer(ctx, 10));
        selRow.addView(UiKit.tv(ctx, "years", 14, Theme.text2(ctx), Theme.body(ctx)));
        card.addView(selRow);
        card.addView(UiKit.vspace(ctx, 20));

        final TextView big = UiKit.tv(ctx, fmtLifeYears(heroShownYears), 64, Theme.lime(ctx), Theme.display(ctx));
        big.setTypeface(Theme.display(ctx), Typeface.BOLD);
        big.setLetterSpacing(Theme.TRACKING_HEADLINE);
        card.addView(big);
        card.addView(UiKit.tv(ctx, "years of your life spent scrolling",
                15, Theme.text2(ctx), Theme.body(ctx)));
        card.addView(UiKit.vspace(ctx, 18));
        card.addView(UiKit.divider(ctx));
        card.addView(UiKit.vspace(ctx, 14));

        LinearLayout stats = UiKit.row(ctx);
        stats.addView(statColumn(ctx, "Total hours", fmtHours(lifeHours) + " h"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(statColumn(ctx, "Full days", fmtDays(lifeDays)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(statColumn(ctx, "Share of your time", fmtPercent(share)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(stats);
        card.addView(UiKit.vspace(ctx, 16));

        ProportionBarView bar = new ProportionBarView(ctx);
        bar.setFraction(share, animateNextRender);
        card.addView(bar);
        card.addView(UiKit.vspace(ctx, 8));
        card.addView(UiKit.tv(ctx, "The lime slice is scrolling; the rest is everything else in those "
                        + selectedYears + " years.",
                12, Theme.text3(ctx), Theme.body(ctx)));

        // Count-up (or settle) on the headline figure.
        if (heroAnimator != null) heroAnimator.cancel();
        if (animateNextRender) {
            heroAnimator = ValueAnimator.ofFloat((float) heroShownYears, (float) lifeYears);
            heroAnimator.setDuration(650);
            heroAnimator.setInterpolator(new DecelerateInterpolator());
            heroAnimator.addUpdateListener(a -> {
                heroShownYears = (float) a.getAnimatedValue();
                big.setText(fmtLifeYears(heroShownYears));
            });
            heroAnimator.start();
        } else {
            heroShownYears = lifeYears;
            big.setText(fmtLifeYears(lifeYears));
        }
        return card;
    }

    private LinearLayout statColumn(Context ctx, String captionText, String valueText) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UiKit.caption(ctx, captionText));
        col.addView(UiKit.vspace(ctx, 4));
        TextView v = UiKit.tv(ctx, valueText, Theme.SIZE_TITLE_3, Theme.text(ctx), Theme.display(ctx));
        v.setTypeface(Theme.display(ctx), Typeface.BOLD);
        v.setLetterSpacing(Theme.TRACKING_HEADLINE);
        col.addView(v);
        return col;
    }

    private View buildAnnual(Context ctx, double avgDailyMs) {
        double annualMs = avgDailyMs * DAYS_PER_YEAR;
        double annualHours = annualMs / MS_PER_HOUR;
        double annualDays = annualMs / MS_PER_DAY;

        LinearLayout card = UiKit.card(ctx);
        card.addView(UiKit.caption(ctx, "Every year"));
        card.addView(UiKit.vspace(ctx, 12));

        LinearLayout stats = UiKit.row(ctx);
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView h = UiKit.tv(ctx, fmtHours(annualHours), 32, Theme.text(ctx), Theme.display(ctx));
        h.setTypeface(Theme.display(ctx), Typeface.BOLD);
        h.setLetterSpacing(Theme.TRACKING_HEADLINE);
        left.addView(h);
        left.addView(UiKit.tv(ctx, "hours per year", 13, Theme.text2(ctx), Theme.body(ctx)));
        stats.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(ctx);
        right.setOrientation(LinearLayout.VERTICAL);
        TextView d = UiKit.tv(ctx, fmtDays(annualDays), 32, Theme.lime(ctx), Theme.display(ctx));
        d.setTypeface(Theme.display(ctx), Typeface.BOLD);
        d.setLetterSpacing(Theme.TRACKING_HEADLINE);
        right.addView(d);
        right.addView(UiKit.tv(ctx, "full days per year", 13, Theme.text2(ctx), Theme.body(ctx)));
        stats.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(stats);
        card.addView(UiKit.vspace(ctx, 18));

        YearDotsView dots = new YearDotsView(ctx);
        dots.setDays(annualDays, animateNextRender);
        card.addView(dots);
        card.addView(UiKit.vspace(ctx, 8));
        card.addView(UiKit.tv(ctx, "One dot is one day of the year. The lime dots are the days given to scrolling.",
                12, Theme.text3(ctx), Theme.body(ctx)));
        return card;
    }

    private View buildLadder(Context ctx, double avgDailyMs) {
        LinearLayout section = new LinearLayout(ctx);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(UiKit.caption(ctx, "Lifetime projection"));
        section.addView(UiKit.vspace(ctx, 6));

        for (int i = 0; i < HORIZONS.length; i++) {
            final int years = HORIZONS[i];
            double ms = avgDailyMs * DAYS_PER_YEAR * years;
            double hours = ms / MS_PER_HOUR;
            double days = ms / MS_PER_DAY;
            double lifeYears = days / DAYS_PER_YEAR;
            boolean selected = years == selectedYears;

            LinearLayout row = UiKit.row(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Theme.dp(ctx, 12), Theme.dp(ctx, 13), Theme.dp(ctx, 12), Theme.dp(ctx, 13));
            if (selected) row.setBackground(UiKit.roundedBg(ctx, Theme.limeSoft(ctx), 12));
            row.setOnClickListener(v -> {
                if (selectedYears != years) {
                    selectedYears = years;
                    render(true);
                }
            });

            TextView label = UiKit.tv(ctx, years + " years", 15,
                    selected ? Theme.lime(ctx) : Theme.text(ctx), Theme.body(ctx));
            label.setTypeface(Theme.body(ctx), Typeface.BOLD);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            SpannableStringBuilder sb = new SpannableStringBuilder();
            String head = fmtHours(hours) + " hours";
            sb.append(head);
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(Theme.text(ctx)), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("  ·  ").append(fmtDays(days)).append(" days  ·  ")
                    .append(fmtLifeYears(lifeYears)).append(" yrs");
            TextView values = UiKit.tv(ctx, sb, 13, Theme.text2(ctx), Theme.body(ctx));
            values.setGravity(Gravity.END);
            row.addView(values);
            section.addView(row);

            if (i < HORIZONS.length - 1 && !selected && HORIZONS[i + 1] != selectedYears) {
                section.addView(UiKit.divider(ctx));
            }
        }
        return section;
    }

    private View buildNote(Context ctx, long basisMs) {
        LocalDate first = Instant.ofEpochMilli(data.firstDayTs)
                .atZone(ZoneId.systemDefault()).toLocalDate();
        String basisName = basis == BASIS_INSTAGRAM ? "Instagram time" : "scrolling-session time";
        String text = "How this is calculated — your recorded " + basisName + " ("
                + fmtHours(basisMs / MS_PER_HOUR) + " hours across " + data.daysTracked
                + " days of recorded use, since " + TimeUtils.fullDate(first)
                + ") is extended at the same rate into the years ahead, assuming nothing changes. "
                + "Only recorded time is used; days ScrollTrack didn't observe aren't guessed.";
        TextView t = UiKit.tv(ctx, text, 12, Theme.text3(ctx), Theme.body(ctx));
        t.setLineSpacing(0, 1.35f);
        return t;
    }

    // ---------- formatting ----------

    private static String fmtHours(double hours) {
        if (hours >= 100) return grouped(hours);
        return oneDecimal(hours);
    }

    private static String fmtDays(double days) {
        if (days >= 100) return grouped(days);
        return oneDecimal(days);
    }

    private static String fmtLifeYears(double years) {
        if (years == 0) return "0";
        if (years < 1) return String.format(Locale.US, "%.2f", years);
        return oneDecimal(years);
    }

    private static String fmtPercent(double fraction) {
        double pct = fraction * 100;
        if (pct > 0 && pct < 0.1) return "<0.1%";
        return oneDecimal(pct) + "%";
    }

    private static String grouped(double v) {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.round(v));
    }

    private static String oneDecimal(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
