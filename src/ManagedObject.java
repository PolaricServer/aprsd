
/* Plugin (in version 1.1+ could inherit this */
package no.polaric.aprsd;

/**
 * Service that can be activated or deactivated. 
 */
public interface ManagedObject
{
    /** Start the service */
   public void activate(ServerAPI a);
   
    /** stop the service */
   public void deActivate();
   
    /** Return true if service is running */
   public boolean isActive();
}
