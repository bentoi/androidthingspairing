// **********************************************************************
//
// Copyright (c) 2018-present ZeroC, Inc. All rights reserved.
//
// **********************************************************************

package com.zeroc.androidthingspairing;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.util.Log;

import com.google.android.things.bluetooth.BluetoothConnectionManager;
import com.google.android.things.bluetooth.PairingParams;
import com.google.android.things.bluetooth.BluetoothPairingCallback;

import java.util.Set;

public class MainActivity extends Activity
{
    final String TAG = "com.zeroc.androidthingspairing";

    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    public static String EXTRA_DEVICE_NAME = "device_name";

    private BluetoothAdapter _adapter;
    private BluetoothConnectionManager _connectionManager;
    private ArrayAdapter<DeviceInfo> _devices;
    private DeviceInfo _currentDevice;
    private Button _scanButton;
    private Button _pairButton;
    private Button _unpairButton;

    //
    // DeviceInfo encapsulates the details about each Bluetooth device that we discover.
    //
    static class DeviceInfo
    {
        DeviceInfo(BluetoothDevice device)
        {
            _device = device;
            _name = device.getName();
            _address = device.getAddress();
        }

        DeviceInfo(String text)
        {
            _text = text;
        }

        @Override
        public String toString()
        {
            if(_text != null)
            {
                return _text;
            }

            StringBuffer s = new StringBuffer();
            s.append(_name != null ? _name : "Unknown device");
            if(_device.getBondState() == BluetoothDevice.BOND_BONDED)
            {
                s.append(" (paired)");
            }
            s.append("\n");
            s.append(_address);
            return s.toString();
        }

        @Override
        public boolean equals(Object o)
        {
            if(!(o instanceof DeviceInfo) || _text != null)
            {
                return false;
            }

            DeviceInfo di = (DeviceInfo)o;
            return getAddress().equals(di.getAddress());
        }

        boolean hasDevice()
        {
            return _device != null;
        }

        BluetoothDevice getDevice()
        {
            return _device;
        }

        boolean isBonded()
        {
            return _device.getBondState() == BluetoothDevice.BOND_BONDED;
        }

        String getAddress()
        {
            return _address;
        }

        String getName()
        {
            return _name;
        }

        private BluetoothDevice _device;
        private String _name;
        private String _address;
        private String _text;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        _scanButton = (Button)findViewById(R.id.scan);
        _scanButton.setOnClickListener(
            new View.OnClickListener()
            {
                public void onClick(View v)
                {
                    runDiscovery();
                    v.setEnabled(false);
                }
            });

        _pairButton = (Button)findViewById(R.id.pair);
        _pairButton.setOnClickListener(
            new View.OnClickListener()
            {
                public void onClick(View v)
                {
                    runPair();
                    v.setEnabled(false);
                }
            });
        _pairButton.setEnabled(false);

        _unpairButton = (Button)findViewById(R.id.unpair);
        _unpairButton.setOnClickListener(
            new View.OnClickListener()
            {
                public void onClick(View v)
                {
                    runUnpair();
                    v.setEnabled(false);
                }
            });
        _unpairButton.setEnabled(false);

        _devices = new ArrayAdapter<DeviceInfo>(this, R.layout.device_name);

        ListView list = (ListView)findViewById(R.id.devices);
        list.setAdapter(_devices);
        list.setOnItemClickListener(_deviceListener);
        list.setItemsCanFocus(true);

        //
        // Listen for discovery of new Bluetooth devices.
        //
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(_receiver, filter);

        _adapter = BluetoothAdapter.getDefaultAdapter();

        //
        // Add the current list of paired devices.
        //
        Set<BluetoothDevice> paired = _adapter.getBondedDevices();
        if(paired.size() > 0)
        {
            for(BluetoothDevice device : paired)
            {
                _devices.add(new DeviceInfo(device));
            }
        }
        else
        {
            //
            // Add an entry indicating that there are no paired devices.
            //
            _devices.add(new DeviceInfo(getResources().getText(R.string.none_paired).toString()));
        }

        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra( BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 600);
        startActivityForResult(discoverableIntent, 0);

        _connectionManager = BluetoothConnectionManager.getInstance();
        _connectionManager.registerPairingCallback(_pairingCallback);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if(_adapter != null)
        {
            _adapter.cancelDiscovery();
        }

        unregisterReceiver(_receiver);
        _connectionManager.unregisterPairingCallback(_pairingCallback);
    }

    private void runDiscovery()
    {
        setTitle(R.string.scanning);

        if(_adapter.isDiscovering())
        {
            _adapter.cancelDiscovery();
        }

        _adapter.startDiscovery();
    }

    private void runPair()
    {
        setTitle(R.string.pair);

        if(_adapter.isDiscovering())
        {
            _adapter.cancelDiscovery();
        }

        assert(_currentDevice != null && _currentDevice.hasDevice());
        _connectionManager.initiatePairing(_currentDevice.getDevice());
    }

    private void runUnpair()
    {
        setTitle(R.string.unpair);

        if(_adapter.isDiscovering())
        {
            _adapter.cancelDiscovery();
        }

        assert(_currentDevice != null && _currentDevice.hasDevice());
        _connectionManager.unpair(_currentDevice.getDevice());
    }

    private AdapterView.OnItemClickListener _deviceListener = new AdapterView.OnItemClickListener()
    {
        public void onItemClick(AdapterView<?> av, View v, int pos, long id)
        {
            DeviceInfo info = _devices.getItem(pos);
            if(!info.hasDevice())
            {
                return;
            }
            _currentDevice = info;
            _pairButton.setEnabled(!_currentDevice.isBonded());
            _unpairButton.setEnabled(_currentDevice.isBonded());
        }
    };

    private final BroadcastReceiver _receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if(BluetoothDevice.ACTION_FOUND.equals(action))
            {
                //
                // Remove the placeholder message (if any).
                //
                if(_devices.getCount() == 1)
                {
                    DeviceInfo info = _devices.getItem(0);
                    if(!info.hasDevice())
                    {
                        _devices.clear();
                    }
                }

                //
                // Add a device if it's not already in the list.
                //
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                DeviceInfo info = new DeviceInfo(device);
                int pos = _devices.getPosition(info);
                if(pos < 0)
                {
                    _devices.add(new DeviceInfo(device));
                }
                else
                {
                    //
                    // The device is already present but we force a refresh in case its state
                    // has changed.
                    //
                    _devices.notifyDataSetChanged();
                }
            }
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                setTitle(R.string.choose_peer);

                if(_devices.getCount() == 1)
                {
                    DeviceInfo info = _devices.getItem(0);
                    if(!info.hasDevice())
                    {
                        _devices.clear();
                    }
                }

                //
                // Add a placeholder entry.
                //
                if(_devices.getCount() == 0)
                {
                    _devices.add(new DeviceInfo(getResources().getText(R.string.none_found).toString()));
                }

                _scanButton.setEnabled(true);
            }
        }
    };

    private final BluetoothPairingCallback _pairingCallback = new BluetoothPairingCallback() {

        @Override
        public void onPairingInitiated(BluetoothDevice device, PairingParams pairingParams) {
            switch (pairingParams.getPairingType()) {
                case PairingParams.PAIRING_VARIANT_DISPLAY_PIN:
                case PairingParams.PAIRING_VARIANT_DISPLAY_PASSKEY:
                    Log.d(TAG, "Display Passkey - " + pairingParams.getPairingPin());
                    break;
                case PairingParams.PAIRING_VARIANT_PIN:
                case PairingParams.PAIRING_VARIANT_PIN_16_DIGITS:
                    Log.d(TAG, "Requested PIN");
                    _connectionManager.finishPairing(device, "0000");
                    break;
                case PairingParams.PAIRING_VARIANT_CONSENT:
                case PairingParams.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                    _connectionManager.finishPairing(device);
                    break;
            }
        }

        @Override
        public void onPaired(BluetoothDevice device) {
            runOnUiThread(() -> {
                    if(_currentDevice != null && device.equals(_currentDevice.getDevice()))
                    {
                        _unpairButton.setEnabled(true);
                    }
                    _devices.notifyDataSetChanged();
                });
        }

        @Override
        public void onUnpaired(BluetoothDevice device) {
            runOnUiThread(() -> {
                if(_currentDevice != null && _currentDevice.getDevice().equals(device))
                {
                    _pairButton.setEnabled(true);
                }
                _devices.notifyDataSetChanged();
            });
        }


        @Override
        public void onPairingError(BluetoothDevice device, BluetoothPairingCallback.PairingError pairingError) {
            runOnUiThread(() -> {
                setTitle(R.string.pairing_error);
            });
        }
    };
}
