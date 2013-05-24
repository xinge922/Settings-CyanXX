/* //device/apps/Settings/src/com/android/settings/Keyguard.java
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package com.android.settings;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ServiceManager;
import android.os.ServiceManagerNative;
import android.provider.Settings;
import android.os.Bundle;
import android.util.Log;
import android.view.IWindowManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;

public class DevelopmentSettings2 extends Activity {
    private static final String TAG = "DevelopmentSettings2";

    private CheckBox mAlwaysFinishCB;
    private Spinner mPointerLocationSpinner;
    private CheckBox mShowLoadCB;
    private CheckBox mCompatibilityModeCB;
    private Spinner mMaxProcsSpinner;
    private Spinner mWindowAnimationScaleSpinner;
    private Spinner mTransitionAnimationScaleSpinner;
    private Spinner mFontHintingSpinner;

    private boolean mAlwaysFinish;
    private int mPointerLocation;
    private int mProcessLimit;
    private boolean mCompatibilityMode;
    private AnimationScaleSelectedListener mWindowAnimationScale
            = new AnimationScaleSelectedListener(0);
    private AnimationScaleSelectedListener mTransitionAnimationScale
            = new AnimationScaleSelectedListener(1);
    private SharedPreferences mSharedPrefs;
    private IWindowManager mWindowManager;

    private static final boolean FONT_HINTING_ENABLED = true;
    private static final String  FONT_HINTING_FILE = "/data/misc/font-hack";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.development_settings2);

        mAlwaysFinishCB = (CheckBox)findViewById(R.id.always_finish);
        mAlwaysFinishCB.setOnClickListener(mAlwaysFinishClicked);
        mPointerLocationSpinner = (Spinner)findViewById(R.id.pointer_location);
        mPointerLocationSpinner.setOnItemSelectedListener(mPointerLocationChanged);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[] {
                        "No Touch Pointer",
                        "Touch Pointer" });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mPointerLocationSpinner.setAdapter(adapter);
        mShowLoadCB = (CheckBox)findViewById(R.id.show_load);
        mShowLoadCB.setOnClickListener(mShowLoadClicked);
        mCompatibilityModeCB = (CheckBox)findViewById(R.id.compatibility_mode);
        mCompatibilityModeCB.setOnClickListener(mCompatibilityModeClicked);
        mMaxProcsSpinner = (Spinner)findViewById(R.id.max_procs);
        mMaxProcsSpinner.setOnItemSelectedListener(mMaxProcsChanged);
        adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[] {
                        "No App Process Limit",
                        "Max 1 App Process",
                        "Max 2 App Processes",
                        "Max 3 App Processes",
                        "Max 4 App Processes" });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mMaxProcsSpinner.setAdapter(adapter);
        mWindowAnimationScaleSpinner = setupAnimationSpinner(
                R.id.window_animation_scale, mWindowAnimationScale, "Window");
        mTransitionAnimationScaleSpinner = setupAnimationSpinner(
                R.id.transition_animation_scale, mTransitionAnimationScale, "Transition");

        if (FONT_HINTING_ENABLED) {
            mFontHintingSpinner = (Spinner)findViewById(R.id.font_hinting);
            mFontHintingSpinner.setOnItemSelectedListener(mFontHintingChanged);
            adapter = new ArrayAdapter<String>(
                    this,
                    android.R.layout.simple_spinner_item,
                    new String[] {
                            "Light Hinting",
                            "Medium Hinting" });
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mFontHintingSpinner.setAdapter(adapter);
        }
        mSharedPrefs = getSharedPreferences("global", 0);
        mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
    }

    Spinner setupAnimationSpinner(int resid,
            AnimationScaleSelectedListener listener, String name) {
        Spinner spinner = (Spinner)findViewById(resid);
        spinner.setOnItemSelectedListener(listener);
        ArrayAdapter adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[] {
                        name + " Animation Scale 1x",
                        name + " Animation Scale 2x",
                        name + " Animation Scale 5x",
                        name + " Animation Scale 10x",
                        name + " Animation Off" });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        listener.spinner = spinner;
        return spinner;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updateFinishOptions();
        updatePointerLocationOptions();
        updateProcessLimitOptions();
        updateSharedOptions();
        updateCompatibilityOptions();

        try {
            FileInputStream  in = new FileInputStream( FONT_HINTING_FILE );
            int    mode = in.read() - 48;
            if (mode >= 0 && mode < 3)
                mFontHintingSpinner.setSelection(mode);
            in.close();
        } catch (Exception e) {
        }

        mWindowAnimationScale.load();
        mTransitionAnimationScale.load();
    }

    private void writeFinishOptions() {
        try {
            ActivityManagerNative.getDefault().setAlwaysFinish(mAlwaysFinish);
        } catch (RemoteException ex) {
        }
    }

    private void updateFinishOptions() {
        mAlwaysFinish = Settings.System.getInt(
            getContentResolver(), Settings.System.ALWAYS_FINISH_ACTIVITIES, 0) != 0;
        mAlwaysFinishCB.setChecked(mAlwaysFinish);
    }

    private void writePointerLocationOptions() {
        Settings.System.putInt(getContentResolver(),
                Settings.System.POINTER_LOCATION, mPointerLocation);
    }

    private void updatePointerLocationOptions() {
        mPointerLocation = Settings.System.getInt(getContentResolver(),
                Settings.System.POINTER_LOCATION, 0);
        mPointerLocationSpinner.setSelection(mPointerLocation);
    }

    private void writeProcessLimitOptions() {
        try {
            ActivityManagerNative.getDefault().setProcessLimit(mProcessLimit);
        } catch (RemoteException ex) {
        }
    }

    private void updateProcessLimitOptions() {
        try {
            mProcessLimit = ActivityManagerNative.getDefault().getProcessLimit();
            mMaxProcsSpinner.setSelection(mProcessLimit);
        } catch (RemoteException ex) {
        }
    }

    private void updateSharedOptions() {
        mShowLoadCB.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.SHOW_PROCESSES, 0) != 0);
    }

    private void writeCompatibilityOptions() {
        Settings.System.putInt(getContentResolver(),
                Settings.System.COMPATIBILITY_MODE, mCompatibilityMode ? 0 : 1);
    }

    private void updateCompatibilityOptions() {
        mCompatibilityMode = Settings.System.getInt(
            getContentResolver(), Settings.System.COMPATIBILITY_MODE, 1) == 0;
        mCompatibilityModeCB.setChecked(mCompatibilityMode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    }


    private View.OnClickListener mAlwaysFinishClicked =
            new View.OnClickListener() {
        public void onClick(View v) {
            mAlwaysFinish = ((CheckBox)v).isChecked();
            writeFinishOptions();
            updateFinishOptions();
        }
    };

    private View.OnClickListener mCompatibilityModeClicked =
        new View.OnClickListener() {
    public void onClick(View v) {
        mCompatibilityMode = ((CheckBox)v).isChecked();
        writeCompatibilityOptions();
        updateCompatibilityOptions();
        Toast toast = Toast.makeText(DevelopmentSettings2.this,
                R.string.development_settings_compatibility_mode_toast,
                Toast.LENGTH_LONG);
        toast.show();
    }
};

    private View.OnClickListener mShowLoadClicked = new View.OnClickListener() {
        public void onClick(View v) {
            boolean value = ((CheckBox)v).isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SHOW_PROCESSES, value ? 1 : 0);
            Intent service = (new Intent())
                    .setClassName("android", "com.android.server.LoadAverageService");
            if (value) {
                startService(service);
            } else {
                stopService(service);
            }
        }
    };

    private Spinner.OnItemSelectedListener mPointerLocationChanged
                                    = new Spinner.OnItemSelectedListener() {
        public void onItemSelected(android.widget.AdapterView av, View v,
                                    int position, long id) {
            mPointerLocation = position;
            writePointerLocationOptions();
        }

        public void onNothingSelected(android.widget.AdapterView av) {
        }
    };

    private Spinner.OnItemSelectedListener mMaxProcsChanged
                                    = new Spinner.OnItemSelectedListener() {
        public void onItemSelected(android.widget.AdapterView av, View v,
                                    int position, long id) {
            mProcessLimit = position;
            writeProcessLimitOptions();
        }

        public void onNothingSelected(android.widget.AdapterView av) {
        }
    };

    private Spinner.OnItemSelectedListener mFontHintingChanged
                                    = new Spinner.OnItemSelectedListener() {
        public void onItemSelected(android.widget.AdapterView  av, View v,
                                    int position, long id) {
            try {
                FileOutputStream  out = new FileOutputStream( FONT_HINTING_FILE );
                out.write(position+48);
                out.close();
            } catch (Exception e) {
                Log.w(TAG, "Failed to write font hinting settings to /data/misc/font-hack");
            }
        }

        public void onNothingSelected(android.widget.AdapterView av) {
        }
    };

    class AnimationScaleSelectedListener implements OnItemSelectedListener {
        final int which;
        float scale;
        Spinner spinner;
        
        AnimationScaleSelectedListener(int _which) {
            which = _which;
        }
        
        void load() {
            try {
                scale = mWindowManager.getAnimationScale(which);

                if (scale > 0.1f && scale < 2.0f) {
                    spinner.setSelection(0);
                } else if (scale >= 2.0f && scale < 3.0f) {
                    spinner.setSelection(1);
                } else if (scale >= 4.9f && scale < 6.0f) {
                    spinner.setSelection(2);
                }  else if (scale >= 9.9f && scale < 11.0f) {
                    spinner.setSelection(3);
                } else {
                    spinner.setSelection(4);
                }
            } catch (RemoteException e) {
            }
        }
        
        public void onItemSelected(android.widget.AdapterView av, View v,
                int position, long id) {
            switch (position) {
                case 0: scale = 1.0f; break;
                case 1: scale = 2.0f; break;
                case 2: scale = 5.0f; break;
                case 3: scale = 10.0f; break;
                case 4: scale = 0.0f; break;
                default: break;
            }

            try {
                mWindowManager.setAnimationScale(which, scale);
            } catch (RemoteException e) {
            }
        }

        public void onNothingSelected(android.widget.AdapterView av) {
        }
    }
}
