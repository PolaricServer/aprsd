/* 
 * Copyright (C) 2022-23 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
 
 
package no.polaric.aprsd.http;
import java.util.*;



public class NodeWsApi<T> {

    private NodeWs _children;
    private String _nodeid;
    private Class<T> _cls;
    private Handler<String> _handler;
    private Handler<T> _chandler;
    private Map<String, NodeWsClient> _servers;
    
    
    public interface Handler<T> {
        public void recv(String nodeid, T obj);
    }

    

    @SuppressWarnings("unchecked")
    public NodeWsApi(String nodeid, NodeWs srv, Class<T> cls) {
        _servers = new HashMap<String,NodeWsClient>();
        _children = srv;
        _nodeid = nodeid;
        _cls = cls;
        
        _handler = new Handler<String>() {
            public void recv(String nodeid, String obj) {
                if (_chandler != null)
                    _chandler.recv(nodeid, (T) ServerBase.fromJson(obj, _cls));
            }
        };
        
        if (_children != null)
            _children.setHandler(_handler);
    }
    
    
    
    public List<String> getNodes() {
        List<String> list = new ArrayList<String>();
        for (String x : _servers.keySet())
            list.add(x);
        for (String x : _children.getSubscribers())
            list.add(x);
        return list;
    }
    
    
    
    
    public void addServer(String nodeid, NodeWsClient srv) {
        System.out.println("*** NodeWsApi.AddServer: "+nodeid);
        _servers.put(nodeid, srv);
        srv.subscribe(_nodeid);
        srv.setHandler(_handler);
    }
    
    
    
    public void rmNode(String nodeid) {
        var srv = _servers.get(nodeid);
        if (srv != null) {
            srv.unsubscribe();
            srv.close();
        }
        _servers.remove(nodeid);
        _children.removeSubscriber(nodeid);
    }
    
    
    
    public void setHandler(Handler<T> h) {
        _chandler = h;
    }
    
    

    public boolean put(String nodeid, T obj) {
        NodeWsClient srv = _servers.get(nodeid);
        if (srv != null && srv.isConnected())
            return srv.put(obj);
        else
            return _children.put(nodeid, obj);
    }
    
    
    
    
    public boolean isConnected(String nodeid) {
        NodeWsClient s = _servers.get(nodeid);
        return ( (s != null && s.isConnected())  
            || _children.getSubscribers().contains(nodeid) );
    }
    
    
}



