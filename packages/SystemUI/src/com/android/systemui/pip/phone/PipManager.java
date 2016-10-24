/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.pip.phone;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Context;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

/**
 * Manages the picture-in-picture (PIP) UI and states for Phones.
 */
public class PipManager {
    private static final String TAG = "PipManager";

    private static PipManager sPipController;

    private Context mContext;
    private IActivityManager mActivityManager;
    private IWindowManager mWindowManager;

    private PipTouchHandler mTouchHandler;

    private PipManager() {}

    /**
     * Initializes {@link PipManager}.
     */
    public void initialize(Context context) {
        mContext = context;
        mActivityManager = ActivityManagerNative.getDefault();
        mWindowManager = WindowManagerGlobal.getWindowManagerService();

        mTouchHandler = new PipTouchHandler(context, mActivityManager, mWindowManager);
    }

    /**
     * Updates the PIP per configuration changed.
     */
    public void onConfigurationChanged() {}

    /**
     * Gets an instance of {@link PipManager}.
     */
    public static PipManager getInstance() {
        if (sPipController == null) {
            sPipController = new PipManager();
        }
        return sPipController;
    }
}
