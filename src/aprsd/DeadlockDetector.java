/* 
 * Deadlock detection
 * Based on example: 
 * http://korhner.github.io/java/multithreading/detect-java-deadlocks-programmatically/
 */

package no.polaric.aprsd;
import java.lang.management.*;
import java.util.concurrent.*;




public class DeadlockDetector {

  private final Handler deadlockHandler;
  private final long period;
  private final TimeUnit unit;
  private final ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
  private final ScheduledExecutorService scheduler = 
      Executors.newScheduledThreadPool(1);
  
  
  public interface Handler {
     void handleDeadlock(final ThreadInfo[] deadlockedThreads);
  }


  final Runnable deadlockCheck = new Runnable() {
    @Override
    public void run() {
      long[] deadlockedThreadIds = DeadlockDetector.this.mbean.findDeadlockedThreads();
    
      if (deadlockedThreadIds != null) {
        ThreadInfo[] threadInfos = 
        DeadlockDetector.this.mbean.getThreadInfo(deadlockedThreadIds);
      
        DeadlockDetector.this.deadlockHandler.handleDeadlock(threadInfos);
      }
    }
  };
  
  
  public DeadlockDetector
     (final Handler deadlockHandler, final long period, final TimeUnit unit) {
    this.deadlockHandler = deadlockHandler;
    this.period = period;
    this.unit = unit;
  }
  
  
  public void start() {
    this.scheduler.scheduleAtFixedRate(
       this.deadlockCheck, this.period, this.period, this.unit);
  }
}


