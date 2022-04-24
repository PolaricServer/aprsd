package no.polaric.aprsd.http;
import java.util.*;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.*;


public class ZeroConf 
{ 

    private List<JmDNS> _ifaces  = new ArrayList<JmDNS>();;
    
    public ZeroConf()
    {
        try {
            Enumeration<NetworkInterface> b = NetworkInterface.getNetworkInterfaces();
            while( b.hasMoreElements()) {
                NetworkInterface nif = b.nextElement(); 
                if (!nif.isUp() || nif.isLoopback() || nif.isPointToPoint() 
                      || nif.isVirtual() || nif.getName().matches("docker.*"))
                    continue;

                for ( InterfaceAddress f : nif.getInterfaceAddresses()) {
                    InetAddress addr = f.getAddress();
                    if ( addr.isSiteLocalAddress() && !addr.isMulticastAddress() && !addr.isLinkLocalAddress() ) {
                        JmDNS dd = JmDNS.create(addr);
                        System.out.println("*** ADDR: "+addr+ " IF: "+nif);
                        _ifaces.add(dd);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public void registerMdns(String type, String name, int port, String attrs) 
    {
        try {
            for (JmDNS d : _ifaces) {
                ServiceInfo serviceInfo = ServiceInfo.create(type, name, port, attrs);
                d.registerService(serviceInfo);
                System.out.println("*** Register service ok");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }        
    }
    
    public void unregisterMdns() {
        try {
            for (JmDNS d : _ifaces)
                d.unregisterAllServices();
        } catch (Exception e) {
            e.printStackTrace();
        }      
    }
}
