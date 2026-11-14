package com.dudeyy.scrolltrack;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.tracking.TrackingService;
import com.dudeyy.scrolltrack.ui.ScreenHost;
import com.dudeyy.scrolltrack.ui.Theme;
import com.dudeyy.scrolltrack.ui.components.UiKit;
import com.dudeyy.scrolltrack.ui.screens.DashboardScreen;
import com.dudeyy.scrolltrack.ui.screens.HistoryScreen;
import com.dudeyy.scrolltrack.ui.screens.LifeImpactScreen;
import com.dudeyy.scrolltrack.ui.screens.LimitsScreen;
import com.dudeyy.scrolltrack.ui.screens.OnboardingScreen;
import com.dudeyy.scrolltrack.ui.screens.SettingsScreen;

/**
 * The single activity: hosts the five tab screens, owns the floating tab bar,
 * and runs the in-app update check once per launch. All screens are built
 * programmatically (no layout XML) through UiKit.
 */
public class MainActivity extends Activity implements ScreenHost {

    private Prefs prefs;
    private FrameLayout container;
    private LinearLayout bottomNav;
    private View[] screens = new View[5];
    private int currentTab = -1;
    private boolean showingOnboarding = false;

    private static final int[] NAV_ICONS = {
            R.drawable.ic_nav_dash, R.drawable.ic_nav_history, R.drawable.ic_nav_impact,
            R.drawable.ic_nav_limits, R.drawable.ic_nav_settings
    };
    private static final String[] NAV_LABELS = {"Dashboard", "History", "Impact", "Limits", "Settings"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = Prefs.get(this);
        setTheme(Theme.styleFor(this));
        super.onCreate(savedInstanceState);
        Theme.init(this);

        if (!prefs.isOnboardingDone()) {
            showingOnboarding = true;
            OnboardingScreen ob = new OnboardingScreen(this, () -> {
                showingOnboarding = false;
                showMain();
                TrackingService.start(this);
            });
            setContentView(ob);
        } else {
            showMain();
            TrackingService.start(this);
        }
        Theme.applyChrome(this);
    }

    private void showMain() {
        // Liquid Glass tab bar (Apple, iOS 26 era): the tab bar floats above
        // the content — translucent, large radius, hairline border — and the
        // screens scroll beneath it. No true backdrop blur exists on Android,
        // so translucency (~90% surface) is the honest approximation.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg(this));

        container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(container);

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable navBg =
                UiKit.roundedBg(this, Theme.navBg(this), 28);
        navBg.setStroke(Theme.dp(this, 1), Theme.outline(this));
        bottomNav.setBackground(navBg);
        int pv = Theme.dp(this, 8);
        bottomNav.setPadding(0, pv, 0, pv);
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        int m = Theme.dp(this, 16);
        navLp.setMargins(m, 0, m, Theme.dp(this, 12));
        bottomNav.setLayoutParams(navLp);
        root.addView(bottomNav);

        for (int i = 0; i < 5; i++) {
            bottomNav.addView(navItem(i));
        }

        setContentView(root);
        goToTab(ScreenHost.TAB_DASHBOARD);
        com.dudeyy.scrolltrack.util.AppUpdater.maybeCheckOnLaunch(this);
    }

    private View navItem(int index) {
        Context ctx = this;
        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        item.setPadding(0, Theme.dp(ctx, 4), 0, Theme.dp(ctx, 4));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(NAV_ICONS[index]);
        int s = Theme.dp(ctx, 24);
        icon.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        item.addView(icon);

        TextView label = UiKit.tv(ctx, NAV_LABELS[index], 10, Theme.text3(ctx), Theme.body(ctx));
        item.addView(label);

        item.setTag(new View[]{icon, label});
        item.setOnClickListener(v -> goToTab(index));
        return item;
    }

    @Override
    public void goToTab(int tab) {
        if (showingOnboarding || container == null) return;
        if (screens[tab] == null) {
            switch (tab) {
                case ScreenHost.TAB_DASHBOARD: screens[tab] = new DashboardScreen(this, this); break;
                case ScreenHost.TAB_HISTORY: screens[tab] = new HistoryScreen(this, this); break;
                case ScreenHost.TAB_LIFE_IMPACT: screens[tab] = new LifeImpactScreen(this, this); break;
                case ScreenHost.TAB_LIMITS: screens[tab] = new LimitsScreen(this, this); break;
                default: screens[tab] = new SettingsScreen(this, this); break;
            }
            container.addView(screens[tab], new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        for (int i = 0; i < 5; i++) {
            // Screens are created lazily; only touch the ones that exist.
            if (screens[i] != null) {
                screens[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            }
            updateNavItem(i, i == tab);
        }
        currentTab = tab;
    }

    private void updateNavItem(int index, boolean selected) {
        View item = bottomNav.getChildAt(index);
        View[] parts = (View[]) item.getTag();
        ImageView icon = (ImageView) parts[0];
        TextView label = (TextView) parts[1];
        int color = selected ? Theme.lime(this) : Theme.text3(this);
        icon.setColorFilter(color);
        label.setTextColor(color);
        label.setTypeface(Theme.body(this), selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void refreshCurrent() {
        if (showingOnboarding || currentTab < 0 || screens[currentTab] == null) return;
        View v = screens[currentTab];
        if (v instanceof DashboardScreen) ((DashboardScreen) v).refresh();
        else if (v instanceof HistoryScreen) ((HistoryScreen) v).refresh();
        else if (v instanceof LifeImpactScreen) ((LifeImpactScreen) v).refresh();
        else if (v instanceof LimitsScreen) ((LimitsScreen) v).refresh();
        else if (v instanceof SettingsScreen) ((SettingsScreen) v).refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Theme.applyChrome(this);
        if (!showingOnboarding) {
            // Re-apply theme if it changed in settings.
            refreshCurrent();
        }
    }

    @Override
    public Activity activityContext() { return this; }

    @Override
    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 9001);
        }
    }
}
