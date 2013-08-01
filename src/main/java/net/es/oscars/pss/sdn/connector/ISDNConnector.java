/**
 * 
 */
package net.es.oscars.pss.sdn.connector;

import java.io.IOException;
import java.util.HashMap;

import net.es.oscars.pss.sdn.common.SDNNode;

/**
 * This interface describes how OSCARS connects to a SDN 
 * controller to execute network operations.
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 */
public interface ISDNConnector {

	public enum ISDNConnectorResponse {
        SUCCESS,
        FAILURE,
        CONTROLLER_NOT_SET,
        ENTRY_NOT_FOUND;
	}
	
	/**
     * Set the connection end-point address this
     * connector should connect to
     * 
     * @param address
     * @throws IOException
     */
    public ISDNConnectorResponse setConnectionAddress(String address);
	
    /**
     * Request the SDN controller to install an entry on a switch
     * @param sdnNode a SDNNode object representing the switch or network device
     * @param entryParams
     * 		Contains sets of key,value pairs describing the entry.
     * 		Implementors should parse its values and build their implementation specific entry. 
     * @throws IOException
     */
    public ISDNConnectorResponse installEntry(SDNNode sdnNode, 
    		HashMap<String,Object> entryParams) throws IOException;
    
    /**
     * Request the SDN controller to delete an entry from a switch or network device
     * @param sdnNode a SDNNode object representing the switch or network device
     * @param entryParams
     * 		Contains sets of key,value pairs describing the entry.
     * 		Implementors should parse its values and build their implementation specific entry. 
     * @throws IOException
     */
    public ISDNConnectorResponse deleteEntry(SDNNode sdnNode,
    		HashMap<String,Object> entryParams) throws IOException;
    
	
}
