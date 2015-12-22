package no.polaric.aprsd.http;

// import org.apache.log4j.Logger;
import org.simpleframework.http.Cookie;
import org.simpleframework.http.Request;
import org.simpleframework.http.socket.*;
import java.io.IOException;



public class ChatRoomClient implements FrameListener {
   
   private final ChatRoom _room;
   private final FrameChannel _chan; 
   private final long _uid;
   
   
   public ChatRoomClient(ChatRoom room, FrameChannel ch, long uid) {
      _room = room;
      _chan = ch;
      _uid = uid;
   }
   
   
   public void send(Frame frame) throws IOException
      { _chan.send(frame); }
   
   
   public void close() throws IOException
      { _chan.close(); }
      
   
   public void onFrame(Session socket, Frame frame) {
      FrameType type = frame.getType();
      String text = frame.getText();
      Request request = socket.getRequest();
      
      if(type == FrameType.TEXT) {
          Frame replay = new DataFrame(type, "(" + _uid + ") " +text);
          _room.distribute(_uid, replay);
      } 
      System.out.println("onFrame (" + type + ")");
   }

   
   public void onError(Session socket, Exception cause) {
      System.out.println("onError (" + cause + ")");
      cause.printStackTrace(System.out);
   }

   
   public void onClose(Session session, Reason reason) {
      System.out.println("onClose (" + reason + ")");
   }
}
