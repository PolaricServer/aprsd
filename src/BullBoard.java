package no.polaric.aprsd;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.*;


public class BullBoard implements MessageProcessor.MessageHandler {
     
    public class Update {}

    public class Bull {
        public char bullid; 
        public String sender; 
        public String text; 
        public Date time; 
        public Bull(char id, Station s, String txt)
            { bullid=id; sender=s.getIdent(); text=txt; time=new Date(); }
            // FIXME: Time should be in UTC !!!!!
    }
    
    public class SenderBulls {
        public String sender;
        public Bull[] bulls;
        
        public Bull get(int i)
            { return bulls[i]; }
        public void update(int i, Bull b)
            { bulls[i] = b; }
        public SenderBulls(String s, int n)
            { sender=s; bulls=new Bull[n]; }
    }
    
    
    public class SubBoard {
        private SortedMap<String, SenderBulls> _map = new TreeMap();
        private int _size = 10;
        private char _start='0', _end='9'; 
        private String name;
        
        /**
         * Add a bulletin to the subboard.
         */
        public void put(Bull b) { 
            if (b.bullid<_start || b.bullid>_end)
                return; 
                
            _api.log().debug("BullBoard", "Bulletin update: "+b.sender+" > "+name+"["+b.bullid+"]: "+b.text);
            if (!_map.containsKey(b.sender))
                _map.put(b.sender, new SenderBulls(b.sender,_size));    
                
            _api.getWebserver().getPubSub().put("bullboard", null);
            Bull orig = _map.get(b.sender).get(b.bullid - _start);  
            if (orig != null && orig.sender.equals(b.sender) && orig.text.equals(b.text)) {
                orig.time = new Date();
                return; 
            }
            
            /* If there is a change, replace and notify */
            _map.get(b.sender).update(b.bullid - _start, b); 
            _api.getWebserver().notifyUser
               ("SYSTEM", new ServerAPI.Notification
                 ("chat", ((b.bullid >= '0' && b.bullid <= '9') ? "Bulletin" : "Announcement"),
                  b.sender+" > "+name+"["+b.bullid+"]: "+b.text, new Date(), 60*1));
        }
        
        
        /** 
         * Cleanup. Remove bulls older than xx hours
         */
        public synchronized void cleanUp(int hours) {
            boolean remove = false; 
            for (Object s : _map.keySet().toArray()) {
                SenderBulls bls = _map.get((String) s); 
                boolean empty = true;
                for (int i=0; i<bls.bulls.length; i++) {
                    if (bls.bulls[i] != null && 
                            bls.bulls[i].time.getTime() + hours*60*60*1000 < (new Date()).getTime()) {
                        _api.log().debug("BullBoard", "Cleanup - removing bull: "+bls.bulls[i].sender);
                        bls.bulls[i] = null;
                        remove = true; 
                    }
                    if (bls.bulls[i] != null)
                        empty = false;
                }
                if (empty)
                    _map.remove(s);
            }
            if (remove)
                _api.getWebserver().getPubSub().put("bullboard", null);
        }
        
        
        public boolean isEmpty() 
            { return (_map.size() == 0); }
        
        public Bull[] get(String sender) 
            { return _map.get(sender).bulls; }
        
        public Set<String> getSenders() 
            { return _map.keySet(); }
        
        public Collection<SenderBulls> getAll() 
            { return _map.values(); }
        
        
        public SubBoard(String name, boolean numeric) {
            if (!numeric) {
                _size = 26; 
                _start='A'; 
                _end='Z';
            }
            this.name = name;
        }
    }
    
    
    private ServerAPI _api; 
    private SubBoard _bulletins = new SubBoard("", true);
    private SubBoard _announcements = new SubBoard("", false); 
    private SortedMap<String, SubBoard> _groups = new TreeMap(); 
    private String _grpsel;
    private String _senders;
    
    // FIXME
    public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
       
    
    // FIXME: Move this to ServerAPI
    private final ScheduledExecutorService scheduler =
       Executors.newScheduledThreadPool(1);
  
  
  
    public BullBoard(ServerAPI api, MessageProcessor p) {
        _api = api;
        _grpsel = api.getProperty("bulletin.groups", ".*");
        _senders = api.getProperty("bulletin.senders", ".*");
        int ttl_bull = _api.getIntProperty("bulletin.ttl.bull", 12);
        int ttl_ann = _api.getIntProperty("bulletin.ttl.ann", 24);
        
        p.subscribe("BLN", this, false);
        
        /** Shedule cleanup each 10 minutes. */
        scheduler.scheduleAtFixedRate( () -> 
            {
                try {
                    _bulletins.cleanUp(ttl_bull); 
                    _announcements.cleanUp(ttl_ann); 
                    for (Object sbn : _groups.keySet().toArray()) {
                        SubBoard sb = getBulletinGroup((String) sbn); 
                        sb.cleanUp(ttl_bull);
                        if (sb.isEmpty())
                            _groups.remove(sbn);
                    }
                }
                catch (Exception e) {
                    _api.log().warn("BullBoard", "Exception in scheduled action: "+e);
                    e.printStackTrace(System.out);
                }
            } ,20, 10, MINUTES); 
    }
    
    
    
  
    /**
     * Handle incoming message. 
     */
    public boolean handleMessage(Station sender, String recipient, String text) {
    
        if (recipient.length() < 4)
            recipient = "BLN0";
        if (!recipient.matches("BLN[0-9A-Z][A-Z]*"))
            return false; 
        char bullid = recipient.charAt(3);
        Bull bull = new Bull(bullid, sender, text); 
        
        /* Group bulletins */
        if (recipient.length() > 4) {
            if (bullid>'9' || bullid < '0') {
                _api.log().warn("BullBoard", "Non-numeric group bulletin-id: "
                    +sender.getIdent()+">"+recipient);
                return false;
            }
            if (!recipient.matches("BLN?("+_grpsel+")"))
                return true;
            String groupid = recipient.substring(4); 
            if (!_groups.containsKey(groupid))
                _groups.put(groupid, new SubBoard(groupid, true));
            _groups.get(groupid).put(bull);
            return true; 
        }
        
        if (!sender.getIdent().matches(_senders))
            return true;
        
        /* General bulletins */ 
        if (bullid >= '0' && bullid <= '9')
            _bulletins.put(bull);
        /* General announcements */
        else if (bullid >= 'A' && bullid <= 'Z')
            _announcements.put(bull);
        else {   
            _api.log().warn("BullBoard", "Invalid bulletin format: "
                +sender.getIdent()+">"+recipient);
            return false;
        }
        return true;
    }
 
 
    /**
     * Get general bulletins subboard 
     */
    public SubBoard getBulletins()
        { return _bulletins; }
 
 
    /**
     * Get announcements subboard.
     */
    public SubBoard getAnnouncements()
        { return _announcements; }
 
 
    /**
     * Get bulletin group. 
     */
    public SubBoard getBulletinGroup(String groupid) 
    { 
        if (groupid == null || groupid.equals(""))
            return getBulletins();
        else if (groupid.equals("_B_")) 
            return getBulletins(); 
        else if (groupid.equals("_A_")) 
            return getAnnouncements(); 
        else 
            return _groups.get(groupid); 
    }
 
 
    /**
     * Get names of all bulletin groups. 
     */
    public Set<String> getGroupNames()
        { return _groups.keySet(); }

}
 
