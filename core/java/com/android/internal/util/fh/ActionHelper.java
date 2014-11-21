/*
* Copyright (C) 2014 SlimRoms Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.internal.util.fh;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.os.UserHandle;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class ActionHelper {

    private static final String SYSTEMUI_METADATA_NAME = "com.android.systemui";


     // get and set the lockcreen shortcut configs from provider and return propper arraylist objects
     // @ActionConfig
     public static ArrayList<ActionConfig> getLockscreenShortcutConfig(Context context) {
         String config = Settings.System.getStringForUser(
                     context.getContentResolver(),
                     Settings.System.LOCKSCREEN_SHORTCUTS,
                     UserHandle.USER_CURRENT);
         if (config == null) {
             config = "";
         }

         return (ConfigSplitHelper.getActionConfigValues(context, config, null, null, true));
     }

     public static void setLockscreenShortcutConfig(Context context,
             ArrayList<ActionConfig> actionConfig, boolean reset) {
         String config;
         if (reset) {
             config = "";
         } else {
             config = ConfigSplitHelper.setActionConfig(actionConfig, true);
         }
         Settings.System.putString(context.getContentResolver(),
                     Settings.System.LOCKSCREEN_SHORTCUTS, config);
     }
}
