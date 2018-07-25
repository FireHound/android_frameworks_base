/*
 * Copyright (C) 2018 FireHound
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.SystemSetting;
import com.android.systemui.qs.tileimpl.QSTileImpl;

/** Quick settings tile: Gaming Mode tile **/
public class GamingModeTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_gaming_mode);
    private final SystemSetting mSetting;
    private final AudioManager mAudioManager;

    public GamingModeTile(QSHost host) {
        super(host);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mSetting = new SystemSetting(mContext, mHandler, System.ENABLE_GAMING_MODE) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        handleState(!mState.value);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    private void handleState(boolean enabled) {
        boolean headsUpEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1) == 1;
        if (enabled && headsUpEnabled) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 0);
        } else if (!enabled) {
            Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1);
        }
        boolean isHwKeysOn = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HARDWARE_KEYS_DISABLE, 0) == 1;
        if (enabled && isHwKeysOn) {
            Settings.Secure.putIntForUser(mContext.getContentResolver(),
                    Settings.Secure.HARDWARE_KEYS_DISABLE, 1, UserHandle.USER_CURRENT);
        } else if (!enabled) {
            Settings.Secure.putIntForUser(mContext.getContentResolver(),
                    Settings.Secure.HARDWARE_KEYS_DISABLE, 0, UserHandle.USER_CURRENT);
        }
        if (enabled) {
            updateSounds();
        } else if (!enabled) {
            return;
        }
        if (enabled) {
            SysUIToast.makeText(mContext, mContext.getString(
                R.string.gaming_mode_tile_toast),
                Toast.LENGTH_SHORT).show();
        }

        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.ENABLE_GAMING_MODE,
                enabled ? 1 : 0, UserHandle.USER_CURRENT);
    }

    private void updateSounds() {
        int oldState = mAudioManager.getRingerModeInternal();
        int newState = oldState;
        switch (oldState) {
            case AudioManager.RINGER_MODE_NORMAL:
                newState = AudioManager.RINGER_MODE_VIBRATE;
                mAudioManager.setRingerModeInternal(newState);
                break;
            case AudioManager.RINGER_MODE_SILENT:
                newState = AudioManager.RINGER_MODE_VIBRATE;
                mAudioManager.setRingerModeInternal(newState);
                break;
            default:
                break;
        }
        int mediaVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mediaVolume, 100);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        final boolean enable = value != 0;
        if (state.slash == null) {
            state.slash = new SlashState();
        }
        state.icon = mIcon;
        state.label = mContext.getString(R.string.gaming_mode_tile_title);
        state.slash.isSlashed = !state.value;
        state.value = enable;
        if (enable) {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.gaming_mode_tile_title);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.FH_SETTINGS;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_off);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
