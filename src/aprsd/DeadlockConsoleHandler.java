/* 
 * Deadlock detection
 * Based on example: 
 * http://korhner.github.io/java/multithreading/detect-java-deadlocks-programmatically/
 */
 
 
package no.polaric.aprsd;
import java.lang.management.*;
import java.util.concurrent.*;
import java.util.*;

 
public class DeadlockConsoleHandler implements DeadlockDetector.Handler {
    int count = 0; 
    @Override
    public void handleDeadlock(final ThreadInfo[] deadlockedThreads) {
        if (count-- > 0)
            return; 
        count = 10;
        if (deadlockedThreads != null) {
            System.out.println();
            System.out.println("******* WARNING: Deadlock detected! *******");
      
            Map<Thread, StackTraceElement[]> stackTraceMap = Thread.getAllStackTraces();
            for (ThreadInfo threadInfo : deadlockedThreads) {
                if (threadInfo != null) {
                    for (Thread thread : Thread.getAllStackTraces().keySet()) {
                        if (thread.getId() == threadInfo.getThreadId()) {
                            System.out.println(threadInfo.toString().trim());
                
                            for (StackTraceElement ste : thread.getStackTrace()) {
                                System.out.println("\t" + ste.toString().trim());
                            }
                        }
                    }
                }
            }
            System.out.println();
        }
    }
}
