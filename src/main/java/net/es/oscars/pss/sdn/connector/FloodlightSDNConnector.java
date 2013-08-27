package net.es.oscars.pss.sdn.connector;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.restlet.resource.ClientResource;

import net.es.oscars.topoBridge.sdn.SDNCapability;
import net.es.oscars.topoBridge.sdn.SDNHop;
import net.es.oscars.topoBridge.sdn.SDNNode;
import net.es.oscars.topoBridge.sdn.SDNObject;
import net.es.oscars.pss.sdn.openflow.OFMatch;

/**
 * Implements the Floodlight SDN connector: the interface that OSCARS use to
 * talk to Floodlight
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 * 
 */
public class FloodlightSDNConnector implements ISDNConnector {
	private String controller = null;
	private static Map<SDNHop, Integer> hopRefCount = null;
	private static final Logger log = Logger
			.getLogger(FloodlightSDNConnector.class.getName());

	/**
	 * Floodlight circuits on emulated L2 switches aren't purely in->out port
	 * mappings.
	 */
	private enum FLCircuitProto {
		IP("0x800"), ARP("0x806");

		private final String value;

		FLCircuitProto(String v) {
			value = v;
		}
	}

	public FloodlightSDNConnector() {
		controller = null;
		hopRefCount = new HashMap<SDNHop, Integer>();
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

	private ISDNConnectorResponse setupL1Hop(SDNHop h, String circuitID) throws IOException {

		HashMap<String, Object> forwardEntry = new HashMap<String, Object>(),
								reverseEntry = new HashMap<String, Object>();

		forwardEntry.put("ingress-port", h.getSrcPort());
		forwardEntry.put("output", h.getDstPort());

		reverseEntry.put("ingress-port", h.getDstPort());
		reverseEntry.put("output", h.getSrcPort());

		ISDNConnectorResponse response;

		
		String entryID = h.hashCode() + "." + h.getNode().getId();
		

		forwardEntry.put("name", entryID + ".F");
		response = installEntry(h.getNode(), forwardEntry);
		if (response != ISDNConnectorResponse.SUCCESS)
			return response;

		reverseEntry.put("name", entryID + ".R");
		response = installEntry(h.getNode(), reverseEntry);
		if (response != ISDNConnectorResponse.SUCCESS)
			return response;
		
		return ISDNConnectorResponse.SUCCESS;
	}

	private ISDNConnectorResponse teardownL1Hop(SDNHop h, String circuitID) throws IOException {
		HashMap<String, Object> entry = new HashMap<String, Object>();
		ISDNConnectorResponse response;
	
		String entryID = h.hashCode() + "." + h.getNode().getId(); 
		entry.put("name", entryID + ".F");
		response = deleteEntry(h.getNode(), entry);
		if (response != ISDNConnectorResponse.SUCCESS)
			return response;
	
		entry.put("name", entryID + ".R");
		response = deleteEntry(h.getNode(), entry);
		if (response != ISDNConnectorResponse.SUCCESS)
			return response;
		
		return ISDNConnectorResponse.SUCCESS; 
	}

	private ISDNConnectorResponse setupL2Hop(SDNHop h,
			String circuitID, Map<String, String> floodlightMatch) throws IOException {
		
		HashMap<String, Object> forwardEntry = new HashMap<String, Object>(),
								reverseEntry = new HashMap<String, Object>();
		
		ISDNConnectorResponse response;
		
		String entryID = circuitID + ".match." + h.hashCode();

		if (!h.isEntryHop()) { // on entry hops, the match decides 
							   // what goes into the circuit
			forwardEntry.put("ingress-port", h.getSrcPort());
		}
		
		forwardEntry.put("output", h.getDstPort());
		forwardEntry.put("name", entryID + ".F");
		
		if (!h.isExitHop()) {
			reverseEntry.put("ingress-port", h.getDstPort());
		}
		reverseEntry.put("output", h.getSrcPort());
		reverseEntry.put("name", entryID + ".R");
		
		if (floodlightMatch != null) {
			for (Map.Entry<String,String> match : floodlightMatch.entrySet()) {
				  String key = match.getKey();
				  String value = match.getValue();
				  if (key != "ingress-port") {
					  forwardEntry.put(key, value);
					  reverseEntry.put(key, value);
				  }
			}
		}
		else if (h.isEntryHop() || h.isExitHop()) {
			// if no match was specified, and this is an Entry or 
			// Exit hop, then we have nothing to do here. Let the
			// end (entry and exit) switches decided what is going 
			// through the circuit.
			log.debug("No match found, skipping entry/exit hop");
			return ISDNConnectorResponse.SUCCESS;
		}
		
		if (!h.isExitHop()) { // Exit hops don't have forward entries
			response = installEntry(h.getNode(), forwardEntry);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}

		if (!h.isEntryHop()) { // Entry hops don't have reverse entries
			response = installEntry(h.getNode(), reverseEntry);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}
		
		return ISDNConnectorResponse.SUCCESS;
	}
	
	private ISDNConnectorResponse teardownL2Hop(SDNHop h, String circuitID) throws IOException {
		HashMap<String, Object> entry = new HashMap<String, Object>();
		ISDNConnectorResponse response;
	
		String entryID = circuitID + ".match." + h.hashCode();
		entry.put("name", entryID + ".F");
		response = deleteEntry(h.getNode(), entry);
		if (response != ISDNConnectorResponse.SUCCESS)
			return response;
		
		entry.put("name", entryID + ".R");
		response = deleteEntry(h.getNode(), entry);
		if (response != ISDNConnectorResponse.SUCCESS)
			return response;
		
		return ISDNConnectorResponse.SUCCESS; 
	}

	protected ISDNConnectorResponse setupL2Bypass(SDNHop h,
			String circuitID, Map<String, String> floodlightMatch) throws IOException {
		
		if (h.isEntryHop() || h.isExitHop()) {
			throw new IOException("Can't bypass an Entry/Exit hop");
		}
		
		HashMap<String, Object> forwardEntry = new HashMap<String, Object>(),
								reverseEntry = new HashMap<String, Object>();
		
		ISDNConnectorResponse response;
		

		forwardEntry.put("ingress-port", h.getSrcPort());
		forwardEntry.put("output", h.getDstPort());
		
		reverseEntry.put("ingress-port", h.getDstPort());
		reverseEntry.put("output", h.getSrcPort());
		
		if (floodlightMatch != null) {
			for (Map.Entry<String,String> match : floodlightMatch.entrySet()) {
				  String key = match.getKey();
				  String value = match.getValue();
				  if (key != "ingress-port") {
					  forwardEntry.put(key, value);
					  reverseEntry.put(key, value);
				  }
			}
		}

		for (FLCircuitProto p : FLCircuitProto.values()) {
			String entryID = circuitID + "." + p.toString() +  h.hashCode();

			forwardEntry.put("name", entryID + ".F");
			forwardEntry.put("ether-type", p.value);

			reverseEntry.put("name", entryID + ".R");
			reverseEntry.put("ether-type", p.value);

			response = installEntry(h.getNode(), forwardEntry);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
	
			response = installEntry(h.getNode(), reverseEntry);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}

		return ISDNConnectorResponse.SUCCESS;
	}

	protected ISDNConnectorResponse teardownL2Bypass(SDNHop h, String circuitID) throws IOException {
		HashMap<String, Object> entry = new HashMap<String, Object>();
		ISDNConnectorResponse response;
	
		for (FLCircuitProto p : FLCircuitProto.values()) {
			String entryID = circuitID + "." + p.toString() +  h.hashCode();
	
			entry.put("name", entryID + ".F");
			response = deleteEntry(h.getNode(), entry);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
	
			entry.put("name", entryID + ".R");
			response = deleteEntry(h.getNode(), entry);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}
	
		return ISDNConnectorResponse.SUCCESS; 
	}

	/**
	 * Compares two SDNObjects by capabilities. This is used to define the order
	 * in which cross connects will be created. Links with lower capabilities
	 * will be configured first. For example, if a reservation has Optical
	 * Devices that can forward using L1 only and L2 switches that can forward
	 * based on MAC addresses, the Optical hops will have higher priority over
	 * L2 switches. The total order, by priority, assumed is: 
	 * 
	 * L1 > MPLS > VLAN > L2 > L3
	 */
	public final class LinkSetupOrder implements Comparator<SDNObject> {
		@Override
		public int compare(SDNObject link1, SDNObject link2) {
			SDNCapability[] priority = { SDNCapability.L1, SDNCapability.MPLS,
					SDNCapability.VLAN, SDNCapability.L2, SDNCapability.L3 };
	
			int link1MaxCap = 0, link2MaxCap = 0, i = 0;
			for (SDNCapability c : priority) {
				if (link1.hasCapability(c))
					link1MaxCap = i;
				if (link2.hasCapability(c))
					link2MaxCap = i;
				i++;
			}
			return link1MaxCap - link2MaxCap;
		}
	}

	@Override
	public ISDNConnectorResponse setupCircuit(List<SDNHop> hops,
			String circuitID) throws IOException {
		return setupCircuit(hops, circuitID, null);
	}

	@Override
	public ISDNConnectorResponse setupCircuit(List<SDNHop> hops,
			String circuitID, String ofMatch) throws IOException {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}

		Map<String, String> floodlightMatch = null;
		if (ofMatch != null) {
			try {
				floodlightMatch = OFMatch.parseOFMatch(ofMatch);
			} catch (Exception e) {
				log.warn("Invalid match: " + e.getMessage());
			}
		}

		// Get hop setup order.
		Collections.sort(hops, new LinkSetupOrder());

		ISDNConnectorResponse response;
		
		for (SDNHop h : hops) {
			if (h.getNode().getId().matches("^11.*")) {
				if (hopRefCount.containsKey(h)) {
					log.debug(String.format("Increasing hopRefcount for %s to %d", 
							h, hopRefCount.get(h) + 1));
					hopRefCount.put(h, new Integer(hopRefCount.get(h) + 1));
					continue;
				}
				log.debug(String.format("Setting hopRefcount for %s to %d", h, 1));
				hopRefCount.put(h, new Integer(1));
			}

			// Check for capabilities
			if (h.getCapabilities().contains(SDNCapability.L2)) {
				response = setupL2Hop(h, circuitID, floodlightMatch);
				if (response != ISDNConnectorResponse.SUCCESS)
					return response;
			}
			else {
				response = setupL1Hop(h, circuitID);
				if (response != ISDNConnectorResponse.SUCCESS)
					return response;
			}
		}

		return ISDNConnectorResponse.SUCCESS;
	}

	@Override
	public ISDNConnectorResponse teardownCircuit(List<SDNHop> hops,
			String circuitID) throws IOException {

		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}

		ISDNConnectorResponse response;
		for (SDNHop h : hops) {
			if (h.getNode().getId().matches("^11.*")) {
				if (!hopRefCount.containsKey(h)) {
					log.warn("FloodlightSDNConnector: where this hop "
							+ h + " came from?");
				} else if (hopRefCount.get(h) > 1) {
					hopRefCount.put(h, new Integer(hopRefCount.get(h) - 1));
					log.debug(String.format("Decreasing hopRefcount for %s to %d", 
							h, hopRefCount.get(h)));
					continue;
				} else {
					log.debug(String.format("Removing hopRefcount for %s", h));
					hopRefCount.remove(h);
				}
			}

			response = teardownL1Hop(h, circuitID);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
			
			response = teardownL2Hop(h, circuitID);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}

		return ISDNConnectorResponse.SUCCESS;
	}

	// @formatter:off
	/**
	 * Requests Floodlight staticflowentrypusher to install a given entry. The
	 * resulting call generated by this method seems like this:
	 * 
	 * {
	 * 	"switch": "00:00:00:00:00:00:00:07",
	 * 	"name":"00:00:00:00:00:00:00:07.test.f",
	 * 	"ether-type":"0x800",
	 * 	"cookie":"0",
	 * 	"priority":"32768",
	 * 	"ingress-port":"1",
	 * 	"active":"true",
	 * 	"actions":"output=3"
	 * }
	 * 
	 * @param node
	 *            the SDNNode that will receive the new entry
	 * @param entryParams
	 *            Contains sets of key,value pairs describing the entry.
	 */
	@Override
	public ISDNConnectorResponse installEntry(SDNNode node,
			HashMap<String, Object> entryParams) throws IOException {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}
		if (!entryParams.containsKey("name")) {
			return ISDNConnectorResponse.FAILURE;
		}

		try {
			String switchDPID = node.getId().replaceAll("\\.", ":");
			String jsonRequest = String.format("{\"switch\":\"%s\"", switchDPID);
			
			for (Map.Entry<String,Object> entry : entryParams.entrySet()) {
				  String key = entry.getKey();
				  String value = (String) entry.getValue();
				  if (key == "output") {
					  jsonRequest += String.format(",\"actions\":\"output=%s\"", value);
				  }
				  else {
					  jsonRequest += String.format(",\"%s\":\"%s\"", key, value);
				  }
				}
			jsonRequest += "}";
		
	 		store(jsonRequest);
		}
		catch (Exception e) {
			log.warn("Couldn't install entry: " + e.getMessage());
			return ISDNConnectorResponse.FAILURE;
		}
		return ISDNConnectorResponse.SUCCESS;
	}
	
	/**
     * Requests Floodlight staticflowentrypusher to delete a given entry. The resulting
     * call generated by this method seems like this:
     * 
     *  {
     * 	"switch": "00:00:00:00:00:00:00:07",
     * 	"name":"00:00:00:00:00:00:00:07.test.f",
     *  }
     * 
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

    	String switchDPID = node.getId().replaceAll("\\.", ":");
    	try {
	 		String request = baseJSONRequest
	 					.replaceAll("\\{switch\\}", switchDPID)
						.replaceAll("\\{name\\}",   (String) entryParams.get("name"));
	 		delete(request);
    	}
    	catch (Exception e) {
    		log.warn("Couldn't delete entry: " + e.getMessage());
    		return ISDNConnectorResponse.FAILURE;
    	}
		return ISDNConnectorResponse.SUCCESS;
	}
	// @formatter:on

	private void store(String request) throws IOException {
		log.debug("Storing entry: " + request);
		try {
			ClientResource cr = new ClientResource(controller
					+ "/wm/staticflowentrypusher/json/store");
			cr.post(request);
			// TODO: parse result
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	private void delete(String request) throws IOException {
		log.debug("Deleting entry: " + request);
		try {
			ClientResource cr = new ClientResource(controller
					+ "/wm/staticflowentrypusher/json/delete");
			cr.post(request);
			// TODO: parse result
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}
}
