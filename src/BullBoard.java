package no.polaric.aprsd;
import java.util.*;
 
public class BullBoard implements MessageProcessor.MessageHandler {
 
 
    public static class Bull {
        public char bullid; 
        public Station sender; 
        public String text; 
        public Bull(char id, Station s, String txt)
            { bullid=id; sender=s; text=txt; }
    }
    
    
    public class SubBoard {
        private SortedMap<String, Bull[]> _map = new TreeMap();
        private int _size = 10;
        private char _start='0', _end='9'; 
        private String name;
        
        public void put(Bull b) { 
            String sender = b.sender.getIdent();
            if (b.bullid<_start || b.bullid>_end)
                return; 
                
            _api.log().debug("BullBoard", "Bulletin update: "+sender+" > "+name+"["+b.bullid+"]");
            if (!_map.containsKey(sender))
                _map.put(sender, new Bull[_size]);
                
            _map.get(sender)[b.bullid - _start] = b;  // Replace
            
            /* FIXME: Don't notify if no real change */
            _api.getWebserver().notifyUser
               ("SYSTEM", new ServerAPI.Notification
                 ("chat", "system", ((b.bullid >= '0' && b.bullid <= '9') ? "Bulletin: " : "Announcement: ")+
                  sender+" > "+name+"["+b.bullid+"]: "+b.text, new Date(), 60*24));
        }
        
        
        public Bull[] get(String sender) 
            { return _map.get(sender); }
        
        
        public Collection<Bull[]> getAll() 
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
    private String _grpsel = ".*";
    
  
  
    public BullBoard(ServerAPI api, MessageProcessor p) {
        _api = api;
        _grpsel = api.getProperty("bulletin.groups", ".*");
        p.subscribe("BLN", this, false);
    }
    
    
    
  
    /**
     * Handle incoming message. 
     */
    public boolean handleMessage(Station sender, String recipient, String text) {
    
        if (recipient.length() < 4)
            recipient = "BLN0";
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
        }
        
        /* General bulletins */ 
        else if (bullid >= '0' && bullid <= '9')
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
 
 
 
    public SubBoard getBulletins()
        { return _bulletins; }
 
 
    public SubBoard getAnnouncements()
        { return _announcements; }
 
 
    public SubBoard getBulletinGroup(String groupid) 
        { return _groups.get(groupid); }
 
 
    public Set<String> getGroupNames()
        { return _groups.keySet(); }
}
 
