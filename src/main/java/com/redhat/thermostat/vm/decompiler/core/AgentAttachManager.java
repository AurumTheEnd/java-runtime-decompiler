package com.redhat.thermostat.vm.decompiler.core;

import com.redhat.thermostat.vm.decompiler.data.VmManager;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Attach manager for agent contains utility methods and information about 
 * attach.
 */
public class AgentAttachManager {
 
    //private static final Logger logger = LoggingUtils.getLogger(AgentAttachManager.class); 
    private AgentLoader loader;
    private VmManager vmManager;

      
    public AgentAttachManager(VmManager vmManager){
        this.vmManager = vmManager;
        this.loader = new AgentLoader();
        
    }
    
     void setAttacher(AgentLoader loader) {
        this.loader = loader;
    }

    void setVmManager(VmManager vmManager) {
        this.vmManager = vmManager;
    }
  

    VmDecompilerStatus attachAgentToVm(String vmId, int vmPid)  {
        //logger.fine("Attaching agent to VM '" + vmPid + "'");
         int attachedPort = AgentLoader.INVALID_PORT;
        try {
            attachedPort = loader.attach(vmId, vmPid);
        } catch (Exception ex) {
            Logger.getLogger(AgentAttachManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (attachedPort == AgentLoader.INVALID_PORT) {
            //logger.warning("Failed to attach agent for VM '" + vmPid);
            return null;
        }
        VmDecompilerStatus status = new VmDecompilerStatus();
        status.setListenPort(attachedPort);
        status.setVmId(vmId);
        status.setTimeStamp(System.currentTimeMillis());
        vmManager.replaceVmDecompilerStatus(vmManager.getVmInfoByID(vmId), status);
        return status;
    }
}

    

