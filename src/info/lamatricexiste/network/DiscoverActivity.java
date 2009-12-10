package info.lamatricexiste.network;

import info.lamatricexiste.network.HostDiscovery.DiscoveryUnicast;
import info.lamatricexiste.network.PortScan.PortScan;
import info.lamatricexiste.network.Utils.Export;
import info.lamatricexiste.network.Utils.HardwareAddress;
import info.lamatricexiste.network.Utils.NetInfo;
import info.lamatricexiste.network.Utils.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

final public class DiscoverActivity extends Activity {

    private final String TAG = "NetworkMain";
    // private final int DEFAULT_DISCOVER = 1;
    private final long VIBRATE = (long) 250;
    private List<String> hosts = null; // TODO: Use a HostBean objects list
    private List<Long[]> hosts_ports = null;
    private List<String> hosts_haddr = null;
    private HostsAdapter adapter;
    private ListView list;
    // private Button btn;
    private Button btn_discover;
    private Button btn_export;
    private SharedPreferences prefs = null;
    // private boolean rooted = false;
    private ConnectivityManager connMgr;
    private CheckHostsTask checkHostsTask = null;
    private ScanPortTask scanPortTask = null;
    private Context ctxt;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);
        ctxt = this;

        // Get global preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Discover
        btn_discover = (Button) findViewById(R.id.btn_discover);
        btn_discover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                checkHostsTask = new CheckHostsTask();
                startDiscovering();
            }
        });

        // Export
        btn_export = (Button) findViewById(R.id.btn_export);
        btn_export.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                export();
            }
        });

        // Options
        Button btn_options = (Button) findViewById(R.id.btn_options);
        btn_options.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(DiscoverActivity.this, Prefs.class));
            }
        });

        // Wifi Settings
        Button btn_wifi = (Button) findViewById(R.id.btn_wifi);
        btn_wifi.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });

        // Send Request
        // btn = (Button) findViewById(R.id.btn);
        // btn.setOnClickListener(new View.OnClickListener() {
        // public void onClick(View v) {
        // sendPacket();
        // }
        // });

        // All
        // Button btn3 = (Button) findViewById(R.id.btn3);
        // btn3.setOnClickListener(new View.OnClickListener() {
        // public void onClick(View v) {
        // setSelectedHosts(true);
        // }
        // });

        // None
        // Button btn4 = (Button) findViewById(R.id.btn4);
        // btn4.setOnClickListener(new View.OnClickListener() {
        // public void onClick(View v) {
        // setSelectedHosts(false);
        // }
        // });

        // Hosts list
        adapter = new HostsAdapter(this, R.layout.list, R.id.list);
        list = (ListView) findViewById(R.id.output);
        list.setAdapter(adapter);
        list.setItemsCanFocus(true);

        connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // checkRoot();

        // Fake hosts
        // adapter.add("10.0.10.1");
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(receiver, filter);
        // networkStateChanged(new Intent()); //FIXME ?
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(getApplication()).inflate(R.menu.options, menu);
        return (super.onCreateOptionsMenu(menu));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings) {
            startActivity(new Intent(this, Prefs.class));
            return true;
        }
        return (super.onOptionsItemSelected(item));
    }

    // Custom ArrayAdapter
    private class HostsAdapter extends ArrayAdapter<String> {
        public HostsAdapter(Context context, int resource,
                int textViewresourceId) {
            super(context, resource, textViewresourceId);
        }

        @Override
        public View getView(final int position, View convertView,
                ViewGroup parent) {
            convertView = super.getView(position, convertView, parent);
            if (convertView != null) {
                // Add listeners to the Buttons
                Button btn_ports = (Button) convertView
                        .findViewById(R.id.list_port);
                btn_ports.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        scanPort(position, false);
                    }
                });
                Button btn_info = (Button) convertView
                        .findViewById(R.id.list_info);
                btn_info.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        showHostInfo(position);
                    }
                });
            }
            return convertView;
        }
    }

    // Broadcast Receiver
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            networkStateChanged(intent);
        }
    };

    private void networkStateChanged(Intent intent) {
        // Use NetworkInfo
        TextView info_ip = (TextView) findViewById(R.id.info_ip);
        TextView info_nt = (TextView) findViewById(R.id.info_nt);
        TextView info_id = (TextView) findViewById(R.id.info_id);

        info_ip.setText("");
        info_id.setText("");
        setButtonOff(btn_discover);
        setButtonOff(btn_export);

        NetInfo net = new NetInfo(ctxt);

        // Wifi state
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int WifiState = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, -1);
                Log.d(TAG, "WifiState=" + WifiState);
                switch (WifiState) {
                    case WifiManager.WIFI_STATE_ENABLING:
                        info_nt.setText(R.string.wifi_enabling);
                        break;
                    case WifiManager.WIFI_STATE_ENABLED:
                        info_nt.setText(R.string.wifi_enabled);
                        break;
                    case WifiManager.WIFI_STATE_DISABLING:
                        info_nt.setText(R.string.wifi_disabling);
                        break;
                    case WifiManager.WIFI_STATE_DISABLED:
                        info_nt.setText(R.string.wifi_disabled);
                        break;
                    default:
                        info_nt.setText(R.string.wifi_unknown);
                }
            }

            if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                SupplicantState sstate = net.getSupplicantState();
                Log.d(TAG, "SupplicantState=" + sstate);
                if (sstate == SupplicantState.SCANNING) {
                    info_nt.setText(R.string.wifi_scanning);
                } else if (sstate == SupplicantState.ASSOCIATING) {
                    String bssid = net.getBSSID();
                    String ssid = net.getSSID();
                    String mac = net.getMacAddress();
                    String id = ssid != null ? ssid : (bssid != null ? bssid
                            : mac);
                    info_nt.setText(String.format(
                            getString(R.string.wifi_associating), id));
                } else if (sstate == SupplicantState.COMPLETED) {
                    info_nt.setText(String.format(
                            getString(R.string.wifi_dhcp), net.getSSID()));
                }

            }
        }

        // 3G(connected) -> Wifi(connected)
        final NetworkInfo network_info = connMgr
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (network_info != null) {
            NetworkInfo.State state = network_info.getState();
            Log.d(TAG, "netinfo=" + state + " with " + network_info.getType());
            // Connection check
            if (net.getSSID() != null && state == NetworkInfo.State.CONNECTED) {
                // Hack to let the wifi start after wakeup, FIXME ?
                /*
                 * if(net.getSSID()==null){ try{ Thread.sleep(2000); } catch
                 * (InterruptedException e){ Log.e(TAG,
                 * "Wifi is re-starting="+e.getMessage()); } }
                 */
                info_ip.setText("IP: " + net.getIp());
                info_nt.setText("NT: " + net.getNetIp() + "/"
                        + net.getNetCidr());
                info_id.setText("SSID: " + net.getSSID());
                setButtonOn(btn_discover, R.drawable.discover);
                setButtonOn(btn_export, R.drawable.export);
            } else if (checkHostsTask != null) {
                cancelAllTasks();
            }
        } else if (checkHostsTask != null) {
            cancelAllTasks();
        }
    }

    /**
     * Discover hosts
     */

    private class CheckHostsTask extends DiscoveryUnicast {
        private int hosts_done = 0;

        @Override
        protected void onPreExecute() {
            prefsMgr = prefs;
            NetInfo net = new NetInfo(ctxt);
            ip = NetInfo.getLongFromIp(net.getIp()); // FIXME: I know it's ugly
            // int shift = (1 << (32 - net.getNetCidr()));
            // start = (ip & (1 - shift)) + 1;
            // end = (ip | (shift - 1)) - 1;
            int shift = (32 - net.getNetCidr());
            start = (ip >> shift << shift) + 1;
            end = (start | ((1 << shift) - 1)) - 1;
            size = (int) (end - start + 1);
            setProgress(0);
        }

        @Override
        protected void onProgressUpdate(String... item) {
            if (!isCancelled()) {
                String host = item[0];
                if (!host.equals(new String())) {
                    addHost(host);
                }
                hosts_done++;
                setProgress(hosts_done * 10000 / size);
            }
        }

        @Override
        protected void onPostExecute(Void unused) {
            if (prefs.getBoolean(Prefs.KEY_VIBRATE_FINISH,
                    Prefs.DEFAULT_VIBRATE_FINISH) == true) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(VIBRATE);
            }
            makeToast(R.string.discover_finished);
            stopDiscovering();
        }

        @Override
        protected void onCancelled() {
            pool.shutdownNow();
            makeToast(R.string.discover_canceled);
            stopDiscovering();
        }
    }

    private void startDiscovering() {
        makeToast(R.string.discover_start);
        setProgressBarVisibility(true);
        setProgressBarIndeterminateVisibility(true);
        initList();
        checkHostsTask.execute();
        btn_discover.setText(R.string.btn_discover_cancel);
        btn_discover.setCompoundDrawablesWithIntrinsicBounds(0,
                R.drawable.cancel, 0, 0);
        btn_discover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                checkHostsTask.cancel(true);
            }
        });
    }

    private void stopDiscovering() {
        setProgressBarVisibility(false);
        setProgressBarIndeterminateVisibility(false);
        btn_discover.setText(R.string.btn_discover);
        btn_discover.setCompoundDrawablesWithIntrinsicBounds(0,
                R.drawable.discover, 0, 0);
        btn_discover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                checkHostsTask = new CheckHostsTask();
                startDiscovering();
            }
        });
    }

    private void cancelAllTasks() {
        if (checkHostsTask != null) {
            checkHostsTask.cancel(true);
            checkHostsTask = null;
        }
        if (scanPortTask != null) {
            scanPortTask.cancel(true);
            scanPortTask = null;
        }
    }

    private void initList() {
        // setSelectedHosts(false);
        adapter.clear();
        hosts = new ArrayList<String>();
        hosts_ports = new ArrayList<Long[]>();
        hosts_haddr = new ArrayList<String>();
    }

    private void addHost(String ip) {
        String haddr = HardwareAddress.getHardwareAddress(ip);
        if (!hosts_haddr.contains(haddr)) {
            adapter.add(ip);
            hosts.add(ip);
            hosts_ports.add(null);
            hosts_haddr.add(haddr);
        } else {
            if (checkHostsTask != null) {
                checkHostsTask.cancel(true);
            }
            NetInfo net = new NetInfo(this);
            AlertDialog.Builder infoDialog = new AlertDialog.Builder(DiscoverActivity.this);
            infoDialog.setTitle(R.string.discover_proxy_title);
            infoDialog
                    .setMessage(String.format(
                            getString(R.string.discover_proxy_msg), net
                                    .getGatewayIp()));
            infoDialog.setNegativeButton(R.string.btn_close, null);
            infoDialog.show();
        }
    }

    private void showHostInfo(int hostPosition) {
        String ip = hosts.get(hostPosition);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.info, null);
        // Build info dialog
        AlertDialog.Builder infoDialog = new AlertDialog.Builder(DiscoverActivity.this);
        infoDialog.setTitle(ip);
        // Set info values
        HardwareAddress hardwareAddress = new HardwareAddress(this);
        String macaddr = hosts_haddr.get(hostPosition);
        String nicvend = hardwareAddress.getNicVendor(macaddr);
        TextView mac = (TextView) v.findViewById(R.id.info_mac);
        TextView vendor = (TextView) v.findViewById(R.id.info_nic);
        mac.setText(macaddr);
        vendor.setText(nicvend);
        // Show dialog
        infoDialog.setView(v);
        infoDialog.setNegativeButton(R.string.btn_close, null);
        infoDialog.show();
    }

    /**
     * Port Scan
     */

    private class ScanPortTask extends PortScan {

        private ProgressDialog progress = null;
        private ArrayList<Long> ports = new ArrayList<Long>();
        private int progress_current = 0;

        ScanPortTask(int position, String host) {
            super(position, host);
        }

        @Override
        protected void onPreExecute() {
            // Get preferences
            String port_start_pref = prefs.getString(Prefs.KEY_PORT_START,
                    Prefs.DEFAULT_PORT_START);
            String port_end_pref = prefs.getString(Prefs.KEY_PORT_END,
                    Prefs.DEFAULT_PORT_END);
            port_start = Integer.parseInt(port_start_pref);
            port_end = Integer.parseInt(port_end_pref);
            nb_port = port_end - port_start + 1;
            // Set progress
            progress = new ProgressDialog(DiscoverActivity.this);
            progress.setMessage(String.format(getString(R.string.scan_start),
                    host));
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setMax(nb_port);
            // Cancelable
            progress.setCancelable(true);
            progress.setButton(ProgressDialog.BUTTON_NEGATIVE,
                    getString(R.string.btn_discover_cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            scanPortTask.cancel(true);
                        }
                    });
            progress.show();
        }

        @Override
        protected void onPostExecute(Void unused) {
            Collections.sort(ports);
            Long[] result = ports.toArray(new Long[ports.size()]);
            hosts_ports.set(position, result);
            progress.dismiss();
            showPorts(result, position, host);
            if (prefs.getBoolean(Prefs.KEY_VIBRATE_FINISH,
                    Prefs.DEFAULT_VIBRATE_FINISH) == true) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(VIBRATE);
            }
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            if (values.length > 0) {
                if (!values[0].equals(new Long(0))) {
                    ports.add(values[0]);
                }
            }
            progress_current++;
            progress.setProgress(progress_current);
        }
    }

    private void scanPort(final int position, boolean force) {
        String host = hosts.get(position);
        Long[] ports = hosts_ports.get(position);
        if (wifiConnectedOrWarn() && (force || ports == null)) {
            scanPortTask = new ScanPortTask(position, host);
            scanPortTask.execute();
        } else if (ports != null) {
            showPorts(ports, position, host);
        }
    }

    private void showPorts(final Long[] ports, final int position,
            final String host) {
        final AlertDialog.Builder scanDone = new AlertDialog.Builder(DiscoverActivity.this);
        scanDone.setTitle(host).setPositiveButton(R.string.btn_rescan,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sumthin) {
                        scanPort(position, true);
                    }
                }).setNegativeButton(R.string.btn_close, null);
        if (ports.length > 0) {
            scanDone.setItems(preparePort(ports),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            openPortService(host, ports[which]);
                            scanDone.show();
                        }
                    });
        } else {
            scanDone.setMessage(R.string.scan_noport);
        }
        scanDone.show();
    }

    public static CharSequence[] preparePort(Long[] ports) {
        CharSequence[] portsChar = new CharSequence[ports.length];
        for (int i = 0; i < ports.length; i++) {
            portsChar[i] = (CharSequence) String.valueOf(ports[i])
                    + "/tcp open";
        }
        return portsChar;
    }

    private void openPortService(String host, Long port) {
        Intent intent = null;
        String action = "";
        int portInt = (int) ((long) port);
        switch (portInt) {
            case 22:
                action = Intent.ACTION_VIEW;
                if (isPackageInstalled(this, "org.connectbot")) {
                    String user = prefs.getString(Prefs.KEY_SSH_USER,
                            Prefs.DEFAULT_SSH_USER);
                    intent = new Intent(action);
                    intent.setData(Uri.parse("ssh://" + user + "@" + host
                            + ":22/#" + user + "@" + host + ":22"));
                } else {
                    makeToast(String.format(getString(R.string.package_missing,
                            "ConnectBot")));
                }
                break;
            case 23:
                action = Intent.ACTION_VIEW;
                if (isPackageInstalled(this, "org.connectbot")) {
                    intent = new Intent(action);
                    intent.setData(Uri.parse("telnet://" + host + ":23"));
                } else {
                    makeToast(String.format(getString(R.string.package_missing,
                            "ConnectBot")));
                }
                break;
            case 80:
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("http://" + host + "/"));
                break;
            case 443:
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://" + host + "/"));
                break;
            default:
                makeToast(R.string.scan_noaction);
                // Use something like netcat to
                // fetch identification message of service
        }
        if (intent != null) {
            startActivity(intent);
        }
    }

    /**
     * Main
     */

    // private boolean isIntentAvailable(Context context, String action) {
    // final PackageManager packageManager = context.getPackageManager();
    // final Intent intent = new Intent(action);
    // List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
    // PackageManager.GET_RESOLVED_FILTER);
    // for (int i = 0; i < list.size(); i++) {
    // Log.v(TAG, list.get(i).activityInfo.packageName);
    // }
    // return list.size() > 0;
    // }

    private boolean isPackageInstalled(Context context, String p) {
        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo(p, 0);
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }

    private boolean wifiConnectedOrWarn() {
        final NetworkInfo network_info = connMgr
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (network_info.getState() == NetworkInfo.State.CONNECTED) {
            return true;
        }
        AlertDialog.Builder alert = new AlertDialog.Builder(DiscoverActivity.this);
        alert.setMessage(R.string.wifi_disabled);
        alert.setPositiveButton(R.string.btn_close, null);
        alert.show();
        return false;
    }

    // private void checkRoot() {
    // // Borrowed here: http://bit.ly/754iGA
    // try {
    // File su = new File("/system/bin/su");
    // if (su.exists() == false) {
    // rooted = false;
    // }
    // } catch (Exception e) {
    // Log.d(TAG, "Can't obtain root: " + e.getMessage());
    // rooted = false;
    // }
    // }

    // private void sendPacket(){
    // CheckBox cb = (CheckBox) findViewById(R.id.repeat);
    // final boolean repeat = cb.isChecked();
    // final CharSequence[] items = {"Ping (ICMP)","Samba exploit"};
    // setButtonOff(btn);
    // @SuppressWarnings("unused")
    // AlertDialog dialog = new AlertDialog.Builder(this)
    // .setTitle("Select method")
    // .setItems(items, new DialogInterface.OnClickListener() {
    // public void onClick(DialogInterface dialog, int item) {
    // try {
    // makeToast("Sending request ...");
    // netInterface.inSendPacket(getSelectedHosts(), item, repeat);
    // } catch (RemoteException e) {
    // Log.e(TAG, e.getMessage());
    // } catch (IllegalStateException e){
    // Log.e(TAG, e.getMessage());
    // }
    // }
    // })
    // .show();
    // }

    private void export() {
        final Export e = new Export(DiscoverActivity.this, hosts, hosts_ports, hosts_haddr);
        final String file = e.getFileName();

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.file, null);
        final EditText txt = (EditText) v.findViewById(R.id.export_file);
        txt.setText(file);

        AlertDialog.Builder getFileName = new AlertDialog.Builder(DiscoverActivity.this);
        getFileName.setTitle(R.string.export_choose);
        getFileName.setView(v);
        getFileName.setPositiveButton(R.string.export_save,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sumthin) {
                        final String fileEdit = txt.getText().toString();
                        if (e.fileExists(fileEdit)) {
                            AlertDialog.Builder fileExists = new AlertDialog.Builder(
                                    DiscoverActivity.this);
                            fileExists.setTitle(R.string.export_exists_title);
                            fileExists.setMessage(R.string.export_exists_msg);
                            fileExists.setPositiveButton(R.string.btn_yes,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which) {
                                            if (e.writeToSd(fileEdit)) {
                                                makeToast(R.string.export_finished);
                                            } else {
                                                export();
                                            }
                                        }
                                    });
                            fileExists.setNegativeButton(R.string.btn_no, null);
                            fileExists.show();
                        } else {
                            if (e.writeToSd(fileEdit)) {
                                makeToast(R.string.export_finished);
                            } else {
                                export();
                            }
                        }
                    }
                });
        getFileName.setNegativeButton(R.string.btn_discover_cancel, null);
        getFileName.show();
    }

    // private void updateList() {
    // adapter.clear();
    // listHosts();
    // }
    //
    // private void listHosts() {
    // for (String h : hosts) {
    // addHost(h);
    // }
    // }

    // private List<String> getSelectedHosts(){
    // List<String> hosts_s = new ArrayList<String>();
    // int listCount = list.getChildCount();
    // for(int i=0; i<listCount; i++){
    // CheckBox cb = (CheckBox) list.getChildAt(i).findViewById(R.id.list);
    // if(cb.isChecked()){
    // hosts_s.add(hosts.get(i));
    // }
    // }
    // return hosts_s;
    // }
    //    
    // private void setSelectedHosts(Boolean all){
    // int listCount = list.getChildCount();
    // for(int i=0; i<listCount; i++){
    // CheckBox cb = (CheckBox) list.getChildAt(i).findViewById(R.id.list);
    // if(all){
    // cb.setChecked(true);
    // } else {
    // cb.setChecked(false);
    // }
    // }
    // }

    private void makeToast(String msg) {
        Toast.makeText(getApplicationContext(), (CharSequence) msg,
                Toast.LENGTH_SHORT).show();
    }

    private void makeToast(int msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void setButtonOff(Button b) {
        b.setClickable(false);
        b.setEnabled(false);
        b.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.disabled, 0, 0);
    }

    private void setButtonOn(Button b, int drawable) {
        b.setClickable(true);
        b.setEnabled(true);
        b.setCompoundDrawablesWithIntrinsicBounds(0, drawable, 0, 0);
    }
}