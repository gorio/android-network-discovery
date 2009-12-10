package info.lamatricexiste.network.Utils;

import info.lamatricexiste.network.DiscoverActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

public class Export {

    private final String TAG = "Export";
    private List<String> hosts;
    private List<Long[]> hosts_ports;
    private List<String> hosts_haddr;
    private NetInfo net;

    public Export(Context ctxt, List<String> hosts, List<Long[]> hosts_ports,
            List<String> hosts_haddr) {
        this.hosts = hosts;
        this.hosts_ports = hosts_ports;
        this.hosts_haddr = hosts_haddr;
        net = new NetInfo(ctxt);
    }

    public boolean writeToSd(String file) {
        String xml = prepareXml();
        try {
            FileWriter f = new FileWriter(file);
            f.write(xml);
            f.flush();
            f.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        return false;
    }

    public boolean fileExists(String filename) {
        File file = new File(filename);
        return file.exists();
    }

    public String getFileName() {
        return Environment.getExternalStorageDirectory().toString()
                + "/discovery-" + net.getNetIp() + ".xml";
    }

    private String prepareXml() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<NetworkDiscovery>\r\n";
        // Network Information
        xml += "\t<info>\r\n"
                + "\t\t<date>"
                + (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"))
                        .format(new Date())
                + "</date>\r\n" // RFC 2822
                + "\t\t<network>" + net.getNetIp() + "/" + net.getNetCidr()
                + "</network>\r\n" + "\t\t<ssid>" + net.getSSID()
                + "</ssid>\r\n" + "\t\t<bssid>" + net.getBSSID()
                + "</bssid>\r\n" + "\t\t<ip>" + net.getIp() + "</ip>\r\n"
                + "\t</info>\r\n";

        // Hosts
        if (hosts != null) {
            xml += "\t<hosts>\r\n";
            for (int i = 0; i < hosts.size(); i++) {
                // Host info
                String host = hosts.get(i);
                xml += "\t\t<host value=\"" + host + "\" mac=\""
                        + hosts_haddr.get(i) + "\">\r\n";
                // Ports
                Long[] portsL = hosts_ports.get(i);
                if (portsL != null) {
                    CharSequence[] ports = DiscoverActivity.preparePort(portsL); //FIXME: Move to ScanPort
                    for (int j = 0; j < ports.length; j++) {
                        xml += "\t\t\t<port>" + ports[j] + "</port>\r\n";
                    }
                }
                xml += "\t\t</host>\r\n";
            }
            xml += "\t</hosts>\r\n";
        }

        xml += "</NetworkDiscovery>";
        return xml;
    }

}