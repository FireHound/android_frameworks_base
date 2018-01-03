/*
 * Copyright (C) 2017 FireHound ROMs
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

/** Quick settings tile: Greenify **/
public class GreenifyTile extends QSTileImpl<BooleanState> {
       private static final String PACKAGE_NAME = "com.oasisfeng.greenify";
        private static final Intent GREENIFY = new Intent().setComponent(new ComponentName(
            PACKAGE_NAME, "com.oasisfeng.greenify.GreenifyActivity"));
        private static final Intent HIBERNATE = new Intent().setComponent(new ComponentName(
            PACKAGE_NAME, "com.oasisfeng.greenify.GreenifyShortcut"));
        private boolean mListening;

    public GreenifyTile(QSHost host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.FH_SETTINGS;
    }

    @Override
    protected void handleClick() {
        mHost.collapsePanels();

        if (!isGreenifyInstalled()) {
            showNotInstalled();
            return;
        }
        hibernateApps();
        showHibernateToast();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
        mHost.collapsePanels();
        if (!isGreenifyInstalled()) {
            showNotInstalled();
            return;
        }
        launchGreenify();
        refreshState();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_greenify);
    }

    private boolean isGreenifyInstalled(){
      boolean isGreenifyInstalled = false;
      try {
        isGreenifyInstalled = (mContext.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionCode > 0);
      } catch (PackageManager.NameNotFoundException e) {
      }
      return isGreenifyInstalled;
    }

    private void showNotInstalled(){
      SysUIToast.makeText(mContext, mContext.getString(
            R.string.greenify_not_installed),
            Toast.LENGTH_SHORT).show();
    }

    private void showHibernateToast(){
      SysUIToast.makeText(mContext, mContext.getString(
            R.string.greenified_apps),
            Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean isAvailable(){
      return isGreenifyInstalled();
    }

    protected void hibernateApps() {
        mHost.startActivityDismissingKeyguard(HIBERNATE);
    }

    protected void launchGreenify() {
        mHost.startActivityDismissingKeyguard(GREENIFY);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_greenify_tile);
        state.label = mContext.getString(R.string.quick_settings_greenify);
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
