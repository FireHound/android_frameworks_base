/*
 * Copyright (C) 2014-2017 SlimRoms Project
 * Author: Lars Greiss - email: kufikugel@googlemail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.systemui.slimrecent;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.cards.recyclerview.view.CardRecyclerView;

import com.android.systemui.R;
import com.android.systemui.RecentsComponent;
import com.android.systemui.recents.misc.Utilities;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.statusbar.BaseStatusBar;

import java.util.List;

/**
 * Our main recents controller.
 * Takes care of the toggle, preload, cancelpreload and close requests
 * and passes the requested actions trough to our views and the panel
 * view controller #link:RecentPanelView.
 *
 * As well the in out animation, constructing the main window container
 * and the remove all tasks animation/detection (#link:RecentListOnScaleGestureListener)
 * are handled here.
 */
public class RecentController implements RecentPanelView.OnExitListener,
        RecentPanelView.OnTasksLoadedListener {

    private static final String TAG = "SlimRecentsController";
    private static final boolean DEBUG = false;

    // Animation control values.
    private static final int ANIMATION_STATE_NONE = 0;
    private static final int ANIMATION_STATE_OUT  = 1;

    // Animation state.
    private int mAnimationState = ANIMATION_STATE_NONE;

    public static float DEFAULT_SCALE_FACTOR = 1.0f;

    private Context mContext;
    private WindowManager mWindowManager;
    private IWindowManager mWindowManagerService;

    private boolean mIsShowing;
    private boolean mIsToggled;
    private boolean mIsPreloaded;

    protected long mLastToggleTime;

    // The different views we need.
    private ViewGroup mParentView;
    private ViewGroup mRecentContainer;
    private View mKeyguardView;
    private LinearLayout mRecentContent;
    private LinearLayout mRecentWarningContent;
    private ImageView mEmptyRecentView;
    private ImageView mKeyguardImage;
    private TextView mKeyguardText;

    private int mLayoutDirection;
    private int mMainGravity;
    private int mUserGravity;
    private int mPanelColor;

    private float mScaleFactor = DEFAULT_SCALE_FACTOR;

    // Main panel view.
    private RecentPanelView mRecentPanelView;

    private Handler mHandler = new Handler();

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.v(TAG, "onReceive: " + intent);
            final String action = intent.getAction();
            // Screen goes off or system dialogs should close.
            // Get rid of our recents screen
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra("reason");
                if (reason != null &&
                        !reason.equals(BaseStatusBar.SYSTEM_DIALOG_REASON_RECENT_APPS)) {
                    hideRecents(false);
                }
                if (DEBUG) Log.d(TAG, "braodcast system dialog");
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)){
                hideRecents(true);
                if (DEBUG) Log.d(TAG, "broadcast screen off");
            }
        }
    };

    public RecentController(Context context, int layoutDirection) {
        mContext = context;
        mLayoutDirection = layoutDirection;

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();

        /**
         * Add intent actions to listen on it.
         * Screen off to get rid of recents,
         * same if close system dialogs is requested.
         */
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        mParentView = new FrameLayout(mContext);

        // Inflate our recents layout
        mRecentContainer =
                (RelativeLayout) View.inflate(context, R.layout.slim_recent, null);

        // Get contents for rebuilding and gesture detector.
        mRecentContent =
                (LinearLayout) mRecentContainer.findViewById(R.id.recent_content);

        mRecentWarningContent =
                (LinearLayout) mRecentContainer.findViewById(R.id.recent_warning_content);

        final CardRecyclerView cardRecyclerView =
                (CardRecyclerView) mRecentContainer.findViewById(R.id.recent_list);

        cardRecyclerView.setHasFixedSize(true);
        CacheMoreCardsLayoutManager llm = new CacheMoreCardsLayoutManager(context, mWindowManager);
        llm.setReverseLayout(true);
        cardRecyclerView.setLayoutManager(llm);

        mEmptyRecentView =
                (ImageView) mRecentContainer.findViewById(R.id.empty_recent);

        mKeyguardView = View.inflate(context, R.layout.slim_recent_keyguard, null);

        mKeyguardImage =
                (ImageView) mKeyguardView.findViewById(R.id.keyguard_recent_img);

        mKeyguardText = (TextView) mKeyguardView.findViewById(R.id.keyguard_recent_text);

        // Prepare gesture detector.
        final ScaleGestureDetector recentListGestureDetector =
                new ScaleGestureDetector(mContext,
                        new RecentListOnScaleGestureListener(
                                mRecentWarningContent, cardRecyclerView));

        // Prepare recents panel view and set the listeners
        cardRecyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                recentListGestureDetector.onTouchEvent(event);
                return false;
            }
        });

        mRecentPanelView = new RecentPanelView(mContext, this, cardRecyclerView, mEmptyRecentView);
        mRecentPanelView.setOnExitListener(this);
        mRecentPanelView.setOnTasksLoadedListener(this);

        // Add finally the views and listen for outside touches.
        mParentView.setFocusableInTouchMode(true);
        mParentView.addView(mRecentContainer);
        mParentView.addView(mKeyguardView);
        mParentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    // Touch outside the recents window....hide recents window.
                    onExit();
                    return true;
                }
                return false;
            }
        });
        // Listen for back key events to close recents screen.
        mParentView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                    && event.getAction() == KeyEvent.ACTION_UP
                    && !event.isCanceled()) {
                    // Back key was pressed....hide recents window.
                    onExit();
                    return true;
                }
                return false;
            }
        });

        // Settings observer
        SettingsObserver observer = new SettingsObserver(mHandler);
        observer.observe();
    }

    /**
     * External call from theme engines to apply
     * new styles.
     */
    public void rebuildRecentsScreen() {
        // Set new layout parameters and backgrounds.
        if (mRecentContainer != null) {
            final ViewGroup.LayoutParams layoutParams = mRecentContainer.getLayoutParams();
            layoutParams.width = (int) (mContext.getResources()
                    .getDimensionPixelSize(R.dimen.recent_width) * mScaleFactor);
            mRecentContainer.setLayoutParams(layoutParams);

            setGravityAndImageResources();
        }
        // Rebuild complete adapter and lists to force style updates.
        if (mRecentPanelView != null) {
            mRecentPanelView.buildCardListAndAdapter();
        }
    }

    /**
     * Calculate main gravity based on layout direction and user gravity value.
     * Set and update all resources and notify the different layouts about the change.
     */
    private void setGravityAndImageResources() {
        // Calculate and set gravitiy.
        if (mLayoutDirection == View.LAYOUT_DIRECTION_RTL) {
            if (mUserGravity == Gravity.LEFT) {
                mMainGravity = Gravity.RIGHT;
            } else {
                mMainGravity = Gravity.LEFT;
            }
        } else {
            mMainGravity = mUserGravity;
        }

        // Set layout direction.
        mRecentContainer.setLayoutDirection(mLayoutDirection);

        // Reset all backgrounds.
        mRecentContent.setBackgroundResource(0);
        mRecentWarningContent.setBackgroundResource(0);
        mEmptyRecentView.setImageResource(0);

        // Set correct backgrounds based on calculated main gravity.
        int warningColor = mContext.getResources().getColor(R.color.recent_warning_background);
        mRecentWarningContent.setBackgroundColor(warningColor);

        int tintColor = getEmptyRecentColor();
        int backgroundColor = mPanelColor;
        if (backgroundColor == 0x00ffffff) {
            backgroundColor = mContext.getResources().getColor(R.color.recent_background);
        }

        VectorDrawable vd = (VectorDrawable)
                mContext.getResources().getDrawable(R.drawable.ic_empty_recent);
        vd.setTint(tintColor);
        mEmptyRecentView.setImageDrawable(vd);

        VectorDrawable kgvd = (VectorDrawable)
                mContext.getResources().getDrawable(R.drawable.ic_recent_keyguard);
        kgvd.setTint(tintColor);
        mKeyguardImage.setImageDrawable(kgvd);
        mKeyguardText.setTextColor(tintColor);

        int padding = mContext.getResources().getDimensionPixelSize(R.dimen.slim_recents_elevation);
        if (mMainGravity == Gravity.LEFT) {
            mRecentContainer.setPadding(0, 0, padding, 0);
            mEmptyRecentView.setRotation(180);
        } else {
            mRecentContainer.setPadding(padding, 0, 0, 0);
            mEmptyRecentView.setRotation(0);
        }

        // Notify panel view about new main gravity.
        if (mRecentPanelView != null) {
            mRecentPanelView.setMainGravity(mMainGravity);
        }

        // Set custom background color (or reset to default, as the case may be
        if (mRecentContent != null) {
            mRecentContent.setElevation(50);
            mRecentContent.setBackgroundColor(backgroundColor);
        }

        if (mKeyguardView != null) {
            mKeyguardView.setBackgroundColor(backgroundColor);
        }
    }

    private int getEmptyRecentColor() {
        if (Utilities.computeContrastBetweenColors(mPanelColor,
                Color.WHITE) < 3f) {
            return mContext.getResources().getColor(
                    R.color.recents_empty_dark_color);
        } else {
            return mContext.getResources().getColor(
                    R.color.recents_empty_light_color);
        }
    }

    /**
     * External call. Toggle recents panel.
     */
    public void toggleRecents(Display display, int layoutDirection, View statusBarView) {
        if (DEBUG) Log.d(TAG, "toggle recents panel");
        if (mLayoutDirection != layoutDirection) {
            mLayoutDirection = layoutDirection;
            setGravityAndImageResources();
        }

        if (mAnimationState == ANIMATION_STATE_NONE) {
            if (!isShowing()) {
                mIsToggled = true;
                if (mRecentPanelView.isTasksLoaded()) {
                    if (DEBUG) Log.d(TAG, "tasks loaded - showRecents()");
                    showRecents();
                } else if (!mIsPreloaded) {
                    // This should never happen due that preload should
                    // always be done if someone calls recents. Well a lot
                    // 3rd party apps forget the preload step. So we do it now.
                    // Due that mIsToggled is true preloader will open the recent
                    // screen as soon the preload is finished and the listener
                    // notifies us that we are ready.
                    if (DEBUG) Log.d(TAG, "preload was not called - do it now");
                    preloadRecentTasksList();
                }
            } else {
                openLastAppPanelToggle();
                hideRecents(false);
            }
        }
    }

    /**
     * External call. Preload recent tasks.
     */
    public void preloadRecentTasksList() {
        if (mRecentPanelView != null) {
            if (DEBUG) Log.d(TAG, "preloading recents");
            mIsPreloaded = true;
            setSystemUiVisibilityFlags();
            mRecentPanelView.setCancelledByUser(false);
            mRecentPanelView.loadTasks();
        }
    }

    /**
     * External call. Cancel preload recent tasks.
     */
    public void cancelPreloadingRecentTasksList() {
        if (mRecentPanelView != null && !isShowing()) {
            if (DEBUG) Log.d(TAG, "cancel preloading recents");
            mIsPreloaded = false;
            mRecentPanelView.setCancelledByUser(true);
        }
    }

    public void closeRecents() {
        if (DEBUG) Log.d(TAG, "closing recents panel");
        hideRecents(false);
    }

    /**
     * Get LayoutParams we need for the recents panel.
     *
     * @return LayoutParams
     */
    private WindowManager.LayoutParams generateLayoutParameter() {
        final int width = (int) (mContext.getResources()
                .getDimensionPixelSize(R.dimen.recent_width) * mScaleFactor);
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SLIM_RECENTS,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        // Turn on hardware acceleration for high end gfx devices.
        if (ActivityManager.isHighEndGfx()) {
            params.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            params.privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED;
        }

        // Set gravitiy.
        params.gravity = Gravity.CENTER_VERTICAL | mMainGravity;

        // Set animation for our recent window.
        if (mMainGravity == Gravity.LEFT) {
            params.windowAnimations =
                    com.android.internal.R.style.Animation_RecentScreen_Left;
        } else {
            params.windowAnimations = com.android.internal.R.style.Animation_RecentScreen;
        }

        // This title is for debugging only. See: dumpsys window
        params.setTitle("RecentControlPanel");
        return params;
    }

    /**
     * For smooth user experience we attach the same systemui visbility
     * flags the current app, where the user is on, has set.
     */
    private void setSystemUiVisibilityFlags() {
        int vis = 0;
        boolean layoutBehindNavigation = true;
        int newVis = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if ((vis & View.STATUS_BAR_TRANSLUCENT) != 0) {
            newVis |= View.STATUS_BAR_TRANSLUCENT
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        }
        if ((vis & View.NAVIGATION_BAR_TRANSLUCENT) != 0) {
            newVis |= View.NAVIGATION_BAR_TRANSLUCENT;
        }
        if ((vis & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0) {
            newVis |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            layoutBehindNavigation = false;
        }
        if ((vis & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
            newVis |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        if ((vis & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0) {
            newVis |= View.SYSTEM_UI_FLAG_IMMERSIVE;
            layoutBehindNavigation = false;
        }
        if ((vis & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0) {
            newVis |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            layoutBehindNavigation = false;
        }
        if (layoutBehindNavigation) {
            newVis |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        mParentView.setSystemUiVisibility(newVis);
    }

    // Returns if panel is currently showing.
    public boolean isShowing() {
        return mIsShowing;
    }

    // Hide the recent window.
    public boolean hideRecents(boolean forceHide) {
        if (isShowing()) {
            mIsPreloaded = false;
            mIsToggled = false;
            mIsShowing = false;
            mRecentPanelView.setTasksLoaded(false);
            mRecentPanelView.dismissPopup();
            if (forceHide) {
                if (DEBUG) Log.d(TAG, "force hide recent window");
                CacheController.getInstance(mContext).setRecentScreenShowing(false);
                mAnimationState = ANIMATION_STATE_NONE;
                mHandler.removeCallbacks(mRecentRunnable);
                mWindowManager.removeViewImmediate(mParentView);
                return true;
            } else if (mAnimationState != ANIMATION_STATE_OUT) {
                if (DEBUG) Log.d(TAG, "out animation starting");
                mAnimationState = ANIMATION_STATE_OUT;
                mHandler.removeCallbacks(mRecentRunnable);
                mHandler.postDelayed(mRecentRunnable, mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_recentDefaultDur));
                mWindowManager.removeView(mParentView);
                return true;
            }
        }
        return false;
    }

    // Show the recent window.
    private void showRecents() {
        if (DEBUG) Log.d(TAG, "in animation starting");
        mIsShowing = true;
        sendCloseSystemWindows(BaseStatusBar.SYSTEM_DIALOG_REASON_RECENT_APPS);
        mAnimationState = ANIMATION_STATE_NONE;
        mHandler.removeCallbacks(mRecentRunnable);
        CacheController.getInstance(mContext).setRecentScreenShowing(true);
        mWindowManager.addView(mParentView, generateLayoutParameter());
        mRecentPanelView.scrollToFirst();

        KeyguardManager km =
                (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode()) {
            mRecentContainer.setVisibility(View.GONE);
            mKeyguardView.setVisibility(View.VISIBLE);
        } else {
            mRecentContainer.setVisibility(View.VISIBLE);
            mKeyguardView.setVisibility(View.GONE);
        }
    }

    public static void sendCloseSystemWindows(String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    // Listener callback.
    @Override
    public void onExit() {
        hideRecents(false);
    }

    // Listener callback.
    @Override
    public void onTasksLoaded() {
        if (mIsToggled && !isShowing()) {
            if (DEBUG) Log.d(TAG, "onTasksLoaded....showRecents()");
            showRecents();
        }
    }

    /**
     * Runnable if recent panel closed to notify the cache controller about the state.
     */
    private final Runnable mRecentRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAnimationState == ANIMATION_STATE_OUT) {
                if (DEBUG) Log.d(TAG, "out animation finished");
                CacheController.getInstance(mContext).setRecentScreenShowing(false);
            }
            mAnimationState = ANIMATION_STATE_NONE;
        }
    };

    /**
     * Settingsobserver to take care of the user settings.
     * Either gravity or scale factor of our recent panel can change.
     */
    private class SettingsObserver extends UserContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_PANEL_GRAVITY),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_PANEL_SCALE_FACTOR),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_PANEL_EXPANDED_MODE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_PANEL_SHOW_TOPMOST),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_PANEL_BG_COLOR),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_SHOW_RUNNING_TASKS),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        @Override
        protected void update() {
            // Close recent panel if it is opened.
            hideRecents(false);

            ContentResolver resolver = mContext.getContentResolver();

            // Get user gravity.
            mUserGravity = Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_PANEL_GRAVITY, Gravity.RIGHT,
                    UserHandle.USER_CURRENT);

            // Update colors in RecentPanelView
            mPanelColor = Settings.System.getIntForUser(resolver,
                    Settings.System.RECENT_PANEL_BG_COLOR, 0x763367d6, UserHandle.USER_CURRENT);

            // Set main gravity and background images.
            setGravityAndImageResources();

            // Get user scale factor.
            float scaleFactor = Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_PANEL_SCALE_FACTOR, 100,
                    UserHandle.USER_CURRENT) / 100.0f;

            // If changed set new scalefactor, rebuild the recent panel
            // and notify RecentPanelView about new value.
            if (scaleFactor != mScaleFactor) {
                mScaleFactor = scaleFactor;
                rebuildRecentsScreen();
            }
            if (mRecentPanelView != null) {
                mRecentPanelView.setScaleFactor(mScaleFactor);
                mRecentPanelView.setExpandedMode(Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_PANEL_EXPANDED_MODE,
                    mRecentPanelView.EXPANDED_MODE_AUTO,
                    UserHandle.USER_CURRENT));
                mRecentPanelView.setShowTopTask(Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_PANEL_SHOW_TOPMOST, 0,
                    UserHandle.USER_CURRENT) == 1);
                mRecentPanelView.setShowOnlyRunningTasks(Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_SHOW_RUNNING_TASKS, 0,
                    UserHandle.USER_CURRENT) == 1);
            }

            mRecentContent.setElevation(50);

            int backgroundColor = mPanelColor;
            if (backgroundColor == 0x00ffffff) {
                backgroundColor = mContext.getResources().getColor(R.color.recent_background);
            }
            mRecentContent.setBackgroundColor(backgroundColor);
            mKeyguardView.setBackgroundColor(backgroundColor);
        }
    }

    /**
     * Extended SimpleOnScaleGestureListener to take
     * care of a pinch to zoom out gesture. This class
     * takes as well care on a bunch of animations which are needed
     * to control the final action.
     */
    private class RecentListOnScaleGestureListener extends SimpleOnScaleGestureListener {

        // Constants for scaling max/min values.
        private final static float MAX_SCALING_FACTOR       = 1.0f;
        private final static float MIN_SCALING_FACTOR       = 0.5f;
        private final static float MIN_ALPHA_SCALING_FACTOR = 0.55f;

        private final static int ANIMATION_FADE_IN_DURATION  = 400;
        private final static int ANIMATION_FADE_OUT_DURATION = 300;

        private float mScalingFactor = MAX_SCALING_FACTOR;
        private boolean mActionDetected;

        // Views we need and are passed trough the constructor.
        private LinearLayout mRecentWarningContent;
        private CardRecyclerView mCardRecyclerView;

        RecentListOnScaleGestureListener(
                LinearLayout recentWarningContent, CardRecyclerView cardRecyclerView) {
            mRecentWarningContent = recentWarningContent;
            mCardRecyclerView = cardRecyclerView;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // Get gesture scaling factor and calculate the values we need.
            mScalingFactor *= detector.getScaleFactor();
            mScalingFactor = Math.max(MIN_SCALING_FACTOR,
                    Math.min(mScalingFactor, MAX_SCALING_FACTOR));
            final float alphaValue = Math.max(MIN_ALPHA_SCALING_FACTOR,
                    Math.min(mScalingFactor, MAX_SCALING_FACTOR));

            // Reset detection value.
            mActionDetected = false;

            // Set alpha value for content.
            mRecentContent.setAlpha(alphaValue);

            // Check if we are under MIN_ALPHA_SCALING_FACTOR and show
            // warning view.
            if (mScalingFactor < MIN_ALPHA_SCALING_FACTOR) {
                mActionDetected = true;
                mRecentWarningContent.setVisibility(View.VISIBLE);
            } else {
                mRecentWarningContent.setVisibility(View.GONE);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return mRecentPanelView.hasClearableTasks();
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            super.onScaleEnd(detector);
            // Reset to default scaling factor to prepare for next gesture.
            mScalingFactor = MAX_SCALING_FACTOR;

            final float currentAlpha = mRecentContent.getAlpha();

            // Gesture was detected and activated. Prepare and play the animations.
            if (mActionDetected) {
                final boolean hasFavorite = mRecentPanelView.hasFavorite();

                // Setup animation for warning content - fade out.
                ValueAnimator animation1 = ValueAnimator.ofFloat(0.7f, 0.0f);
                animation1.setDuration(ANIMATION_FADE_OUT_DURATION);
                animation1.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mRecentWarningContent.setAlpha((Float) animation.getAnimatedValue());
                    }
                });

                // Setup animation for list view - fade out.
                ValueAnimator animation2 = ValueAnimator.ofFloat(1.0f, 0.0f);
                animation2.setDuration(ANIMATION_FADE_OUT_DURATION);
                animation2.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mCardRecyclerView.setAlpha((Float) animation.getAnimatedValue());
                    }
                });

                // Setup animation for base content - fade in.
                ValueAnimator animation3 = ValueAnimator.ofFloat(currentAlpha, 1.0f);
                animation3.setDuration(ANIMATION_FADE_IN_DURATION);
                animation3.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mRecentContent.setAlpha((Float) animation.getAnimatedValue());
                    }
                });

                // Setup animation for empty recent image - fade in.
                if (!hasFavorite) {
                    mEmptyRecentView.setAlpha(0.0f);
                    mEmptyRecentView.setVisibility(View.VISIBLE);
                }
                ValueAnimator animation4 = ValueAnimator.ofFloat(0.0f, 1.0f);
                animation4.setDuration(ANIMATION_FADE_IN_DURATION);
                animation4.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mEmptyRecentView.setAlpha((Float) animation.getAnimatedValue());
                    }
                });

                // Start all ValueAnimator animations
                // and listen onAnimationEnd to prepare the views for the next call.
                AnimatorSet animationSet = new AnimatorSet();
                if (hasFavorite) {
                    animationSet.playTogether(animation1, animation3);
                } else {
                    animationSet.playTogether(animation1, animation2, animation3, animation4);
                }
                animationSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Animation is finished. Prepare warning content for next call.
                        mRecentWarningContent.setVisibility(View.GONE);
                        mRecentWarningContent.setAlpha(0.7f);
                        // Remove all tasks now.
                        if (mRecentPanelView.removeAllApplications()) {
                            // Prepare listview for next recent call.
                            mCardRecyclerView.setVisibility(View.GONE);
                            mCardRecyclerView.setAlpha(1.0f);
                            // Finally hide our recents screen.
                            hideRecents(false);
                        }
                    }
                });
                animationSet.start();

            } else if (currentAlpha < 1.0f) {
                // No gesture action was detected. But we may have a lower alpha
                // value for the content. Animate back to full opacitiy.
                ValueAnimator animation = ValueAnimator.ofFloat(currentAlpha, 1.0f);
                animation.setDuration(100);
                animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mRecentContent.setAlpha((Float) animation.getAnimatedValue());
                    }
                });
                animation.start();
            }
        }
   }

    public void startMultiWin() {
        SystemServicesProxy ssp = SystemServicesProxy.getInstance(mContext);
        ActivityManager.RunningTaskInfo runningTask = ssp.getRunningTask();
        int createMode = ActivityManager.DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT;
        if (ssp.startTaskInDockedMode(runningTask.id, createMode)) {
            openLastApptoBottom();
            if (!isShowing()) {
                showRecents();
            }
        }
   }

    public void openLastApptoBottom() {
        final ActivityManager am =
                (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.RunningTaskInfo lastTask = getLastTask(am);

        if (lastTask != null) {
            am.moveTaskToFront(lastTask.id, ActivityManager.MOVE_TASK_NO_USER_ACTION);
        }
    }

    public void openOnDraggedApptoOtherSide(int taskid) {
        final ActivityManager am =
                (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

        am.moveTaskToFront(taskid, ActivityManager.MOVE_TASK_NO_USER_ACTION);
    }

    public void openLastAppPanelToggle() {
        final ActivityOptions animations = ActivityOptions.makeCustomAnimation(mContext,
                mMainGravity == Gravity.LEFT ? com.android.internal.R.anim.recent_screen_enter_left :
                com.android.internal.R.anim.recent_screen_enter,
                com.android.internal.R.anim.recent_screen_fade_out);
        final ActivityManager am =
                (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.RunningTaskInfo lastTask = getLastTask(am);

        if (lastTask != null) {
            am.moveTaskToFront(lastTask.id, ActivityManager.MOVE_TASK_NO_USER_ACTION, animations.toBundle());
        }
    }

    private ActivityManager.RunningTaskInfo getLastTask(final ActivityManager am) {
        final String defaultHomePackage = resolveCurrentLauncherPackage();
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);

        for (int i = 1; i < tasks.size(); i++) {
            String packageName = tasks.get(i).topActivity.getPackageName();
            if (!packageName.equals(defaultHomePackage)
                    && !packageName.equals(mContext.getPackageName())
                    && !packageName.equals("com.android.systemui")) {
                return tasks.get(i);
            }
        }
        return null;
    }

    private String resolveCurrentLauncherPackage() {
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        final PackageManager pm = mContext.getPackageManager();
        final ResolveInfo launcherInfo = pm.resolveActivity(launcherIntent, 0);
        return launcherInfo.activityInfo.packageName;
    }

    private class CacheMoreCardsLayoutManager extends LinearLayoutManager {
        private Context context;
        private WindowManager mWindowManager;

        public CacheMoreCardsLayoutManager(Context context, WindowManager windowManager) {
            super(context);
            this.context = context;
            this.mWindowManager = windowManager;
        }

        @Override
        protected int getExtraLayoutSpace(RecyclerView.State state) {
            return getScreenHeight();
        }

        private int getScreenHeight() {
            Display display = mWindowManager.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int screenHeight = size.y;
            return screenHeight;
        }
    }

}
