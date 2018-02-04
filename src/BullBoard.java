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
    
    
    private Bull[] _bulletins = new Bull[10];
    private Bull[] _announcements = new Bull[26]; 
    private Map<String, Bull[]> _groups = new HashMap(); 
    
    
  
  
    public BullBoard(ServerAPI api, MessageProcessor p) {
        p.subscribe("BLN", this, false);
    }
    
    
    
  
    /**
     * Handle incoming message. 
     */
    public boolean handleMessage(Station sender, String recipient, String text) {
        char bullid = recipient.charAt(3);
        Bull bull = new Bull(bullid, sender, text); 
        
        if (recipient.length() > 4) {
            if (bullid>'Z' || bullid < 'A') 
                return false;
            String groupid = recipient.substring(4); 
            if (!_groups.containsKey(groupid))
                _groups.put(groupid, new Bull[10]);
            _groups.get(groupid)[bullid-'0'] = bull;
        }
        else
            if (bullid>'0' && bullid<'9')
                _bulletins[bullid-'0'] = bull;
            else if (bullid>'A' && bullid<'Z')
                _announcements[bullid-'A'] = bull;
            else    
                return false;
        return true;
    }
 
 
 
    public Bull[] getBulletins()
        { return _bulletins; }
 
 
    public Bull[] getAnnouncements()
        { return _announcements; }
 
 
    public Bull[] getBulletinGroup(String groupid) 
        { return _groups.get(groupid); }
 
 
}
 
