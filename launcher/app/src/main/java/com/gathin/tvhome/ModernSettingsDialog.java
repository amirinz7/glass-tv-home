package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Modern floating Glass TV Settings modal matching the Apple TV / modern TV OS 2-column UI.
 * Features:
 * - Left column: Search, Profile & User card, and categorized navigation tabs.
 * - Right column: Dynamic content pane (Multi-user profiles, 7 Animal Avatars, Home, etc.).
 * - TV & System: Displays native 4K Ultra HD panel resolution (3840x2160) & UI scale.
 * - Anti-clipping: Zero crop on focus borders and selection items across all TV screens.
 * - All Candy Box customizations consolidated here.
 */
public final class ModernSettingsDialog {

    private static final int TAB_PROFILE = 0;
    private static final int TAB_HOME_SCREEN = 1;
    private static final int TAB_APPEARANCE = 2;
    private static final int TAB_STATUS_WEATHER = 3;
    private static final int TAB_STANDBY = 4;
    private static final int TAB_REMOTE = 5;
    private static final int TAB_CHILD_MODE = 6;
    private static final int TAB_GENERAL = 7;
    private static final int TAB_TV = 8;
    private static final int TAB_CONTROL = 9;

    private static Runnable sDpadLeftTarget;

    private static boolean focusRightPane(ViewGroup container) {
        if (container == null) return false;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == View.VISIBLE) {
                child.requestFocus();
                return true;
            }
            if (child instanceof ViewGroup) {
                if (focusRightPane((ViewGroup) child)) return true;
            }
        }
        return false;
    }

    public static void show(MainActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SoundEffects.playSelect();

        float density = activity.getResources().getDisplayMetrics().density;
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        Typeface outfit = activity.getResources().getFont(R.font.instrument_sans);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        int[] activeTab = new int[]{TAB_HOME_SCREEN};

        // Main Horizontal Container (2 Columns)
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        // ReactDrops Soap Bubble styled translucent liquid glass modal container
        GradientDrawable dialogBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB81E2432, 0xB8101522}
        );
        dialogBg.setCornerRadius(34 * density);
        dialogBg.setStroke((int) (1.2f * density), 0x66FFFFFF); // Soap bubble thin-film iridescent rim
        // The same glass as the home screen: it shows the blurred wallpaper behind the panel.
        root.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{new GlassDrawable(root, 34 * density, density, null, 0.6f).frosted(), GlassPage.veil(34 * density, 0x660E1626)}));
        Backdrop.watch(root);
        final float panelRadius = 34 * density;
        root.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), panelRadius);
            }
        });
        root.setClipToOutline(true);

        // --- LEFT COLUMN: Sidebar ---
        LinearLayout leftCol = new LinearLayout(activity);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        int leftWidth = (int) (250 * density);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(leftWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        leftCol.setLayoutParams(leftLp);
        leftCol.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        leftCol.setClipChildren(false);
        leftCol.setClipToPadding(false);

        // The tab list floats as its own frosted card, set apart from the content.
        LinearLayout.LayoutParams leftLpCard = (LinearLayout.LayoutParams) leftCol.getLayoutParams();
        leftLpCard.setMargins((int) (14 * density), (int) (14 * density), (int) (6 * density), (int) (14 * density));
        GradientDrawable leftCard = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x2EFFFFFF, 0x0DFFFFFF});
        leftCard.setCornerRadius(26 * density);
        leftCard.setStroke((int) (1f * density), 0x40FFFFFF);
        leftCol.setBackground(leftCard);


        final View[] firstNav = new View[1];
        final LinearLayout profileCard = new LinearLayout(activity);
        final LinearLayout rightContent = new LinearLayout(activity);

        // 1. Search Bar (Optimized Soap Bubble pill container)
        LinearLayout searchBox = new LinearLayout(activity);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding((int) (14 * density), 0, (int) (14 * density), 0);
        searchBox.setFocusable(true);
        searchBox.setClickable(true);

        GradientDrawable searchBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x3328354A, 0x241C2534}
        );
        searchBg.setCornerRadius(14 * density);
        searchBg.setStroke((int) (1 * density), 0x2EFFFFFF);
        searchBox.setBackground(searchBg);

        ImageView searchIcon = new ImageView(activity);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(0xB3FFFFFF);
        LinearLayout.LayoutParams sIconLp = new LinearLayout.LayoutParams((int) (15 * density), (int) (15 * density));
        sIconLp.setMarginEnd((int) (8 * density));

        TextView searchPrompt = new TextView(activity);
        searchPrompt.setText(AppLanguage.strSettingsSearch(activity));
        searchPrompt.setTextSize(12.5f);
        searchPrompt.setTextColor(0xB3FFFFFF);
        searchPrompt.setTypeface(outfit, Typeface.NORMAL);
        searchBox.addView(searchPrompt);

        searchBox.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    hasFocus ? new int[]{0x55384B66, 0x4428354A} : new int[]{0x3328354A, 0x241C2534}
            );
            g.setCornerRadius(14 * density);
            g.setStroke((int) ((hasFocus ? 2f : 1f) * density), hasFocus ? 0xFFFFFFFF : 0x2EFFFFFF);
            v.setBackground(g);
            searchPrompt.setTextColor(hasFocus ? 0xFFFFFFFF : 0xB3FFFFFF);
            searchIcon.setColorFilter(hasFocus ? 0xFFFFFFFF : 0xB3FFFFFF);
            v.animate()
                    .scaleX(hasFocus ? 1.02f : 1.0f)
                    .scaleY(hasFocus ? 1.02f : 1.0f)
                    .setInterpolator(hasFocus ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator())
                    .setDuration(hasFocus ? 160 : 100)
                    .start();
        });

        searchBox.setOnClickListener(v -> {
            SoundEffects.playSelect();
            Toast.makeText(activity, "Quick Search Settings & Apps", Toast.LENGTH_SHORT).show();
        });

        searchBox.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (firstNav[0] != null) firstNav[0].requestFocus();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return focusRightPane(rightContent);
                }
            }
            return false;
        });

        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (36 * density));
        sbLp.topMargin = (int) (2 * density);
        sbLp.bottomMargin = (int) (8 * density);
        // The search box is gone; the tabs start at the top.

        // Title of the page, top left.
        TextView settingsTitle = new TextView(activity);
        settingsTitle.setText("Settings");
        settingsTitle.setTextSize(24f);
        settingsTitle.setTextColor(0xFFFFFFFF);
        settingsTitle.setTypeface(outfit, Typeface.BOLD);
        settingsTitle.setPadding((int) (6 * density), (int) (4 * density), 0, (int) (12 * density));
        leftCol.addView(settingsTitle);

        // 2. Profile Card (User Profiles - No mention of backup)
        profileCard.setOrientation(LinearLayout.HORIZONTAL);
        profileCard.setGravity(Gravity.CENTER_VERTICAL);
        profileCard.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
        // Profile removed from the control centre: the card stays out of sight and out of focus.
        profileCard.setFocusable(false);
        profileCard.setClickable(false);
        profileCard.setVisibility(View.GONE);

        FrameLayout profileAvatarView = new FrameLayout(activity);
        ProfileAvatarHelper.bindAvatar(profileAvatarView, ProfileManager.getActiveProfile(activity), activity);
        LinearLayout.LayoutParams pavLp = new LinearLayout.LayoutParams((int) (38 * density), (int) (38 * density));
        pavLp.setMarginEnd((int) (10 * density));
        profileCard.addView(profileAvatarView, pavLp);

        LinearLayout profileTextCol = new LinearLayout(activity);
        profileTextCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvProfileName = new TextView(activity);
        tvProfileName.setText(ProfileManager.getProfileName(activity));
        tvProfileName.setTextSize(14f);
        tvProfileName.setTextColor(0xFFFFFFFF);
        tvProfileName.setTypeface(outfit, Typeface.BOLD);
        tvProfileName.setSingleLine(true);
        profileTextCol.addView(tvProfileName);

        TextView tvProfileSub = new TextView(activity);
        tvProfileSub.setText(AppLanguage.strProfileSubtitle(activity));
        tvProfileSub.setTextSize(11f);
        tvProfileSub.setTextColor(0xB3FFFFFF);
        tvProfileSub.setTypeface(outfit, Typeface.NORMAL);
        profileTextCol.addView(tvProfileSub);

        profileCard.addView(profileTextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (52 * density));
        pcLp.bottomMargin = (int) (8 * density);
        leftCol.addView(profileCard, pcLp);

        // Vertical divider between Left and Right
        View verticalDivider = new View(activity);
        verticalDivider.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams((int) (1 * density), ViewGroup.LayoutParams.MATCH_PARENT);

        // --- RIGHT COLUMN: Content Area ---
        FrameLayout rightCol = new FrameLayout(activity);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightCol.setLayoutParams(rightLp);
        rightCol.setPadding((int) (22 * density), (int) (16 * density), (int) (22 * density), (int) (16 * density));
        rightCol.setClipChildren(false);
        rightCol.setClipToPadding(false);

        ScrollView rightScroll = new ScrollView(activity);
        rightScroll.setFillViewport(true);
        rightScroll.setVerticalScrollBarEnabled(false);
        rightScroll.setClipChildren(false);
        rightScroll.setClipToPadding(false);
        rightScroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rightContent.setOrientation(LinearLayout.VERTICAL);
        rightContent.setClipChildren(false);
        rightContent.setClipToPadding(false);
        rightContent.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (24 * density));
        rightScroll.addView(rightContent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rightCol.addView(rightScroll);

        // Sidebar Navigation Items
        ScrollView leftScroll = new ScrollView(activity);
        leftScroll.setVerticalScrollBarEnabled(false);
        leftScroll.setClipChildren(false);
        leftScroll.setClipToPadding(false);
        LinearLayout menuList = new LinearLayout(activity);
        menuList.setOrientation(LinearLayout.VERTICAL);
        menuList.setClipChildren(false);
        menuList.setClipToPadding(false);
        leftScroll.addView(menuList);
        leftCol.addView(leftScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        class NavItem {
            final int tabId;
            final String label;
            final int iconRes;
            final int squircleBg;
            View rowView;

            NavItem(int tabId, String label, int iconRes, int squircleBg) {
                this.tabId = tabId;
                this.label = label;
                this.iconRes = iconRes;
                this.squircleBg = squircleBg;
            }
        }

        List<NavItem> navItems = new ArrayList<>();
        navItems.add(new NavItem(TAB_HOME_SCREEN, AppLanguage.strHomeScreen(activity), R.drawable.ic_home_screen, 0xFF7B1FA2));
        navItems.add(new NavItem(TAB_APPEARANCE, AppLanguage.strAppearance(activity), R.drawable.ic_appearance, 0xFF0097A7));
        navItems.add(new NavItem(TAB_STATUS_WEATHER, AppLanguage.strStatusBarWeather(activity), R.drawable.ic_screen_weather, 0xFF1976D2));
        navItems.add(new NavItem(TAB_STANDBY, AppLanguage.strStandByMode(activity), R.drawable.ic_standby_clock, 0xFF374151));
        navItems.add(new NavItem(TAB_REMOTE, AppLanguage.strRemoteAccessibility(activity), R.drawable.ic_remote, 0xFF374151));
        navItems.add(new NavItem(TAB_CHILD_MODE, AppLanguage.strChildMode(activity), R.drawable.ic_shield_child, 0xFFD97706));
        navItems.add(new NavItem(TAB_GENERAL, AppLanguage.strGeneral(activity), R.drawable.ic_globe, 0xFF374151));
        String tvLabel = AppLanguage.strTv(activity);
        if (AppUpdateManager.isUpdateAvailable(activity)) {
            tvLabel += " •";
        }
        navItems.add(new NavItem(TAB_TV, tvLabel, R.drawable.ic_tv_display, 0xFF374151));

        Runnable[] renderRightPane = new Runnable[1];

        Runnable updateSidebarSelection = () -> {
            boolean isProfile = (activeTab[0] == TAB_PROFILE);
            rowGlass(profileCard, density, 16, isProfile);

            ProfileAvatarHelper.bindAvatar(profileAvatarView, ProfileManager.getActiveProfile(activity), activity);
            tvProfileName.setText(ProfileManager.getProfileName(activity));

            for (NavItem item : navItems) {
                if (item.rowView == null) continue;
                boolean isSelected = (activeTab[0] == item.tabId);
                if (isSelected) {
                    // The chosen tab is smoked glass with a bright rim: the text always reads.
                    item.rowView.setBackground(GlassPage.selected(item.rowView, density, 20));
                } else {
                    item.rowView.setBackground(new GradientDrawable());
                }
            }
        };

        View[] activeSidebarView = new View[]{profileCard};
        sDpadLeftTarget = () -> {
            if (activeSidebarView[0] != null) {
                activeSidebarView[0].requestFocus();
            }
        };

        profileCard.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                activeSidebarView[0] = profileCard;
                activeTab[0] = TAB_PROFILE;
                updateSidebarSelection.run();
                if (renderRightPane[0] != null) renderRightPane[0].run();
            }
            v.animate()
                    .scaleX(hasFocus ? 1.02f : 1.0f)
                    .scaleY(hasFocus ? 1.02f : 1.0f)
                    .setInterpolator(hasFocus ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator())
                    .setDuration(hasFocus ? 160 : 100)
                    .start();
        });

        profileCard.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    searchBox.requestFocus();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (!navItems.isEmpty() && navItems.get(0).rowView != null) {
                        navItems.get(0).rowView.requestFocus();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return focusRightPane(rightContent);
                }
            }
            return false;
        });

        profileCard.setOnClickListener(v -> {
            SoundEffects.playSelect();
            activeSidebarView[0] = profileCard;
            activeTab[0] = TAB_PROFILE;
            updateSidebarSelection.run();
            if (renderRightPane[0] != null) renderRightPane[0].run();
        });

        for (int i = 0; i < navItems.size(); i++) {
            NavItem item = navItems.get(i);
            final int itemIndex = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (10 * density), (int) (4 * density), (int) (10 * density), (int) (4 * density));
            row.setFocusable(true);
            row.setClickable(true);

            // Squircle Icon
            FrameLayout iconSquircle = new FrameLayout(activity);
            GradientDrawable sqBg = new GradientDrawable();
            sqBg.setColor(item.squircleBg);
            sqBg.setCornerRadius(8 * density);
            iconSquircle.setBackground(sqBg);

            ImageView iv = new ImageView(activity);
            iv.setImageResource(item.iconRes);
            iv.setColorFilter(0xFFFFFFFF);
            FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams((int) (15 * density), (int) (15 * density), Gravity.CENTER);
            iconSquircle.addView(iv, ivLp);

            LinearLayout.LayoutParams sqLp = new LinearLayout.LayoutParams((int) (26 * density), (int) (26 * density));
            sqLp.setMarginEnd((int) (10 * density));

            // Label
            TextView lbl = new TextView(activity);
            lbl.setText(item.label);
            lbl.setTextSize(13f);
            lbl.setTextColor(0xFFFFFFFF);
            lbl.setTypeface(outfit, Typeface.NORMAL);
            row.addView(lbl);

            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    activeSidebarView[0] = row;
                    activeTab[0] = item.tabId;
                    updateSidebarSelection.run();
                    if (renderRightPane[0] != null) renderRightPane[0].run();
                }
                v.animate().scaleX(hasFocus ? 1.015f : 1.0f).scaleY(hasFocus ? 1.015f : 1.0f).setDuration(120).start();
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && itemIndex == 0) {
                        searchBox.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return focusRightPane(rightContent);
                    }
                }
                return false;
            });

            row.setOnClickListener(v -> {
                SoundEffects.playSelect();
                activeSidebarView[0] = row;
                activeTab[0] = item.tabId;
                updateSidebarSelection.run();
                if (renderRightPane[0] != null) renderRightPane[0].run();
            });

            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (36 * density));
            rLp.bottomMargin = (int) (2 * density);
            menuList.addView(row, rLp);
            item.rowView = row;
            if (i == 0) firstNav[0] = row;
        }

        // --- RENDER RIGHT CONTENT PANE ---
        renderRightPane[0] = () -> {
            rightContent.removeAllViews();
            int tab = activeTab[0];

            if (tab == TAB_CONTROL) {
                buildControlTiles(activity, density, outfit, rightContent, dialogHolder);
            } else if (tab == TAB_PROFILE) {
                // === TAB 0: PROFILES & MULTI-USER MANAGEMENT ===
                TextView tvTitle = new TextView(activity);
                tvTitle.setText(AppLanguage.strProfileTitle(activity));
                tvTitle.setTextSize(24);
                tvTitle.setTextColor(0xFFFFFFFF);
                tvTitle.setTypeface(outfit, Typeface.BOLD);
                rightContent.addView(tvTitle);

                ProfileManager.UserProfile activeP = ProfileManager.getActiveProfile(activity);

                // Section 1: Active Profile Avatars
                TextView tvAvatars = new TextView(activity);
                tvAvatars.setText("CURRENT AVATAR (" + activeP.name.toUpperCase() + ")");
                tvAvatars.setTextSize(11.5f);
                tvAvatars.setTextColor(0xB3FFFFFF);
                tvAvatars.setTypeface(outfit, Typeface.BOLD);
                tvAvatars.setPadding(0, (int) (14 * density), 0, (int) (8 * density));
                rightContent.addView(tvAvatars);

                // Modern 3 Avatar Options Card
                LinearLayout avatarSettingsCard = buildCardRow(
                        activity,
                        density,
                        outfit,
                        "Customise Avatar (3 Options)",
                        "Pick a character, search GIFs (Giphy), or upload from your phone",
                        () -> {
                            SoundEffects.playSelect();
                            ProfileSettingsDialog.show(activity, activeP, () -> {
                                updateSidebarSelection.run();
                                if (renderRightPane[0] != null) renderRightPane[0].run();
                                activity.updateTopBarUserIcon();
                            });
                        }
                );
                rightContent.addView(avatarSettingsCard);

                // Edit Active Profile Name Card
                LinearLayout editNameCard = new LinearLayout(activity);
                editNameCard.setOrientation(LinearLayout.HORIZONTAL);
                editNameCard.setGravity(Gravity.CENTER_VERTICAL);
                editNameCard.setPadding((int) (14 * density), (int) (10 * density), (int) (16 * density), (int) (10 * density));
                editNameCard.setFocusable(true);
                editNameCard.setClickable(true);

                rowGlass(editNameCard, density, 14, false);

                // Pencil icon squircle
                FrameLayout pencilSquircle = new FrameLayout(activity);
                GradientDrawable psq = new GradientDrawable();
                psq.setColor(0xFF1E2026);
                psq.setCornerRadius(8 * density);
                pencilSquircle.setBackground(psq);

                ImageView ivPencil = new ImageView(activity);
                ivPencil.setImageResource(R.drawable.ic_pencil);
                FrameLayout.LayoutParams ivpLp = new FrameLayout.LayoutParams((int) (18 * density), (int) (18 * density), Gravity.CENTER);
                pencilSquircle.addView(ivPencil, ivpLp);

                LinearLayout.LayoutParams psqLp = new LinearLayout.LayoutParams((int) (32 * density), (int) (32 * density));
                psqLp.setMarginEnd((int) (12 * density));
                editNameCard.addView(pencilSquircle, psqLp);

                TextView tvEditLabel = new TextView(activity);
                tvEditLabel.setText(AppLanguage.strEditName(activity));
                tvEditLabel.setTextSize(14f);
                tvEditLabel.setTextColor(0xFFFFFFFF);
                tvEditLabel.setTypeface(outfit, Typeface.BOLD);
                editNameCard.addView(tvEditLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvCurName = new TextView(activity);
                tvCurName.setText(activeP.name);
                tvCurName.setTextSize(13.5f);
                tvCurName.setTextColor(0xB3FFFFFF);
                tvCurName.setTypeface(outfit, Typeface.NORMAL);
                editNameCard.addView(tvCurName);

                editNameCard.setOnFocusChangeListener((v, hasFocus) -> {
                    rowGlass(v, density, 14, hasFocus);
                    v.animate().scaleX(hasFocus ? 1.015f : 1.0f).scaleY(hasFocus ? 1.015f : 1.0f).setDuration(120).start();
                });

                editNameCard.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (sDpadLeftTarget != null) {
                            sDpadLeftTarget.run();
                            return true;
                        }
                    }
                    return false;
                });

                editNameCard.setOnClickListener(v -> {
                    SoundEffects.playSelect();
                    showEditNameDialog(activity, density, outfit, newName -> {
                        ProfileManager.setProfileName(activity, newName);
                        tvCurName.setText(newName);
                        tvProfileName.setText(newName);
                        if (renderRightPane[0] != null) renderRightPane[0].run();
                    });
                });

                LinearLayout.LayoutParams encLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                encLp.bottomMargin = (int) (14 * density);
                rightContent.addView(editNameCard, encLp);

                // Section 2: Manage All Users (Add & Delete User)
                List<ProfileManager.UserProfile> allProfiles = ProfileManager.getAllProfiles(activity);
                TextView tvUserList = new TextView(activity);
                tvUserList.setText("PROFILES (" + allProfiles.size() + ")");
                tvUserList.setTextSize(11.5f);
                tvUserList.setTextColor(0xB3FFFFFF);
                tvUserList.setTypeface(outfit, Typeface.BOLD);
                tvUserList.setPadding(0, 0, 0, (int) (8 * density));
                rightContent.addView(tvUserList);

                for (ProfileManager.UserProfile p : allProfiles) {
                    boolean isCur = p.id.equals(activeP.id);

                    LinearLayout pRow = new LinearLayout(activity);
                    pRow.setOrientation(LinearLayout.HORIZONTAL);
                    pRow.setGravity(Gravity.CENTER_VERTICAL);
                    pRow.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (8 * density));
                    pRow.setFocusable(true);
                    pRow.setClickable(true);

                    rowGlass(pRow, density, 14, isCur);

                    FrameLayout ivP = new FrameLayout(activity);
                    ProfileAvatarHelper.bindAvatar(ivP, p, activity);
                    LinearLayout.LayoutParams ivpLp2 = new LinearLayout.LayoutParams((int) (32 * density), (int) (32 * density));
                    ivpLp2.setMarginEnd((int) (10 * density));
                    pRow.addView(ivP, ivpLp2);

                    TextView tvPName = new TextView(activity);
                    tvPName.setText(p.name + (p.isChild ? " (Child mode)" : ""));
                    tvPName.setTextSize(13.5f);
                    tvPName.setTextColor(0xFFFFFFFF);
                    tvPName.setTypeface(outfit, isCur ? Typeface.BOLD : Typeface.NORMAL);
                    pRow.addView(tvPName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    if (isCur) {
                        TextView tvTag = new TextView(activity);
                        tvTag.setText("Active ");
                        tvTag.setTextSize(11f);
                        tvTag.setTextColor(0xFFFFB347);
                        tvTag.setTypeface(outfit, Typeface.BOLD);
                        pRow.addView(tvTag);
                    } else if (allProfiles.size() > 1) {
                        TextView tvDel = new TextView(activity);
                        tvDel.setText("Delete");
                        tvDel.setTextSize(11.5f);
                        tvDel.setTextColor(0xFFF87171);
                        tvDel.setTypeface(outfit, Typeface.NORMAL);
                        pRow.addView(tvDel);
                    }

                    pRow.setOnFocusChangeListener((v, hasFocus) -> {
                        rowGlass(v, density, 14, hasFocus || isCur);
                        v.animate().scaleX(hasFocus ? 1.015f : 1.0f).scaleY(hasFocus ? 1.015f : 1.0f).setDuration(120).start();
                    });

                    pRow.setOnKeyListener((v, keyCode, event) -> {
                        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            if (sDpadLeftTarget != null) {
                                sDpadLeftTarget.run();
                                return true;
                            }
                        }
                        return false;
                    });

                    pRow.setOnClickListener(v -> {
                        SoundEffects.playSelect();
                        if (isCur) {
                            ProfileSettingsDialog.show(activity, p, () -> {
                                updateSidebarSelection.run();
                                if (renderRightPane[0] != null) renderRightPane[0].run();
                                activity.updateTopBarUserIcon();
                            });
                        } else {
                            // Show options: Switch, Manage/Avatar, or Delete
                            AlertDialog.Builder ob = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog);
                            ob.setTitle("User: " + p.name);
                            String[] opts = allProfiles.size() > 1 
                                    ? new String[]{"Switch to This Profile", "Manage Profile & Avatar", "Delete User"} 
                                    : new String[]{"Switch to This Profile", "Manage Profile & Avatar"};
                            ob.setItems(opts, (d, which) -> {
                                if (which == 0) {
                                    ProfileManager.setActiveProfile(activity, p.id);
                                    if (p.isChild) ChildModeManager.setChildModeEnabled(activity, true);
                                    activity.reloadTiles();
                                    activity.updateTopBarUserIcon();
                                    updateSidebarSelection.run();
                                    if (renderRightPane[0] != null) renderRightPane[0].run();
                                    Toast.makeText(activity, "Bertukar ke profil: " + p.name, Toast.LENGTH_SHORT).show();
                                } else if (which == 1) {
                                    ProfileSettingsDialog.show(activity, p, () -> {
                                        updateSidebarSelection.run();
                                        if (renderRightPane[0] != null) renderRightPane[0].run();
                                        activity.updateTopBarUserIcon();
                                    });
                                } else if (which == 2) {
                                    ProfileManager.deleteProfile(activity, p.id);
                                    updateSidebarSelection.run();
                                    if (renderRightPane[0] != null) renderRightPane[0].run();
                                    Toast.makeText(activity, "Profil dipadam", Toast.LENGTH_SHORT).show();
                                }
                            });
                            GlassPage.glassDialog(ob.show());
                        }
                    });

                    LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    prLp.bottomMargin = (int) (6 * density);
                    rightContent.addView(pRow, prLp);
                }

                // Add User Button
                LinearLayout addUserBtn = buildCardRow(activity, density, outfit, "Add New User", "Set up a new profile for a family member or a child", () -> {
                    SoundEffects.playSelect();
                    UserSwitchDialog.showAddProfileDialog(activity, density, outfit, () -> {
                        updateSidebarSelection.run();
                        if (renderRightPane[0] != null) renderRightPane[0].run();
                    });
                });
                rightContent.addView(addUserBtn);

                // Section 3: Your Photos
                TextView tvPhotos = new TextView(activity);
                tvPhotos.setText(AppLanguage.strYourPhotos(activity));
                tvPhotos.setTextSize(11.5f);
                tvPhotos.setTextColor(0xB3FFFFFF);
                tvPhotos.setTypeface(outfit, Typeface.BOLD);
                tvPhotos.setPadding(0, (int) (12 * density), 0, (int) (4 * density));
                rightContent.addView(tvPhotos);

                LinearLayout photoUploadRow = buildCardRow(activity, density, outfit, "Manage Pictures & Phone Upload (QR)", "Scan the QR code to send pictures from your phone straight to the TV", () -> {
                    SoundEffects.playSelect();
                    activity.showCustomWallpaperManagerDialog();
                });
                rightContent.addView(photoUploadRow);

            } else if (tab == TAB_HOME_SCREEN) {
                // === TAB 1: HOME SCREEN (CONSOLIDATING CANDY BOX SETTINGS) ===
                addHeader(rightContent, outfit, "Main Screen");

                rightContent.addView(buildSwitchRow(activity, density, outfit,
                        "Doa Harian & Prayer Times Shelf", "Show a daily doa and local prayer times",
                        activity.isIslamicShelfEnabled(), isChecked -> {
                            activity.setIslamicShelfEnabled(isChecked);
                        }));

                boolean ayahShown = WeeklyAyah.shown(activity);
                rightContent.addView(buildSwitchRow(activity, density, outfit,
                        "Text Above Prayer Times", "Show Daily Doa or Minbar text above the prayer times",
                        ayahShown, isChecked -> {
                            WeeklyAyah.setShown(activity, isChecked);
                            activity.updateIslamicShelf();
                        }));

                int displayMode = MinbarManager.getDisplayMode(activity);
                String modeLabel = displayMode == MinbarManager.MODE_MINBAR ? "Friday Sermon Text (Minbar)" : "Daily Doa (Quran verse)";
                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Display: " + modeLabel,
                        "Choose between Doa Harian or Minbar (Friday sermon by location)",
                        () -> activity.showDisplayModeChoiceDialog(() -> {
                            if (renderRightPane[0] != null) renderRightPane[0].run();
                        })));

                rightContent.addView(buildSwitchRow(activity, density, outfit,
                        "Weather & Air Quality (AQI) Panel", "Show live temperature, weather and haze status",
                        activity.isWeatherWidgetEnabled(), isChecked -> {
                            activity.setWeatherWidgetEnabled(isChecked);
                        }));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Azan Settings & Options", "Azan audio (Makkah/Madinah/KLCC) & prayer time popup",
                        activity::showAzanSettingsDialog));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Gemini AI Assistant", "Smart AI help and your voice assistant",
                        activity::showGeminiAssistantDialog));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Kosongkan Cache TV (Clean TV Cache)", "Free up TV memory and clear temporary files",
                        activity::showCleanTvCacheDialog));

            } else if (tab == TAB_APPEARANCE) {
                // === TAB 2: APPEARANCE ===
                addHeader(rightContent, outfit, "Appearance");

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Wallpaper Collections", "Malaysia in 4K, nature, cities, space and more",
                        () -> { activity.wallDialogFocusKey = null; activity.showWallpaperDialog(); }));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Wallpaper Change Interval", "How often the picture changes by itself (5 min, 10 min and so on)",
                        activity::showWallpaperIntervalDialog));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Language", "Bahasa Melayu, English, Bahasa Indonesia",
                        activity::showLanguageDialog));

                String darkSubtitle;
                int curDark = activity.getSharedPreferences("appearance_prefs", Context.MODE_PRIVATE).getInt("dark_mode_style", MainActivity.DARK_MODE_OFF);
                if (curDark == MainActivity.DARK_MODE_ALWAYS || curDark == MainActivity.DARK_MODE_OLED_BLACK) {
                    darkSubtitle = "On";
                } else if (curDark == MainActivity.DARK_MODE_AUTO) {
                    darkSubtitle = "Follows the time of day (dark 7 pm to 7 am)";
                } else {
                    darkSubtitle = "Off";
                }
                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Dark mode", darkSubtitle,
                        activity::showDarkModeDialog));

            } else if (tab == TAB_STATUS_WEATHER) {
                // === TAB 3: STATUS BAR & WEATHER ===
                addHeader(rightContent, outfit, "Top Bar & Weather");

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Choose TV Location & Prayer Zone", "Supports Malaysia (JAKIM), Singapore (MUIS) & Indonesia (Kemenag) zones",
                        () -> activity.startActivity(new Intent(activity, LocationActivity.class))));

                boolean isWeatherFx = activity.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).getBoolean("dynamic_weather_overlay", true);
                rightContent.addView(buildSwitchRow(activity, density, outfit,
                        "Dynamic Weather (overlay effects)", "Rain, haze and snow particles drawn over your wallpaper",
                        isWeatherFx, isChecked -> {
                            activity.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit().putBoolean("dynamic_weather_overlay", isChecked).apply();
                            activity.testWeatherOverride = null;
                            activity.updateWeatherOverlay(HomeWeather.last(activity));
                            Toast.makeText(activity, isChecked ? "Dynamic weather on" : "Dynamic weather off", Toast.LENGTH_SHORT).show();
                        }));

            } else if (tab == TAB_STANDBY) {
                // === TAB 4: STANDBY MODE ===
                addHeader(rightContent, outfit, "Ambient Clock");

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Test StandBy Screensaver Now", "Preview the full-screen ambient clock",
                        activity::showNightstandScreensaver));

                final boolean photosOn = activity.screensaverPhotos();
                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Screensaver background: " + (photosOn ? "My photos" : "Black"),
                        "Show the photos you uploaded behind the clock. Press OK to switch.",
                        () -> {
                            activity.setScreensaverPhotos(!photosOn);
                            if (renderRightPane[0] != null) renderRightPane[0].run();
                        }));
                final int[] delays = {1, 2, 3, 5, 10, 15, 30, 60};
                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Start screensaver after: " + activity.saverDelayMin() + " min",
                        "How long the TV can sit idle before the clock appears. Press OK to change.",
                        () -> {
                            int cur = activity.saverDelayMin();
                            int next = delays[0];
                            for (int i = 0; i < delays.length; i++) {
                                if (delays[i] > cur) { next = delays[i]; break; }
                            }
                            activity.setSaverDelayMin(next);
                            if (renderRightPane[0] != null) renderRightPane[0].run();
                        }));
                addHeader(rightContent, outfit, "Show on the screensaver");
                final String[][] saverItems = {
                        {"clock", "Clock", "1"}, {"date", "Date", "1"}, {"hijri", "Hijri date", "1"},
                        {"prayer", "Next prayer", "1"},
                        {"weather", "Live weather sky (sun, stars, rain, lightning)", "0"},
                        {"temp", "Temperature and sky text", "0"},
                        {"agenda", "Today's calendar events", "0"},
                        {"red", "Red night clock from 10 pm to 6 am", "1"}};
                for (final String[] it : saverItems) {
                    rightContent.addView(buildSwitchRow(activity, density, outfit, it[1], "",
                            activity.saverOpt(it[0], "1".equals(it[2])),
                            isChecked -> activity.setSaverOpt(it[0], isChecked)));
                }
                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Upload my photos from the phone (QR)",
                        "Scan the code on the TV, pick any pictures. They are also used as wallpapers.",
                        () -> { if (dialogHolder[0] != null) dialogHolder[0].dismiss(); activity.showWallpaperUploadQrDialog(); }));

            } else if (tab == TAB_REMOTE) {
                // === TAB 5: REMOTE & ACCESSIBILITY ===
                addHeader(rightContent, outfit, "Remote & Access");

                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                ResolveInfo resolveInfo = activity.getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
                boolean isDefault = resolveInfo != null && activity.getPackageName().equals(resolveInfo.activityInfo.packageName);
                String activePkg = resolveInfo != null ? resolveInfo.activityInfo.packageName : "None";

                String homeSub = isDefault ? "Active as the TV's default launcher" : "Butang Home masih dikawal oleh: " + activePkg;
                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Set Glass TV as Default Home",
                        homeSub + " — Klik untuk panduan kunci butang Home",
                        () -> showDefaultLauncherDialog(activity, density, outfit, isDefault, activePkg)));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Open TV Accessibility Settings",
                        "Turn on 'Glass TV Home Button Service' to take over the Home button on Google TV & Chromecast",
                        () -> {
                            SoundEffects.playSelect();
                            try {
                                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(activity, "Open TV Settings > System > Accessibility", Toast.LENGTH_SHORT).show();
                            }
                        }));

            } else if (tab == TAB_CHILD_MODE) {
                // === TAB 6: CHILD MODE & APP LOCK ===
                addHeader(rightContent, outfit, "Kids Mode");

                boolean isChildOn = ChildModeManager.isChildModeEnabled(activity);
                Set<String> lockedSet = ChildModeManager.getLockedApps(activity);
                Set<String> hiddenSet = ChildModeManager.getHiddenApps(activity);

                rightContent.addView(buildSwitchRow(activity, density, outfit,
                        "Kids Mode Status",
                        "Lock apps with a 4-digit PIN or hide apps from the home screen",
                        isChildOn, isChecked -> {
                            ChildModeManager.promptPin(activity, "Confirm Security PIN", "Enter the 4-digit PIN to change this mode", new ChildModeManager.PinCallback() {
                                @Override
                                public void onSuccess() {
                                    ChildModeManager.setChildModeEnabled(activity, isChecked);
                                    activity.reloadTiles();
                                    Toast.makeText(activity, isChecked ? "Child mode on" : "Child mode off", Toast.LENGTH_SHORT).show();
                                    if (renderRightPane[0] != null) renderRightPane[0].run();
                                }

                                @Override
                                public void onCancelled() {
                                    if (renderRightPane[0] != null) renderRightPane[0].run();
                                }
                            });
                        }));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Lock Apps with PIN (" + lockedSet.size() + " Apps)",
                        "Choose apps that need the 4-digit PIN to open",
                        () -> showAppLockPicker(activity, density, outfit, false)));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Hide Apps (" + hiddenSet.size() + " Apps)",
                        "Choose apps to hide completely from the dock and home grid",
                        () -> showAppLockPicker(activity, density, outfit, true)));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Change Security PIN",
                        "Change the 4-digit parental PIN (current PIN: " + (ChildModeManager.getPin(activity).equals("0000") ? "0000 [default]" : "••••") + ")",
                        () -> showChangePinDialog(activity, density, outfit)));

            } else if (tab == TAB_GENERAL) {
                // === TAB 7: GENERAL ===
                addHeader(rightContent, outfit, "General");

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Reboot TV", "Restart the Android TV system",
                        activity::showRestartDialog));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Switch Off TV", "Put the Android TV into sleep / off mode",
                        activity::showTurnOffDialog));

                long freeBytes = CacheCleaner.getFreeStorageBytes();
                long totalBytes = CacheCleaner.getTotalStorageBytes();
                String freeStr = android.text.format.Formatter.formatFileSize(activity, freeBytes);
                String totalStr = android.text.format.Formatter.formatFileSize(activity, totalBytes);

                rightContent.addView(buildInfoCard(activity, density, outfit,
                        "Status Storan TV", "Free space: " + freeStr + " of " + totalStr + " (" + (100 - CacheCleaner.usedPercent()) + "% free)"));

            } else if (tab == TAB_TV) {
                // === TAB 8: TV & SISTEM (NATIVE 4K RECOGNITION) ===
                addHeader(rightContent, outfit, "TV & System");

                Display display = activity.getWindowManager().getDefaultDisplay();
                Display.Mode mode = (display != null) ? display.getMode() : null;
                int physW = (mode != null) ? mode.getPhysicalWidth() : dm.widthPixels;
                int physH = (mode != null) ? mode.getPhysicalHeight() : dm.heightPixels;
                float fps = (mode != null) ? mode.getRefreshRate() : 60f;

                String resTitle;
                if (physW >= 3840 || physH >= 2160) {
                    resTitle = "4K Ultra HD (" + physW + " × " + physH + " @" + Math.round(fps) + "Hz)";
                } else if (physW >= 2560) {
                    resTitle = "2K QHD (" + physW + " × " + physH + " @" + Math.round(fps) + "Hz)";
                } else if (physW >= 1920 || dm.widthPixels >= 1920) {
                    resTitle = "Full HD (" + physW + " × " + physH + " @" + Math.round(fps) + "Hz)";
                } else {
                    resTitle = physW + " × " + physH + " @" + Math.round(fps) + "Hz";
                }

                rightContent.addView(buildInfoCard(activity, density, outfit,
                        "Model TV", Build.MODEL + " (" + Build.MANUFACTURER + ")"));

                rightContent.addView(buildInfoCard(activity, density, outfit,
                        "Android TV Version", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"));

                rightContent.addView(buildInfoCard(activity, density, outfit,
                        "TV Display Resolution", resTitle));

                rightContent.addView(buildInfoCard(activity, density, outfit,
                        "UI Framebuffer Resolution", dm.widthPixels + " × " + dm.heightPixels + " (" + Math.round(density * 160) + " dpi · 60fps lancar)"));

                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                ResolveInfo resolveInfo = activity.getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
                boolean isDefault = resolveInfo != null && activity.getPackageName().equals(resolveInfo.activityInfo.packageName);
                String activePkg = resolveInfo != null ? resolveInfo.activityInfo.packageName : "None";

                String homeSub = isDefault ? "Active as the TV's default launcher" : "Butang Home masih dikawal oleh: " + activePkg;
                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Set as Default Home Launcher",
                        homeSub + " — Klik untuk panduan kunci butang Home",
                        () -> showDefaultLauncherDialog(activity, density, outfit, isDefault, activePkg)));

                boolean hasUpdate = AppUpdateManager.isUpdateAvailable(activity);
                String otaTitle = hasUpdate ? "System Update (OTA) •" : "System Update (OTA)";
                String otaSub = hasUpdate
                        ? "New update (v" + AppUpdateManager.getNewVersionName(activity) + ") is ready! Click to update."
                        : "Check for the latest Glass TV Home (" + activity.getAppVersionName() + ")";

                rightContent.addView(buildCardRow(activity, density, outfit,
                        otaTitle, otaSub,
                        () -> {
                            if (hasUpdate) {
                                AppUpdateManager.checkForUpdates(activity, true);
                            } else {
                                activity.showAutoUpdateDialog();
                            }
                        }));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Google Home", "Control your home (opens Google Assistant on TV)",
                        activity::openGoogleHome));

                rightContent.addView(buildCardRow(activity, density, outfit,
                        "Open Full Android TV Settings", "Open the device's original settings menu",
                        () -> activity.startActivity(new Intent(Settings.ACTION_SETTINGS))));
            }
        };

        // Initial render of Tab 0 (Profile)
        updateSidebarSelection.run();
        renderRightPane[0].run();

        root.addView(leftCol, leftLp);
        root.addView(verticalDivider, divLp);
        root.addView(rightCol, rightLp);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog);
        builder.setView(root);
        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        dialog.setOnDismissListener(d -> sDpadLeftTarget = null);
        dialog.show();

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setWindowAnimations(R.style.GlassPop);
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(0.3f);
            if (Build.VERSION.SDK_INT >= 31) {
                // Frosted glass: blur what is behind the panel where the TV allows it.
                try {
                    win.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    android.view.WindowManager.LayoutParams wl = win.getAttributes();
                    wl.setBlurBehindRadius(50);
                    win.setAttributes(wl);
                    win.setBackgroundBlurRadius(60);
                } catch (Throwable ignored) { }
            }
            int w = Math.min((int) (1000 * density), (int) (dm.widthPixels * 0.92f));
            int h = Math.min((int) (580 * density), (int) (dm.heightPixels * 0.92f));
            win.setLayout(w, h);
            win.setGravity(Gravity.CENTER);
        }

        if (!navItems.isEmpty() && navItems.get(0).rowView != null) navItems.get(0).rowView.requestFocus();
    }

    /** Set when a Control Centre tile opens another screen: Home opens the Control Centre again when Back returns. */
    static boolean returnToControl;

    /** The Control Centre on its own, opened from the button in the top bar. */
    public static void showControl(MainActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.restoreHomeWallpaperNow();
        float density = activity.getResources().getDisplayMetrics().density;
        Typeface font = activity.getResources().getFont(R.font.instrument_sans);
        final AlertDialog[] holder = new AlertDialog[1];
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (10 * density));
        buildControlTiles(activity, density, font, panel, holder);
        panel.setPadding((int) (18 * density), (int) (20 * density), (int) (18 * density), (int) (14 * density));
        AlertDialog dlg = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog).setView(panel).create();
        holder[0] = dlg;
        dlg.show();
        try { activity.glassify(dlg); } catch (Throwable ignored) { }
        android.view.Window win = dlg.getWindow();
        if (win != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            win.setLayout(Math.min((int) (470 * density), (int) (dm.widthPixels * 0.5f)), ViewGroup.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            android.view.WindowManager.LayoutParams wl = win.getAttributes();
            wl.x = (int) (24 * density);
            wl.dimAmount = 0.15f;
            win.setAttributes(wl);
        }
        Object first = panel.getTag();
        if (first instanceof View) ((View) first).requestFocus();
    }

    /** The Power menu as three frosted tiles: reboot, switch off, sleep. */
    static void showPower(MainActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        float density = activity.getResources().getDisplayMetrics().density;
        Typeface font = activity.getResources().getFont(R.font.instrument_sans);
        final AlertDialog[] holder = new AlertDialog[1];
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setPadding((int) (22 * density), (int) (22 * density), (int) (22 * density), (int) (20 * density));
        TextView head = new TextView(activity);
        head.setText("Power");
        head.setTextSize(24);
        head.setTextColor(0xFFFFFFFF);
        head.setTypeface(font, Typeface.BOLD);
        head.setPadding(0, 0, 0, (int) (14 * density));
        panel.addView(head);
        LinearLayout row = bentoRow(activity, density);
        LinearLayout t1 = controlTile(activity, density, font, "R", "Reboot", "Restart the TV", 0, false, true,
                () -> { holder[0].dismiss(); activity.showRestartDialog(); });
        LinearLayout t2 = controlTile(activity, density, font, "O", "Switch off", "Turn the TV off", 0, false, true,
                () -> { holder[0].dismiss(); activity.showTurnOffDialog(); });
        LinearLayout t3 = controlTile(activity, density, font, "Z", "Sleep", "Screen off", 0, false, true,
                () -> { holder[0].dismiss(); HomeKeyService.sleepScreen(); });
        addTile(row, t1, 1f, 110, density);
        addTile(row, t2, 1f, 110, density);
        addTile(row, t3, 1f, 110, density);
        panel.addView(row);
        AlertDialog dlg = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog).setView(panel).create();
        holder[0] = dlg;
        dlg.show();
        try { activity.glassify(dlg); } catch (Throwable ignored) { }
        android.view.Window win = dlg.getWindow();
        if (win != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            win.setLayout(Math.min((int) (560 * density), (int) (dm.widthPixels * 0.6f)), ViewGroup.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.CENTER);
        }
        t1.requestFocus();
    }

    private static void glassShow(MainActivity activity, android.app.AlertDialog dlg) {
        // Closing one of the Control Centre's own dialogs lands back on the Control Centre.
        dlg.setOnDismissListener(x -> showControl(activity));
        dlg.show();
        try { activity.glassify(dlg); } catch (Throwable ignored) { }
    }

    /** The doa now on the home screen, in full. */
    private static void showDoaToday(MainActivity activity) {
        ZikirList.Zikir z = DoaList.current();
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(activity)
                .setTitle("Doa  -  " + z.count)
                .setMessage(activity.richDoa(z.arabic, z.rumi, z.meaning))
                .setPositiveButton("Close", null).create();
        glassShow(activity, dlg);
    }

    /** A zikir counter: OK adds one. */
    private static void showTasbih(MainActivity activity) {
        final int[] n = {0};
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(activity)
                .setTitle("Tasbih")
                .setMessage("0\n\nPress OK to count")
                .setPositiveButton("Count", null)
                .setNeutralButton("Reset", null)
                .setNegativeButton("Close", null).create();
        glassShow(activity, dlg);
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            n[0]++;
            dlg.setMessage(n[0] + (n[0] == 33 || n[0] == 99 ? "   (done)" : "") + "\n\nPress OK to count");
        });
        dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            n[0] = 0;
            dlg.setMessage("0\n\nPress OK to count");
        });
    }

    /** What the launcher can actually see: build number, app wallpaper files and calendar sources. For when something does not show up. */
    private static String diagnostics(MainActivity activity) {
        StringBuilder b = new StringBuilder();
        try {
            long code = Build.VERSION.SDK_INT >= 28
                    ? activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).getLongVersionCode()
                    : activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
            b.append("\nBuild number: ").append(code);
        } catch (Exception ignored) { }
        try {
            java.io.File[] w = new java.io.File(activity.getExternalFilesDir(null), "app-wallpapers").listFiles();
            b.append("\nApp wallpapers on the TV: ").append(w == null ? 0 : w.length);
            java.io.File cfg = new java.io.File(activity.getExternalFilesDir(null), "config.json");
            b.append("\nSettings file (config.json): ").append(cfg.isFile() ? "yes" : "no");
        } catch (Exception ignored) { }
        try {
            String urls = CalendarData.myUrl(activity).trim();
            int linked = urls.isEmpty() ? 0 : urls.split("\\s+").length;
            java.time.LocalDate today = java.time.LocalDate.now();
            int mine = 0, all = CalendarData.items(activity, today.minusYears(1), today.plusYears(1)).size();
            for (CalendarData.Item it : CalendarData.items(activity, today.minusYears(1), today.plusYears(1))) if (!it.holiday) mine++;
            b.append("\nCalendars linked: ").append(linked).append("  (").append(mine).append(" of my events, ").append(all - mine).append(" holidays)");
        } catch (Exception ignored) { }
        return b.toString();
    }

    private static void showTvInfo(MainActivity activity) {
        String ip = "-";
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> e = java.net.NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                java.net.NetworkInterface ni = e.nextElement();
                for (java.util.Enumeration<java.net.InetAddress> a = ni.getInetAddresses(); a.hasMoreElements();) {
                    java.net.InetAddress ia = a.nextElement();
                    if (!ia.isLoopbackAddress() && ia instanceof java.net.Inet4Address) ip = ia.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        String msg = "Model: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\nAndroid: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\nIP address: " + ip
                + "\nGlass TV Home: " + activity.getAppVersionName() + diagnostics(activity);
        glassShow(activity, new android.app.AlertDialog.Builder(activity).setTitle("TV info").setMessage(msg)
                .setPositiveButton("Close", null).create());
    }

    /** The thin outline icon for a Control Centre or Power tile, by its key letter. */
    private static int controlIcon(String key, boolean active) {
        switch (key) {
            case "C": return R.drawable.ic_calendar;
            case "W": return R.drawable.ic_screen_weather;
            case "P": return R.drawable.ic_c_mosque;
            case "D": return R.drawable.ic_c_book;
            case "T": return R.drawable.ic_c_beads;
            case "G": return R.drawable.ic_home_screen;
            case "M": return active ? R.drawable.ic_c_mute : R.drawable.ic_c_sound;
            case "i": return R.drawable.ic_c_info;
            case "A": return R.drawable.ic_c_chip;
            case "R": return R.drawable.ic_c_refresh;
            case "O": return R.drawable.ic_power;
            case "Z": return R.drawable.ic_c_sleep;
            default: return R.drawable.ic_c_info;
        }
    }

    /** One Control Centre tile: its own colour, a letter badge, a title and a line under it. */
    private static LinearLayout controlTile(MainActivity activity, float density, Typeface font, String glyph,
                                            String title, String sub, int accent, boolean active, boolean big, Runnable action) {
        final LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.BOTTOM | Gravity.START);
        tile.setFocusable(true);
        tile.setClickable(true);
        final int pad = (int) (12 * density);
        tile.setPadding(pad, pad, pad, pad);
        applyControlLook(tile, density, accent, active, false);

        android.widget.ImageView badge = new android.widget.ImageView(activity);
        badge.setImageResource(controlIcon(glyph, active));
        badge.setColorFilter(0xFFFFFFFF);
        int bs = (int) ((big ? 30 : 26) * density);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(bs, bs);
        blp.bottomMargin = (int) (8 * density);
        tile.addView(badge, blp);

        TextView t1 = new TextView(activity);
        t1.setText(title);
        t1.setTextSize(big ? 17f : 13.5f);
        t1.setTextColor(0xFFFFFFFF);
        t1.setTypeface(font, Typeface.BOLD);
        t1.setSingleLine(true);
        tile.addView(t1);

        TextView t2 = new TextView(activity);
        t2.setText(sub);
        t2.setTextSize(10f);
        t2.setTextColor(0xCCFFFFFF);
        t2.setTypeface(font, Typeface.NORMAL);
        t2.setSingleLine(true);
        t2.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(t2);

        tile.setOnFocusChangeListener((v, has) -> {
            applyControlLook(v, density, accent, active, has);

        });
        tile.setOnClickListener(v -> action.run());
        return tile;
    }

    /** Frosted glass tile with a wash of its own colour; a white rim and a stronger wash while selected. */
    private static void applyControlLook(View tile, float density, int accent, boolean active, boolean focused) {
        if (focused) {
            tile.setBackground(GlassPage.selected(tile, density, 18));
            return;
        }
        float r = 18 * density;
        GlassDrawable glass = new GlassDrawable(tile, r, density, null, 0.4f).frosted();
        Backdrop.watch(tile);
        int top = active ? 0x40FFFFFF : 0x1FFFFFFF;
        int bottom = active ? 0x1AFFFFFF : 0x08FFFFFF;
        GradientDrawable tint = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        tint.setCornerRadius(r);
        tint.setStroke((int) ((active ? 1.5f : 1f) * density), active ? 0x99FFFFFF : 0x40FFFFFF);
        tile.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{glass, GlassPage.veil(r, 0x4D0E1626), tint}));
    }

    /** The Control Centre: coloured tiles of different sizes, stacked in a bento grid. OK on a tile runs it. */
    private static void buildControlTiles(MainActivity activity, float density, Typeface font, LinearLayout out, AlertDialog[] holder) {
        TextView head = new TextView(activity);
        head.setText("Control Centre");
        head.setTextSize(26);
        head.setTextColor(0xFFFFFFFF);
        head.setTypeface(font, Typeface.BOLD);
        out.addView(head);
        TextView sub = new TextView(activity);
        sub.setText(new java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(new java.util.Date()));
        sub.setTextSize(12);
        sub.setTextColor(0xB3FFFFFF);
        sub.setTypeface(font, Typeface.NORMAL);
        sub.setPadding(0, 0, 0, (int) (14 * density));
        out.addView(sub);

        final Runnable close = () -> { if (holder[0] != null) holder[0].dismiss(); };
        final Runnable again = () -> { close.run(); ModernSettingsDialog.showControl(activity); };
        final android.media.AudioManager am = (android.media.AudioManager) activity.getSystemService(android.content.Context.AUDIO_SERVICE);
        final boolean muted = am != null && am.isStreamMute(android.media.AudioManager.STREAM_MUSIC);
        final String doaName = DoaList.current().count;

        final LinearLayout calendar = controlTile(activity, density, font, "C", "Calendar", "Holidays and events", 0xFFFF7A59, false, true,
                () -> { returnToControl = true; close.run(); activity.startActivity(new Intent(activity, CalendarActivity.class)); });
        final LinearLayout weather = controlTile(activity, density, font, "W", "Weather", "Radar, forecast", 0xFF3D9BFF, false, false,
                () -> { returnToControl = true; close.run(); activity.startActivity(new Intent(activity, WeatherActivity.class)); });
        final LinearLayout prayer = controlTile(activity, density, font, "P", "Prayer times", "Next 7 days", 0xFF1FBF9A, false, true,
                () -> { returnToControl = true; close.run(); activity.startActivity(new Intent(activity, PrayerActivity.class)); });
        final LinearLayout doa = controlTile(activity, density, font, "D", "Doa of the day", doaName, 0xFF9B7BFF, false, false,
                () -> { close.run(); showDoaToday(activity); });
        final LinearLayout tasbih = controlTile(activity, density, font, "T", "Tasbih", "Count zikir", 0xFFFFB347, false, false,
                () -> { close.run(); showTasbih(activity); });
        final LinearLayout home = controlTile(activity, density, font, "G", "Google Home", "Lights, plugs and cameras", 0xFFEF5DA8, false, true,
                () -> { close.run(); activity.openGoogleHome(); });
        final LinearLayout mute = controlTile(activity, density, font, "M", muted ? "Unmute" : "Mute", muted ? "Sound off" : "Sound on", 0xFFFF5A6E, muted, false,
                () -> {
                    if (am != null) am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                            muted ? android.media.AudioManager.ADJUST_UNMUTE : android.media.AudioManager.ADJUST_MUTE, 0);
                    again.run();
                });
        final LinearLayout info = controlTile(activity, density, font, "i", "TV info", "Model, IP, version", 0xFF7C7367, false, false,
                () -> { close.run(); showTvInfo(activity); });
        final LinearLayout android = controlTile(activity, density, font, "A", "Android", "Original settings", 0xFF5B6472, false, false,
                () -> { returnToControl = true; close.run(); activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); });
        final LinearLayout refresh = controlTile(activity, density, font, "R", "Refresh", "Calendar", 0xFF2BB5C4, false, false,
                () -> {
                    close.run();
                    CalendarData.refresh(activity, true, null);
                    Toast.makeText(activity, "Refreshing the calendar...", Toast.LENGTH_SHORT).show();
                });

        // Row 1: a wide tile and a narrow one.
        LinearLayout r1 = bentoRow(activity, density);
        addTile(r1, calendar, 2f, 96, density);
        addTile(r1, weather, 1f, 96, density);
        out.addView(r1);
        // Row 2: one tall tile on the left; two stacked on the right.
        LinearLayout r2 = bentoRow(activity, density);
        addTile(r2, prayer, 1f, 156, density);
        LinearLayout stack = new LinearLayout(activity);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setClipChildren(false);
        addTile(stack, doa, 0f, 74, density);
        addTile(stack, tasbih, 0f, 74, density);
        r2.addView(stack, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        out.addView(r2);
        // Row 3: Google Home is wide, mute is narrow.
        LinearLayout r3 = bentoRow(activity, density);
        addTile(r3, home, 2f, 84, density);
        addTile(r3, mute, 1f, 84, density);
        out.addView(r3);
        // Row 4: three small ones.
        LinearLayout r4 = bentoRow(activity, density);
        addTile(r4, info, 1f, 78, density);
        addTile(r4, android, 1f, 78, density);
        addTile(r4, refresh, 1f, 78, density);
        out.addView(r4);

        out.setTag(calendar);
    }

    private static LinearLayout bentoRow(MainActivity activity, float density) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        return row;
    }

    private static void addTile(LinearLayout parent, View tile, float weight, int heightDp, float density) {
        boolean horizontal = parent.getOrientation() == LinearLayout.HORIZONTAL;
        LinearLayout.LayoutParams lp = horizontal
                ? new LinearLayout.LayoutParams(0, (int) (heightDp * density), weight)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (heightDp * density));
        int m = (int) (4 * density);
        lp.setMargins(m, m, m, m);
        parent.addView(tile, lp);
    }

    private static void addHeader(LinearLayout root, Typeface font, String title) {
        TextView tv = new TextView(root.getContext());
        tv.setText(title);
        tv.setTextSize(24);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTypeface(font, Typeface.BOLD);
        tv.setPadding(0, 0, 0, (int) (14 * root.getContext().getResources().getDisplayMetrics().density));
        root.addView(tv);
    }

    /** A row on the panel: its own glass, and a brighter fill with a white rim while selected. */
    private static void applySoapBubbleCardBg(View v, float density, boolean hasFocus) {
        rowGlass(v, density, 20, hasFocus);
    }

    /** A row of the settings panel: frosted glass under a light smoky wash; smoked glass with a bright rim when chosen. */
    private static void rowGlass(View v, float density, float radiusDp, boolean focused) {
        if (focused) {
            v.setBackground(GlassPage.selected(v, density, radiusDp));
            return;
        }
        float r = radiusDp * density;
        GlassDrawable g = new GlassDrawable(v, r, density, null, 0.3f).frosted();
        Backdrop.watch(v);
        v.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{g, GlassPage.veil(r, 0x4D0E1626)}));
    }

    private static LinearLayout buildCardRow(Context ctx, float density, Typeface font, String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (18 * density), (int) (12 * density), (int) (18 * density), (int) (12 * density));
        card.setFocusable(true);
        card.setClickable(true);

        applySoapBubbleCardBg(card, density, false);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextSize(14f);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(font, Typeface.BOLD);
        card.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextSize(12);
            tvSub.setTextColor(0xB3FFFFFF);
            tvSub.setTypeface(font, Typeface.NORMAL);
            tvSub.setPadding(0, (int) (2 * density), 0, 0);
            card.addView(tvSub);
        }

        card.setOnFocusChangeListener((v, hasFocus) -> {
            applySoapBubbleCardBg(v, density, hasFocus);
            v.animate()
                    .scaleX(hasFocus ? 1.025f : 1.0f)
                    .scaleY(hasFocus ? 1.025f : 1.0f)
                    .setInterpolator(hasFocus ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator())
                    .setDuration(hasFocus ? 160 : 100)
                    .start();
        });

        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (sDpadLeftTarget != null) {
                    sDpadLeftTarget.run();
                    return true;
                }
            }
            return false;
        });

        card.setOnClickListener(v -> {
            if (action != null) action.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * density);
        card.setLayoutParams(lp);
        return card;
    }

    private static LinearLayout buildSwitchRow(Context ctx, float density, Typeface font, String title, String subtitle, boolean isChecked, SwitchCallback callback) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int) (18 * density), (int) (12 * density), (int) (18 * density), (int) (12 * density));
        card.setFocusable(true);
        card.setClickable(true);

        applySoapBubbleCardBg(card, density, false);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextSize(14f);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(font, Typeface.BOLD);
        textCol.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextSize(12);
            tvSub.setTextColor(0xB3FFFFFF);
            tvSub.setTypeface(font, Typeface.NORMAL);
            tvSub.setPadding(0, (int) (2 * density), 0, 0);
            textCol.addView(tvSub);
        }

        card.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(ctx);
        sw.setChecked(isChecked);
        sw.setFocusable(false);
        sw.setClickable(false);
        card.addView(sw);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            applySoapBubbleCardBg(v, density, hasFocus);
            v.animate()
                    .scaleX(hasFocus ? 1.025f : 1.0f)
                    .scaleY(hasFocus ? 1.025f : 1.0f)
                    .setInterpolator(hasFocus ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator())
                    .setDuration(hasFocus ? 160 : 100)
                    .start();
        });

        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (sDpadLeftTarget != null) {
                    sDpadLeftTarget.run();
                    return true;
                }
            }
            return false;
        });

        card.setOnClickListener(v -> {
            SoundEffects.playSelect();
            boolean newVal = !sw.isChecked();
            sw.setChecked(newVal);
            if (callback != null) callback.onChanged(newVal);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * density);
        card.setLayoutParams(lp);
        return card;
    }

    private static LinearLayout buildInfoCard(Context ctx, float density, Typeface font, String title, String value) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int) (16 * density), (int) (10 * density), (int) (16 * density), (int) (10 * density));

        card.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{new GlassDrawable(card, 18 * density, density, null, 0.3f).frosted(), GlassPage.veil(18 * density, 0x4D0E1626)}));
        Backdrop.watch(card);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextSize(13.5f);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(font, Typeface.BOLD);
        card.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvVal = new TextView(ctx);
        tvVal.setText(value);
        tvVal.setTextSize(13f);
        tvVal.setTextColor(0xFF64B5FF);
        tvVal.setTypeface(font, Typeface.NORMAL);
        card.addView(tvVal);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * density);
        card.setLayoutParams(lp);
        return card;
    }

    private static void showEditNameDialog(MainActivity activity, float density, Typeface font, EditNameCallback callback) {
        EditText et = new EditText(activity);
        et.setText(ProfileManager.getProfileName(activity));
        et.setSelection(et.getText().length());
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(16);
        et.setTypeface(font, Typeface.NORMAL);
        et.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));

        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF282A33);
        etBg.setCornerRadius(10 * density);
        etBg.setStroke((int) (1 * density), 0xFF4A4E5C);
        et.setBackground(etBg);

        FrameLayout container = new FrameLayout(activity);
        container.setPadding((int) (24 * density), (int) (12 * density), (int) (24 * density), (int) (8 * density));
        container.addView(et);

        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog)
                .setTitle(AppLanguage.strEditName(activity))
                .setView(container)
                .setPositiveButton("Save", (d, which) -> {
                    String newName = et.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        if (callback != null) callback.onSave(newName);
                    }
                })
                .setNegativeButton(AppLanguage.strCancel(activity), (d, which) -> d.dismiss())
                .create();
        dialog.show(); GlassPage.glassDialog(dialog);
    }

    private static void showChangePinDialog(MainActivity activity, float density, Typeface font) {
        ChildModeManager.promptPin(activity, "Confirm Current PIN", "Enter the current 4-digit security PIN", new ChildModeManager.PinCallback() {
            @Override
            public void onSuccess() {
                ChildModeManager.promptPin(activity, "Set New PIN", "Enter a new 4-digit PIN for parental control", new ChildModeManager.PinCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(activity, "Security PIN updated!", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onCancelled() {}
                });
            }
            @Override
            public void onCancelled() {}
        });
    }

    private static void showAppLockPicker(MainActivity activity, float density, Typeface font, boolean isHideMode) {
        PackageManager pm = activity.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        if (apps == null || apps.isEmpty()) {
            mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            apps = pm.queryIntentActivities(mainIntent, 0);
        }

        String selfPkg = activity.getPackageName();
        class AppEntry {
            final String pkg;
            final String label;
            final android.graphics.drawable.Drawable icon;
            AppEntry(String pkg, String label, android.graphics.drawable.Drawable icon) {
                this.pkg = pkg;
                this.label = label;
                this.icon = icon;
            }
        }

        List<AppEntry> entryList = new ArrayList<>();
        if (apps != null) {
            for (ResolveInfo ri : apps) {
                String p = ri.activityInfo.packageName;
                if (p.equals(selfPkg)) continue;
                String lbl = ri.loadLabel(pm).toString();
                entryList.add(new AppEntry(p, lbl, ri.loadIcon(pm)));
            }
        }
        Collections.sort(entryList, (a, b) -> a.label.compareToIgnoreCase(b.label));

        final Set<String> targetSet = isHideMode ? ChildModeManager.getHiddenApps(activity) : ChildModeManager.getLockedApps(activity);

        LinearLayout pickerLayout = new LinearLayout(activity);
        pickerLayout.setOrientation(LinearLayout.VERTICAL);
        pickerLayout.setPadding((int) (22 * density), (int) (18 * density), (int) (22 * density), (int) (16 * density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E2026);
        bg.setCornerRadius(20 * density);
        bg.setStroke((int) (1.5f * density), 0xFF2F323B);
        pickerLayout.setBackground(bg);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(isHideMode ? "Hide Apps from Home Screen" : "Lock Apps with 4-Digit PIN");
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(font, Typeface.BOLD);
        pickerLayout.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(isHideMode ? "Marked apps will not appear on the home screen." : "Selected apps need the PIN to open.");
        tvSub.setTextSize(12);
        tvSub.setTextColor(0xB3FFFFFF);
        tvSub.setTypeface(font, Typeface.NORMAL);
        tvSub.setPadding(0, (int) (2 * density), 0, (int) (10 * density));
        pickerLayout.addView(tvSub);

        ScrollView sv = new ScrollView(activity);
        sv.setVerticalScrollBarEnabled(true);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        for (AppEntry app : entryList) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
            row.setFocusable(true);
            row.setClickable(true);

            GradientDrawable rbg = new GradientDrawable();
            rbg.setColor(0xFF242730);
            rbg.setCornerRadius(10 * density);
            row.setBackground(rbg);

            ImageView iv = new ImageView(activity);
            iv.setImageDrawable(app.icon);
            LinearLayout.LayoutParams ivlp = new LinearLayout.LayoutParams((int) (28 * density), (int) (28 * density));
            ivlp.setMarginEnd((int) (10 * density));
            row.addView(iv, ivlp);

            TextView tv = new TextView(activity);
            tv.setText(app.label);
            tv.setTextSize(13.5f);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTypeface(font, Typeface.BOLD);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            CheckBox cb = new CheckBox(activity);
            cb.setChecked(targetSet.contains(app.pkg));
            cb.setFocusable(false);
            cb.setClickable(false);
            row.addView(cb);

            row.setOnFocusChangeListener((v, hasFocus) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(hasFocus ? 0xFF353946 : 0xFF242730);
                g.setCornerRadius(10 * density);
                if (hasFocus) g.setStroke((int) (2f * density), 0xFFFFFFFF);
                v.setBackground(g);
                v.animate().scaleX(hasFocus ? 1.015f : 1.0f).scaleY(hasFocus ? 1.015f : 1.0f).setDuration(120).start();
            });

            row.setOnClickListener(v -> {
                SoundEffects.playSelect();
                boolean nowChecked = !cb.isChecked();
                cb.setChecked(nowChecked);
                if (isHideMode) {
                    ChildModeManager.setAppHidden(activity, app.pkg, nowChecked);
                } else {
                    ChildModeManager.setAppLocked(activity, app.pkg, nowChecked);
                }
            });

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int) (6 * density);
            list.addView(row, rlp);
        }

        sv.addView(list);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (260 * density));
        svLp.bottomMargin = (int) (12 * density);
        pickerLayout.addView(sv, svLp);

        AlertDialog[] pickerHolder = new AlertDialog[1];
        TextView btnDone = new TextView(activity);
        btnDone.setText("Finished");
        btnDone.setTextSize(13.5f);
        btnDone.setTextColor(0xFFFFFFFF);
        btnDone.setTypeface(font, Typeface.BOLD);
        btnDone.setGravity(Gravity.CENTER);
        btnDone.setIncludeFontPadding(false);
        btnDone.setFocusable(true);
        btnDone.setClickable(true);

        int doneRadius = Math.round(12 * density);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF10B981);
        btnBg.setCornerRadius(doneRadius);
        btnDone.setBackground(btnBg);

        btnDone.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(hasFocus ? 0xFF059669 : 0xFF10B981);
            g.setCornerRadius(doneRadius);
            if (hasFocus) g.setStroke((int) (2 * density), 0xFFFFFFFF);
            v.setBackground(g);
        });

        btnDone.setOnClickListener(v -> {
            SoundEffects.playSelect();
            if (pickerHolder[0] != null) pickerHolder[0].dismiss();
            activity.reloadTiles();
        });

        pickerLayout.addView(btnDone, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (40 * density)));

        AlertDialog.Builder pb = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog);
        pb.setView(pickerLayout);
        AlertDialog d = pb.create();
        pickerHolder[0] = d;
        d.show(); GlassPage.glassDialog(d);

        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dw = Math.min((int) (520 * density), (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.85f));
            win.setLayout(dw, ViewGroup.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.CENTER);
        }
    }

    private static void showDefaultLauncherDialog(MainActivity activity, float density, Typeface font, boolean isDefault, String activePkg) {
        if (activity == null || activity.isFinishing()) return;
        SoundEffects.playSelect();

        AlertDialog[] dialogHolder = new AlertDialog[1];

        // Root container with clipChildren disabled
        FrameLayout dialogWrap = new FrameLayout(activity);
        dialogWrap.setClipChildren(false);
        dialogWrap.setClipToPadding(false);

        // Elegant Dark Slate Glass Container
        LinearLayout dialogBox = new LinearLayout(activity);
        dialogBox.setOrientation(LinearLayout.VERTICAL);
        dialogBox.setClipChildren(false);
        dialogBox.setClipToPadding(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF4181B24); // Deep translucent glass
        bg.setCornerRadius(20 * density);
        bg.setStroke(Math.round(1.5f * density), 0x33FFFFFF); // Subtle cyan rim
        dialogBox.setBackground(bg);
        dialogBox.setPadding(Math.round(24 * density), Math.round(20 * density), Math.round(24 * density), Math.round(20 * density));

        // Dialog Title
        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Set Glass TV as Default Home");
        tvTitle.setTextSize(18f);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(font, Typeface.BOLD);
        tvTitle.setIncludeFontPadding(false);
        tvTitle.setPadding(0, 0, 0, Math.round(8 * density));
        dialogBox.addView(tvTitle);

        // Subtitle / Status description
        TextView tvDesc = new TextView(activity);
        String desc = isDefault
                ? "Tahniah! Glass TV Home kini merupakan pelancar lalai utama peranti anda. Setiap kali butang Home ditekan pada alat kawalan jauh, anda akan terus kembali ke Glass TV."
                : "Current launcher: " + activePkg + "\nChoose one of the methods below to make Glass TV your main launcher:";
        tvDesc.setText(desc);
        tvDesc.setTextSize(12.5f);
        tvDesc.setTextColor(isDefault ? 0xFF34D399 : 0xFFA89F91);
        tvDesc.setTypeface(font, Typeface.NORMAL);
        tvDesc.setIncludeFontPadding(false);
        tvDesc.setPadding(0, 0, 0, Math.round(14 * density));
        dialogBox.addView(tvDesc);

        // Scrollable container with plenty of breathing room
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        LinearLayout optionsCol = new LinearLayout(activity);
        optionsCol.setOrientation(LinearLayout.VERTICAL);
        optionsCol.setClipChildren(false);
        optionsCol.setClipToPadding(false);
        optionsCol.setPadding(Math.round(2 * density), Math.round(4 * density), Math.round(2 * density), Math.round(4 * density));

        // Selection Option 1: Open TV Settings
        View optSettings = buildSelectionOption(
                activity, density, font,
                "Open TV Launcher Picker Settings",
                "Try opening Android's menu so you can pick Glass TV as your home app (if your TV allows it)",
                "Open Settings ",
                0xFFFFB347,
                () -> {
                    SoundEffects.playSelect();
                    try {
                        Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    } catch (Exception e1) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(intent);
                            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        } catch (Exception e2) {
                            Toast.makeText(activity, "This firmware has no launcher settings menu.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
        optionsCol.addView(optSettings);

        // Selection Option 2: ADB Command
        View optAdb = buildSelectionOption(
                activity, density, font,
                "Lock Permanently via ADB (Recommended)",
                "Jalankan arahan melalui PC:\nadb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity" +
                (isDefault ? "" : "\nNyahdayakan pelancar lama: adb shell pm disable-user --user 0 " + activePkg),
                "Salin ADB ",
                0xFF34D399,
                () -> {
                    SoundEffects.playSelect();
                    try {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("ADB Command", "adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity"));
                        }
                        Toast.makeText(activity, "Arahan ADB disalin ke papan keratan", Toast.LENGTH_LONG).show();
                    } catch (Exception ignored) {
                        Toast.makeText(activity, "Arahan: adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity", Toast.LENGTH_LONG).show();
                    }
                }
        );
        optionsCol.addView(optAdb);

        // Selection Option 3: Phone App / Installer
        View optPhone = buildSelectionOption(
                activity, density, font,
                "No PC: Use the Phone App (Glass TV Installer)",
                "Install release/glass-tv-installer.apk on a phone on the same Wi-Fi to turn on Home in one click.",
                "Open Guide ",
                0xFFA78BFA,
                () -> {
                    SoundEffects.playSelect();
                    Toast.makeText(activity, "Open glass-tv-installer.apk on your phone on the same Wi-Fi.", Toast.LENGTH_SHORT).show();
                }
        );
        optionsCol.addView(optPhone);

        scroll.addView(optionsCol, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.bottomMargin = Math.round(14 * density);
        dialogBox.addView(scroll, scrollLp);

        // Bottom Action: Tutup Button (styled as uncropped focusable pill TextView)
        TextView btnClose = new TextView(activity);
        btnClose.setText("Close");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setTypeface(font, Typeface.BOLD);
        btnClose.setTextSize(14f);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setIncludeFontPadding(false);
        btnClose.setFocusable(true);
        btnClose.setClickable(true);

        int closeHeight = Math.round(38 * density);
        int closeRadius = Math.round(19 * density);

        GradientDrawable closeIdle = new GradientDrawable();
        closeIdle.setShape(GradientDrawable.RECTANGLE);
        closeIdle.setColor(0xFF2E3440);
        closeIdle.setCornerRadius(closeRadius);
        closeIdle.setStroke(Math.round(1f * density), 0x33FFFFFF);
        btnClose.setBackground(closeIdle);
        btnClose.setPadding(Math.round(24 * density), 0, Math.round(24 * density), 0);

        btnClose.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setCornerRadius(closeRadius);
            if (hasFocus) {
                g.setColor(0xFF434C5E);
                g.setStroke(Math.round(2f * density), 0xFFFFFFFF); // Glowing cyan rim
            } else {
                g.setColor(0xFF2E3440);
                g.setStroke(Math.round(1f * density), 0x33FFFFFF);
            }
            v.setBackground(g);
        });

        btnClose.setOnClickListener(v -> {
            SoundEffects.playSelect();
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
        });

        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, closeHeight);
        dialogBox.addView(btnClose, closeLp);

        dialogWrap.addView(dialogBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        AlertDialog.Builder pb = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog);
        pb.setView(dialogWrap);
        AlertDialog d = pb.create();
        dialogHolder[0] = d;
        d.show(); GlassPage.glassDialog(d);

        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dw = Math.min(Math.round(650 * density), Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.92f));
            int dh = Math.min(Math.round(520 * density), Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.88f));
            win.setLayout(dw, dh);
            win.setGravity(Gravity.CENTER);
        }

        optSettings.requestFocus();
    }

    private static View buildSelectionOption(
            Context ctx,
            float density,
            Typeface font,
            String title,
            String subtitle,
            String actionLabel,
            int actionColor,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(
                Math.round(16 * density),
                Math.round(12 * density),
                Math.round(16 * density),
                Math.round(12 * density)
        );

        int radius = Math.round(14 * density);

        // Idle background
        rowGlass(row, density, 14, false);

        // Text Column (Left)
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextSize(14f);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(font, Typeface.BOLD);
        tvTitle.setIncludeFontPadding(false);
        textCol.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextSize(11.5f);
            tvSub.setTextColor(0xFFA89F91);
            tvSub.setTypeface(font, Typeface.NORMAL);
            tvSub.setIncludeFontPadding(false);
            tvSub.setPadding(0, Math.round(4 * density), Math.round(10 * density), 0);
            textCol.addView(tvSub);
        }
        row.addView(textCol);

        // Action Pill Button (Right) - Uncropped focusable indicator pill!
        TextView btnAction = new TextView(ctx);
        btnAction.setText(actionLabel);
        btnAction.setTextSize(12f);
        btnAction.setTextColor(0xFFFFFFFF);
        btnAction.setTypeface(font, Typeface.BOLD);
        btnAction.setIncludeFontPadding(false);
        btnAction.setGravity(Gravity.CENTER);

        int pillHeight = Math.round(32 * density);
        int pillRadius = Math.round(16 * density);

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(pillRadius);
        pillBg.setColor((actionColor & 0x00FFFFFF) | 0x33000000);
        pillBg.setStroke(Math.round(1f * density), actionColor);
        btnAction.setBackground(pillBg);
        btnAction.setPadding(Math.round(14 * density), 0, Math.round(14 * density), 0);

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, pillHeight);
        pillLp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(btnAction, pillLp);

        // Focus listener on row
        row.setOnFocusChangeListener((v, hasFocus) -> {
            rowGlass(v, density, 14, hasFocus);
            if (hasFocus) {
                btnAction.setTextColor(0xFFFFFFFF);
                GradientDrawable pGlow = new GradientDrawable();
                pGlow.setShape(GradientDrawable.RECTANGLE);
                pGlow.setCornerRadius(pillRadius);
                pGlow.setColor(actionColor);
                pGlow.setStroke(Math.round(1.5f * density), 0xFFFFFFFF);
                btnAction.setBackground(pGlow);
            } else {
                btnAction.setTextColor(0xFFFFFFFF);
                btnAction.setBackground(pillBg);
            }
        });

        row.setOnClickListener(v -> {
            if (action != null) action.run();
        });

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.bottomMargin = Math.round(8 * density);
        row.setLayoutParams(rowLp);

        return row;
    }

    interface EditNameCallback {
        void onSave(String name);
    }

    interface SwitchCallback {
        void onChanged(boolean isChecked);
    }
}
