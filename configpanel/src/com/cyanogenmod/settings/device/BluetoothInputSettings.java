package com.cyanogenmod.settings.device;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.MenuItem;
import android.widget.Toast;

import com.cyanogenmod.settings.device.utils.Constants;

@SuppressWarnings("deprecation")
public class BluetoothInputSettings extends PreferenceActivity implements OnPreferenceChangeListener {

    static final String PROCESS_COMMAND_ACTION = "process_command";
    static final String COMMAND_KEY = "command";

    private static final int BLUETOOTH_REQUEST_CODE = 1;
    private static final int BLUETOOTH_PICKER_CODE = 2;
    private static final String sOclickActionsCategory = "oclick_action_category";
    private static final String sOclickAlertCategory = "oclick_alert_category";
    private static final String sOclickConnectPreference = "oclick_connect";

    private ProgressDialog mProgressDialog;
    EventReceiver mReceiver;
    boolean mConnected;
    Ringtone mRingtone;

    class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int commandKey = intent.getIntExtra(COMMAND_KEY, -1);
            switch (commandKey) {
            case BluetoothGatt.STATE_CONNECTED:
                if (mProgressDialog != null) {
                    setConnectedState(true);
                    mProgressDialog.dismiss();
                    Toast.makeText(context, "O-click connected", Toast.LENGTH_SHORT).show();
                }
                break;
            case BluetoothGatt.STATE_DISCONNECTED:
                setConnectedState(false);
                break;
            }
        }
    }

    void setConnectedState(boolean enable) {
        mConnected = enable;
        findPreference(sOclickActionsCategory).setEnabled(enable);
        findPreference(sOclickAlertCategory).setEnabled(enable);
        findPreference(sOclickConnectPreference).setTitle(enable ?
                R.string.oclick_disconnect_string : R.string.oclick_connect_string);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.oclick_panel);
        mReceiver = new EventReceiver();
        setConnectedState(OclickService.isConnectedToOclick);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isBluetoothOn() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        return (bluetoothAdapter != null && bluetoothAdapter.isEnabled());
    }

    private void startBluetootDevicePicker() {
        // Start bluetooth device picker
        Intent i = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
        i.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_PACKAGE, getPackageName());
        i.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_CLASS, BluetoothReceiver.class.getName());
        startActivityForResult(i, BLUETOOTH_PICKER_CODE);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (!preference.getKey().equals(Constants.OCLICK_CONNECT_KEY)) {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
        if (mConnected) {
            Intent i = new Intent(this, OclickService.class);
            stopService(i);
            setConnectedState(false);
        } else {
            if (!isBluetoothOn()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, BLUETOOTH_REQUEST_CODE);
            } else {
                startBluetootDevicePicker();
            }
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == BLUETOOTH_REQUEST_CODE) && (resultCode == Activity.RESULT_OK)) {
            // Start bluetooth device picker
            startBluetootDevicePicker();
        } else if (requestCode == BLUETOOTH_PICKER_CODE && isBluetoothOn()) {
            String dialogTitle = this.getString(R.string.oclick_dialog_title);
            String dialogMessage = this.getString(R.string.oclick_dialog_connecting_message);
            mProgressDialog = ProgressDialog.show(this, dialogTitle, dialogMessage, true);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                    }
                }
            }, 10000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PROCESS_COMMAND_ACTION);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }
}
