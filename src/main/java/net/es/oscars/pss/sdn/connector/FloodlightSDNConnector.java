package net.es.oscars.pss.sdn.connector;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

import net.es.oscars.pss.sdn.common.SDNLink;
import net.es.oscars.pss.sdn.common.SDNNode;

/**
 * Implements the Floodlight SDN connector: the interface that OSCARS use to talk to Floodlight
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 *
 */
public class FloodlightSDNConnector implements ISDNConnector {
	private String controller = null;
	private static final Logger log = Logger.getLogger(FloodlightSDNConnector.class.getName());

	/**
	 * Floodlight circuits on emulated L2 switches aren't purely in->out port mappings.  
	 */
	private enum FLCircuitProto {
        IP("0x800"),
        ARP("0x806");

        private final String value;
        FLCircuitProto (String v) {
            value = v;
        }
	}

	public FloodlightSDNConnector() {
		controller = null;
	}

	public FloodlightSDNConnector(String address) {
		controller = address;
	}
	
	@Override
	public ISDNConnectorResponse setConnectionAddress(String address) {
		// TODO: check if we can establish a connection to the controller
		controller = address;
		return ISDNConnectorResponse.SUCCESS;
	}
	
    /**
     * Requests Floodlight staticflowentrypusher to install a given entry. The resulting
     * call generated by this method seems like this:
     * 
     * curl -s -d '{
     * 	"switch": "00:00:00:00:00:00:00:07",
     * 	"name":"00:00:00:00:00:00:00:07.test.f",
     * 	"ether-type":"0x800",
     * 	"cookie":"0",
     * 	"priority":"32768",
     * 	"ingress-port":"1",
     * 	"active":"true",
     * 	"actions":"output=3"
     * }'
     * http://URL/wm/staticflowentrypusher/json
     * 
     * @param node the SDNNode that will receive the new entry
     * @param entryParams 
     * 		Contains sets of key,value pairs describing the entry.
     */
	@Override
	public ISDNConnectorResponse installEntry(SDNNode node,
			HashMap<String, Object> entryParams) throws IOException {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}
		
    	String baseJSONRequest = "{"
    			+ "\"switch\":\"{switch}\","
        		+ "\"name\":\"{name}\","
        		+ "\"ether-type\":\"{ethtype}\","
        		+ "\"cookie\":\"0\","
        		+ "\"priority\":\"32768\","
        		+ "\"ingress-port\":\"{srcPort}\","
        		+ "\"active\":\"true\","
        		+ "\"actions\":\"output={dstPort}\""
        		+ "}";
    	
    	try {
	 		String request = baseJSONRequest
						.replaceAll("\\{switch\\}", node.getId())
						.replaceAll("\\{name\\}",   (String) entryParams.get("id"))
						.replaceAll("\\{ethtype\\}",(String) entryParams.get("proto"))
						.replaceAll("\\{srcPort\\}",(String) entryParams.get("src"))
						.replaceAll("\\{dstPort\\}",(String) entryParams.get("dst"));
	 		// TODO: check return
			write("curl -s -d " + request);
    	}
    	catch (Exception e) {
    		return ISDNConnectorResponse.FAILURE;
    	}
		return ISDNConnectorResponse.SUCCESS;
	}
	
	@Override
	public ISDNConnectorResponse setupCircuit(List<SDNLink> links,
			String circuitID) throws IOException {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}
		
		for(SDNLink l : links) {
			HashMap<String,Object> forwardEntry = new HashMap<String,Object>(), 
								   reverseEntry = new HashMap<String,Object>();
			
			forwardEntry.put("src",  l.srcPort);
			forwardEntry.put("dst",  l.dstPort);
			
			reverseEntry.put("src",  l.dstPort);
			reverseEntry.put("dst",  l.srcPort);
			
			for (FLCircuitProto p : FLCircuitProto.values()) {
				String entryID = circuitID + "." + p.toString() + "." + l.getNode().getId();
				
				forwardEntry.put("id", entryID + ".F");
				forwardEntry.put("proto", p.value);

				reverseEntry.put("id", entryID + ".R");
				reverseEntry.put("proto", p.value);

				// TODO: check return
				installEntry(l.getNode(), forwardEntry);
				installEntry(l.getNode(), reverseEntry);
			}
		}
		
		return ISDNConnectorResponse.SUCCESS;
	}

	@Override
	public ISDNConnectorResponse teardownCircuit(List<SDNLink> links,
			String circuitID) throws IOException {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}
		
		for(SDNLink l : links) {
			HashMap<String,Object> entry = new HashMap<String,Object>();
			
			entry.put("node", l.srcNode.replaceAll("\\.", ":"));
			
			for (FLCircuitProto p : FLCircuitProto.values()) {
				String entryID = circuitID + "." + p.toString() + "." + l.getNode().getId();
				
				// TODO: check return
				entry.put("id", entryID + ".F");
				deleteEntry(l.getNode(), entry);
				entry.put("id", entryID + ".R");
				deleteEntry(l.getNode(), entry);
			}
		}
		
		return ISDNConnectorResponse.SUCCESS;
	}
	
    /**
     * Requests Floodlight staticflowentrypusher to delete a given entry. The resulting
     * call generated by this method seems like this:
     * 
     *  curl -X DELETE -d '{
     * 	"switch": "00:00:00:00:00:00:00:07",
     * 	"name":"00:00:00:00:00:00:00:07.test.f",
     * }'
     * http://URL/wm/staticflowentrypusher/json
     * 
     * @param node the SDNNode that has the entry to be deleted
     * @param entryParams 
     * 		Contains sets of key,value pairs describing the entry.
     */
	@Override
	public ISDNConnectorResponse deleteEntry(SDNNode node,
			HashMap<String, Object> entryParams) throws IOException {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}
		
    	String baseJSONRequest = "{"
    			+ "\"name\":\"{name}\","
    			+ "\"switch\":\"{switch}\""
    			+ "}";
    	
    	try {
	 		String request = baseJSONRequest
	 					.replaceAll("\\{switch\\}", node.getId())
						.replaceAll("\\{name\\}",   (String) entryParams.get("id"));
	 		// TODO: check return
	 		write("curl -X DELETE -d " + request);
    	}
    	catch (Exception e) {
    		return ISDNConnectorResponse.FAILURE;
    	}
		return ISDNConnectorResponse.SUCCESS;
	}
	
    private void write(String request) {
    	// TODO: rewrite this using jersey/REST    	
    	Runtime rt = Runtime.getRuntime();
    	try {
    		log.info(request + " " + controller + "/wm/staticflowentrypusher/json");
    		rt.exec(request + " " + controller + "/wm/staticflowentrypusher/json");
    	}
    	catch (Exception e) {
    		return;
    	}
	}
}
