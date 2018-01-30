package com.android.systemui.statusbar.phone;

import com.android.systemui.FontSizeUtils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.Clock;

/**
 * To control your...clock
 */
public class ClockController {

    public static final int STYLE_HIDE_CLOCK    = 0;
    public static final int STYLE_CLOCK_RIGHT   = 1;
    public static final int STYLE_CLOCK_CENTER  = 2;
    public static final int STYLE_CLOCK_LEFT    = 3;

    public static final int DEFAULT_ICON_TINT = Color.WHITE;

    private final Context mContext;
    private final SettingsObserver mSettingsObserver;
    private Clock mRightClock, mCenterClock, mLeftClock, mActiveClock;

    private int mClockLocation;
    private int mAmPmStyle;
    private int mClockDateStyle;
    private int mClockDateDisplay;
    private int mIconTint = DEFAULT_ICON_TINT;
    private final Rect mTintArea = new Rect();

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_AM_PM),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_DISPLAY),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT),
                    false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public ClockController(View statusBar, Handler handler) {
        mRightClock = (Clock) statusBar.findViewById(R.id.clock);
        mCenterClock = (Clock) statusBar.findViewById(R.id.center_clock);
        mCenterClock.setVisibility(View.GONE);
        mContext = statusBar.getContext();

        mActiveClock = mRightClock;
        mSettingsObserver = new SettingsObserver(handler);
        mSettingsObserver.observe();

        updateSettings();
    }

    private Clock getClockForCurrentLocation() {
        Clock clockForAlignment;
        switch (mClockLocation) {
            case STYLE_CLOCK_CENTER:
                clockForAlignment = mCenterClock;
                mCenterClock.setVisibility(View.GONE);
                break;
            case STYLE_CLOCK_RIGHT:
            case STYLE_HIDE_CLOCK:
            default:
                clockForAlignment = mRightClock;
                mCenterClock.setVisibility(View.GONE);
                break;
        }
        return clockForAlignment;
    }

    private void updateActiveClock() {
        mActiveClock.setVisibility(View.GONE);
        if (mClockLocation == STYLE_HIDE_CLOCK) {
            return;
        }

        mActiveClock = getClockForCurrentLocation();
        mActiveClock.setVisibility(View.VISIBLE);
        mActiveClock.setAmPmStyle(mAmPmStyle);
        mActiveClock.setClockDateDisplay(mClockDateDisplay);
        mActiveClock.setClockDateStyle(mClockDateStyle);
        setTextColor(mIconTint);
        updateFontSize();
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mAmPmStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_AM_PM, Clock.AM_PM_STYLE_GONE,
                UserHandle.USER_CURRENT);
        mClockLocation = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CLOCK, STYLE_CLOCK_RIGHT,
                UserHandle.USER_CURRENT);
        mClockDateDisplay = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CLOCK_DATE_DISPLAY, Clock.CLOCK_DATE_DISPLAY_GONE,
                UserHandle.USER_CURRENT);
        mClockDateStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CLOCK_DATE_STYLE, Clock.CLOCK_DATE_STYLE_REGULAR,
                UserHandle.USER_CURRENT);
        updateActiveClock();
    }

    public void setVisibility(boolean visible) {
        if (mActiveClock != null) {
            mActiveClock.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setTintArea(Rect tintArea) {
        if (tintArea == null) {
            mTintArea.setEmpty();
        } else {
            mTintArea.set(tintArea);
        }
        applyClockTint();
    }

    public void setTextColor(int iconTint) {
        mIconTint = iconTint;
        if (mActiveClock != null) {
            mActiveClock.setTextColor(iconTint);
        }
        applyClockTint();
    }

    public void updateFontSize() {
        if (mActiveClock != null) {
            FontSizeUtils.updateFontSize(mActiveClock, R.dimen.status_bar_clock_size);
            mActiveClock.setPaddingRelative(
                    mContext.getResources().getDimensionPixelSize(
                            R.dimen.status_bar_clock_starting_padding),
                    0,
                    mContext.getResources().getDimensionPixelSize(
                        R.dimen.status_bar_clock_end_padding),
                    0);
        }
    }

    private void applyClockTint() {
        //StatusBarIconController.getTint(mTintArea, mActiveClock, mIconTint);
    }
}

