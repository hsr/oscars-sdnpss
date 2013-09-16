/**
 * 
 */
package net.es.oscars.pss.sdn.connector;

import java.util.List;

import net.es.oscars.pss.sdn.openflow.OFRule;
import net.es.oscars.topoBridge.sdn.SDNHop;
import net.es.oscars.topoBridge.sdn.SDNNode;

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
     * @throws Exception
     */
    public ISDNConnectorResponse setConnectionAddress(String address);
	
    /**
     * Request the SDN controller to install an entry on a switch
     * @param sdnNode a SDNNode object representing the switch or network device
     * @param rule
     * 		Contains sets of key,value pairs describing the entry.
     * 		Implementors should parse its values and build their implementation specific entry. 
     * @throws Exception
     */
    public ISDNConnectorResponse installEntry(SDNNode sdnNode, 
    		OFRule rule) throws Exception;
    
    /**
     * Request the SDN controller to delete an entry from a switch or network device
     * @param sdnNode a SDNNode object representing the switch or network device
     * @param rule
     * 		Contains sets of key,value pairs describing the entry.
     * 		Implementors should parse its values and build their implementation specific entry. 
     * @throws Exception
     */
    public ISDNConnectorResponse deleteEntry(SDNNode sdnNode,
    		OFRule rule) throws Exception;
    

    /**
	 * SDN Connector specific implementation of a circuit setup action with the given 
	 * list of hops and a string with an OpenFlow rule that specify OFMatch and 
	 * OFActions for this circuit.
	 * 
	 * @param hops a list of SDNHops (List<SDNHop>) that describes each hop in the circuit
	 * @param rule OpenFlow rule 
     * @throws Exception 
	 */
	public ISDNConnectorResponse setupCircuit(List<SDNHop> hops,
			String circuitID, OFRule rule) throws Exception;

	/**
     * SDN Connector implementation of a circuit teardown action with the given 
     * list of hops.
     * 
     * @param hops a list of SDNHops (List<SDNHops>) that describes each hop in the circuit
     * @throws Exception
     */	
	public ISDNConnectorResponse teardownCircuit(List<SDNHop> hops,
			String circuitID) throws Exception;



    
    /**
	 * Setup circuit using GMPLS for L0/1 devices
	 * 
	 * WARNING: this is not supposed to be done in practice! GMPLS provisioning
	 *          is orthogonal to SDN provisioning. The path that GMPLS provision
	 *          could be different from the path that OSCARS compute. This method
	 *          is here just to demonstrate the functionality, but it is not supposed
	 *          to be used in practice.
	 * 
	 * @param hops a list of SDNHops (List<SDNHop>) that describes each hop in the circuit
	 * @param rule OpenFlow rule 
     * @throws Exception
	 */
	public ISDNConnectorResponse setupCircuitImplicitly(List<SDNHop> hops,
			String circuitID, OFRule rule) throws Exception;


    /**
	 * teardown circuit previously setupCircuitImplicitly
	 * 
	 * WARNING: this is not supposed to be done in practice! GMPLS provisioning
	 *          is orthogonal to SDN provisioning. The path that GMPLS provision
	 *          could be different from the path that OSCARS compute. This method
	 *          is here just to demonstrate the functionality, but it is not supposed
	 *          to be used in practice.
	 * 
	 * @param hops a list of SDNHops (List<SDNHop>) that describes each hop in the circuit
     * @throws Exception
	 */
	public ISDNConnectorResponse teardownCircuitImplicitly(List<SDNHop> hops,
			String circuitID) throws Exception;
	
}
