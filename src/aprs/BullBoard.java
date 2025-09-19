
package no.polaric.aprsd;
import no.arctic.core.*;
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.channel.*;
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
        public Date time, refresh; 
        public long resend = 0;
        public boolean local = false; 
        
        public Bull()
            {}
        public Bull(char id, String s, String txt)
            { bullid=id; sender=s; text=txt; time=new Date(); }
        public Bull(char id, Station s, String txt)
            { bullid=id; sender=s.getIdent(); text=txt; time=new Date(); }
            // FIXME: Time should be in UTC !!!!!
    }
    
    public class SenderBulls {
        public String sender;
        public Bull[] bulls;
        public int nbulls = 0;
        public boolean local = false;
        
        public Bull get(int i)
            { return bulls[i]; }
        public void update(int i, Bull b)
            { if (bulls[i]==null)
                 nbulls++; 
              bulls[i] = b; 
            }
        public void remove(int i)
            { nbulls--; bulls[i] = null; }
        public SenderBulls(String s, int n)
            { sender=s; bulls=new Bull[n]; }
    }
    
    
    public class SubBoard {
        private SortedMap<String, SenderBulls> _map = new TreeMap<String, SenderBulls>();
        private int _size = 10;
        private char _start='0', _end='9'; 
        private String name;
        
        
        /** 
         * Post a bulletin to the system. 
         */
        public void post(String sender, char bullid, String text) {
            SenderBulls mybulls = _map.get(sender);
            
            // check if mybulls exist
            if (mybulls == null) {
                /* If not, create them */
                mybulls = new SenderBulls(sender, _size); 
                _map.put(sender, mybulls);
            }
                 
            /* Check bullid and compute index */
            if (bullid < _start || bullid > _end)
                return;
            int index = (bullid - _start); 

            /* If text is null or "", remove the bull */
            if (text == null || text.equals("")) {
                mybulls.remove(index);
                if (mybulls.nbulls == 0)
                    _map.remove(sender);
                _api.getWebserver().pubSub().put("bullboard", null);
                return;
            }
            
            /* If bull already exists for index, reuse it */
            var bull = mybulls.bulls[index];
            var bexist = false; 
            if (bull == null) 
                bull = new Bull(bullid, sender, text); 
            else
                bexist = true; 
                
            /* If bull didn't exist or text change, send it to APRS */
            if (bull.text==null || !bexist || !bull.text.equals(text)) {
                bull.text = text;
                sendBulletin(bull, name);
            }
 
            /* Add to list */ 
            put(bull);
        }
        
        
        /**
         * Add a bulletin to the subboard.
         */
        public void put(Bull b) { 
            if (b.bullid<_start || b.bullid>_end)
                return; 
                
            if (b.refresh == null)
                b.refresh = b.time;
                
            _api.log().debug("BullBoard", "Bulletin update: "+b.sender+" > "+name+"["+b.bullid+"]: "+b.text);
            if (!_map.containsKey(b.sender))
                _map.put(b.sender, new SenderBulls(b.sender,_size));    

            /* If there is no change, return */
            Bull orig = _map.get(b.sender).get(b.bullid - _start);  
            if (orig != null && orig.sender.equals(b.sender) && orig.text.equals(b.text)) {
                _api.log().debug("BullBoard", "NO CHANGE to bulletin: "+b.sender+" > "+name+"["+b.bullid+"]: "+b.text);
                orig.refresh = new Date();
                return; 
            }
            
            /* If there is a change, replace and notify */
            _map.get(b.sender).update(b.bullid - _start, b); 
            _api.getWebserver().pubSub().put("bullboard", null);
                        
            /* FIXME: allow users to subscribe to notifications? */
            if (false)
                _api.getWebserver().notifyUser
                    ("SYSTEM", new ServerConfig.Notification
                        ("chat", ((b.bullid >= '0' && b.bullid <= '9') ? "Bulletin" : "Announcement"),
                        b.sender+" > "+name+"["+b.bullid+"]: "+b.text, new Date(), 60*1));
        }
        
        
        /** 
         * Cleanup. Remove bulls older than xx hours
         */
        public synchronized void cleanUp(int hours) {
            boolean remove = false; 
            /* For each sender */
            for (Object s : _map.keySet().toArray()) {
                SenderBulls bls = _map.get((String) s); 
                boolean empty = true;
                for (int i=0; i<bls.bulls.length; i++) {
                    if (bls.bulls[i] != null && 
                        (bls.bulls[i].refresh.getTime() + hours*60*60*1000 < (new Date()).getTime())
                    ) {
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
                _api.getWebserver().pubSub().put("bullboard", null);
        }
        
        
        public boolean isEmpty() 
            { return (_map.size() == 0); }
        
        public Bull[] get(String sender) {
            if (_map.get(sender) == null)
                return null;
            return _map.get(sender).bulls; 
        }
        
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
    
    
    private AprsServerConfig _api; 
    private SubBoard _bulletins = new SubBoard("_B_", true);
    private SubBoard _announcements = new SubBoard("_A_", false); 
    private SortedMap<String, SubBoard> _groups = new TreeMap<String, SubBoard>(); 
    private String _grpsel;
    private String _senders;
    private String _myCall;
    private MessageProcessor _msg;
    
    
    
    // FIXME
    public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
       
    
    // FIXME: Move this to ServerConfig
    private final ScheduledExecutorService scheduler =
       Executors.newScheduledThreadPool(1);
  
  
  
    public BullBoard(AprsServerConfig api, MessageProcessor p) {
        _api = api;
        _grpsel = _api.getProperty("bulletin.groups", ".*");
        _senders = _api.getProperty("bulletin.senders", ".*");
        int ttl_bull = _api.getIntProperty("bulletin.ttl.bull", 8);
        int ttl_ann = _api.getIntProperty("bulletin.ttl.ann", 24);
        _myCall = _api.getProperty("message.mycall", "").toUpperCase();
        if (_myCall.length() == 0)
           _myCall = _api.getProperty("default.mycall", "NOCALL").toUpperCase();
           
        p.subscribe("BLN", this, false);
        _msg = p;
        
        /* Shedule cleanup each 10 minutes. */
        scheduler.scheduleAtFixedRate( () -> 
            {
                try {
                    _bulletins.cleanUp(ttl_bull); 
                    _announcements.cleanUp(ttl_ann); 
                    
                    /* Do cleanup for each group. Remove group if empty */
                    for (Object sbn : _groups.keySet().toArray()) {
                        SubBoard sb = getBulletinGroup((String) sbn); 
                        sb.cleanUp(ttl_bull);
                        if (sb.isEmpty())
                            _groups.remove(sbn);
                    }
                    
                    /* Re-send messages (if necessary) */
                    resend();
                }
                catch (Exception e) {
                    _api.log().warn("BullBoard", "Exception in scheduled action: "+e);
                    e.printStackTrace(System.out);
                }
            } ,5, 5, MINUTES); 
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
     * Send bulletin to APRS.
     */
    public void sendBulletin(Bull b, String group) {
        _api.log().info("BullBoard", "SEND BULL to "+group+": "+b.text);
        String dest = "BLN"+b.bullid;

        if (group != null && !group.matches("_[AB]_"))
            dest += group;
        
        _msg.sendRawMessage(b.sender, b.text, dest);
        b.local = true; 
        b.refresh = new Date();
    }
 
 
 
    /**
     * Go through all messages and se if any sent messages need to be re-sent.
     */
    public void resend() {
        resendGroup(getAnnouncements());
        resendGroup(getBulletins());
        for (SubBoard grp : _groups.values())
            resendGroup(grp);
    }
    
    
    
    /**
     * Resend messages in a group. Announcements after 10 minutes, other bulls after 5 min. 
     * Time interval doubles each time up to 2 hours. 
     */
    public void resendGroup(SubBoard grp) {
        for (SenderBulls sb : grp.getAll())
            for (Bull b : sb.bulls) {
                if (b==null || !b.local)
                    continue;
                if (b.resend == 0)
                    b.resend = (grp.name.equals("_A_") ? 10 : 5) * 60 *1000;
                if (b.resend > 2*60*60*1000)
                    b.resend = 2*60*60*1000; /* Max 2 hours */
                
                long now = (new Date()).getTime();
                if (now - b.refresh.getTime() + 60*1000 >= b.resend)
                    sendBulletin(b, grp.name);
                b.resend *= 2;
            }
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
 
 
    public SubBoard createBulletinGroup(String groupid)
    {
        var x = _groups.get(groupid);
        if (x == null)
            _groups.put(groupid, new SubBoard(groupid, true));
        return _groups.get(groupid);
    }
    
 
    /**
     * Get names of all bulletin groups. 
     */
    public Set<String> getGroupNames()
        { return _groups.keySet(); }

}
 
