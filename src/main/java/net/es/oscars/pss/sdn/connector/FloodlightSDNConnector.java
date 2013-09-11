package net.es.oscars.pss.sdn.connector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.es.oscars.pss.sdn.openflow.OFRule;
import net.es.oscars.topoBridge.sdn.SDNCapability;
import net.es.oscars.topoBridge.sdn.SDNHop;
import net.es.oscars.topoBridge.sdn.SDNNode;
import net.es.oscars.topoBridge.sdn.SDNObject;

import org.apache.log4j.Logger;
import org.restlet.resource.ClientResource;

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

	private static ClientResource restStoreResource = null;
	private static ClientResource restDeleteResource = null;
	
	private void initRestResources() {
		try {
			
			if (restStoreResource == null)
				restStoreResource = new ClientResource(controller
						+ "/wm/staticflowentrypusher/json/store");
		
			if (restDeleteResource == null)
				restDeleteResource  = new ClientResource(controller
					+ "/wm/staticflowentrypusher/json/delete");
		}
		catch (Exception e) {
			restStoreResource = null;
			restDeleteResource = null;
			log.error("Could not create restlet resources!");
		}
		
	}
	
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
		initRestResources();
		hopRefCount = new HashMap<SDNHop, Integer>();
	}

	@Override
	public ISDNConnectorResponse setConnectionAddress(String address) {
		controller = address;
		
		restStoreResource = null;
		restDeleteResource = null;
		initRestResources();
		
		return ISDNConnectorResponse.SUCCESS;
	}

	private ISDNConnectorResponse setupL1Hop(SDNHop h, String circuitID) throws Exception {

		log.debug("start setupL1Hop");
		OFRule forwardEntry = new OFRule(),
			   reverseEntry = new OFRule();

		forwardEntry.put("in_port", h.getSrcPort());
		forwardEntry.put("output", h.getDstPort());

		reverseEntry.put("in_port", h.getDstPort());
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
		
		log.debug("end setupL1Hop");
		return ISDNConnectorResponse.SUCCESS;
	}

	private ISDNConnectorResponse teardownL1Hop(SDNHop h, String circuitID) throws Exception {
		OFRule entry = new OFRule();
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
			String circuitID, OFRule rule) throws Exception {
		log.debug("start setupL2Hop");

		OFRule forwardEntry = new OFRule(rule),
			   reverseEntry = rule.reverse();

		ISDNConnectorResponse response;
		
		String entryID = circuitID + ".match." + h.hashCode();

		if (!h.isEntryHop()) { // on entry hops, the match decides 
							   // what goes into the circuit
			forwardEntry.put("in_port", h.getSrcPort());
		}
		
		forwardEntry.put("output", h.getDstPort());
		forwardEntry.put("name", entryID + ".F");
		
		if (!h.isExitHop()) {
			reverseEntry.put("in_port", h.getDstPort());
		}
		reverseEntry.put("output", h.getSrcPort());
		reverseEntry.put("name", entryID + ".R");
		
		if ((h.isEntryHop() || h.isExitHop())
			&& rule.matchSize() < 1) {
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
		log.debug("end setupL2Hop");
		return ISDNConnectorResponse.SUCCESS;
	}
	
	private ISDNConnectorResponse teardownL2Hop(SDNHop h, String circuitID) throws Exception {
		OFRule entry = new OFRule();
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
			String circuitID, OFRule rule) throws Exception {
		
		if (h.isEntryHop() || h.isExitHop()) {
			throw new Exception("Can't bypass an Entry/Exit hop");
		}
		
		OFRule forwardEntry = new OFRule(rule),
			   reverseEntry = rule.reverse();
		
		ISDNConnectorResponse response;
		

		forwardEntry.put("in_port", h.getSrcPort());
		forwardEntry.put("output", h.getDstPort());
		
		reverseEntry.put("in_port", h.getDstPort());
		reverseEntry.put("output", h.getSrcPort());
		
		for (FLCircuitProto p : FLCircuitProto.values()) {
			String entryID = circuitID + "." + p.toString() +  h.hashCode();

			forwardEntry.put("name", entryID + ".F");
			forwardEntry.put("dl_type", p.value);

			reverseEntry.put("name", entryID + ".R");
			reverseEntry.put("dl_type", p.value);

			response = installEntry(h.getNode(), forwardEntry);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
	
			response = installEntry(h.getNode(), reverseEntry);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}

		return ISDNConnectorResponse.SUCCESS;
	}

	protected ISDNConnectorResponse teardownL2Bypass(SDNHop h, String circuitID) throws Exception {
		OFRule entry = new OFRule();
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
	public final class CircuitSetupOrder implements Comparator<SDNObject> {
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
			String circuitID) throws Exception {
		return setupCircuit(hops, circuitID, null);
	}

	@Override
	public ISDNConnectorResponse setupCircuit(List<SDNHop> hops,
			String circuitID, OFRule rule) throws Exception {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}

		// Get hop setup order.
		Collections.sort(hops, new CircuitSetupOrder());

		ISDNConnectorResponse response;
		
		for (SDNHop h : hops) {
			if (h.getNode().getId().matches("^11.*")) {
				if (hopRefCount.containsKey(h)) {
					log.debug(String.format(
							"Increasing hopRefcount for %s to %d", 
							h, hopRefCount.get(h) + 1));
					hopRefCount.put(h, 
							new Integer(hopRefCount.get(h) + 1));
					continue;
				}
				log.debug(String.format(
						"Setting hopRefcount for %s to %d", h, 1));
				hopRefCount.put(h, new Integer(1));
			}

			// Check for capabilities
			if (h.getCapabilities().contains(SDNCapability.L2) && rule != null) {
				if (!h.isEntryHop() && !h.isExitHop())
					response = setupL2Bypass(h, circuitID, rule);
				else
					response = setupL2Hop(h, circuitID, rule);
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
			String circuitID) throws Exception {

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
	 * @param rule
	 *            Contains sets of key,value pairs describing the entry.
	 */
	@Override
	public ISDNConnectorResponse installEntry(SDNNode node,
			OFRule rule) throws Exception {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}
		if (!rule.containsKey("name")) {
			return ISDNConnectorResponse.FAILURE;
		}

		try {
			String switchDPID = node.getId().replaceAll("\\.", ":");
			String jsonRequest = String.format("{\"switch\":\"%s\"", switchDPID);
			
			for (Map.Entry<String,String> entry : rule.floodlightEntrySet()) {
				  String key = entry.getKey();
				  String value = entry.getValue();
				  jsonRequest += String.format(",\"%s\":\"%s\"", key, value);
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
     * @param rule 
     * 		Contains sets of key,value pairs describing the entry.
     */
	@Override
	public ISDNConnectorResponse deleteEntry(SDNNode node,
			OFRule rule) throws Exception {
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
						.replaceAll("\\{name\\}",   rule.get("name"));
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
			ClientResource cr = restStoreResource;
			cr.post(request);
			// TODO: parse result
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	private void delete(String request) throws IOException {
		log.debug("Deleting entry: " + request);
		try {
			ClientResource cr = restDeleteResource;
			cr.post(request);
			// TODO: parse result
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	/**
	 * Setup circuit using GMPLS for L0/1 devices
	 * 
	 * WARNING: this is not supposed to be done in practice! GMPLS provisioning
	 * is orthogonal to SDN provisioning. The path that GMPLS provision could be
	 * different from the path that OSCARS compute. This method is here just to
	 * demonstrate the functionality, but it is not supposed to be used in
	 * practice.
	 * 
	 */
	@Override
	public ISDNConnectorResponse setupCircuitImplicitly(List<SDNHop> hops,
			String circuitID, OFRule rule) throws Exception {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}

		// Don't reorder hops, they will be setup sequentially
		// Collections.sort(hops, new CircuitSetupOrder());

		// The following code builds two lists, one with circuits supposed
		// to be setup implicitly using GMPL and another with regular hops
		// (explicitHops).
		List<List<SDNHop>> implicitCircuits = new ArrayList<List<SDNHop>>();
		List<SDNHop> explicitHops = new ArrayList<SDNHop>();

		SDNHop implicitHopPtr = null;
		List<SDNHop> implicitCircuitPtr = null;

		for (SDNHop h : hops) {
			List<SDNCapability> capabilities = h.getCapabilities();
			if (capabilities.size() == 1
					&& capabilities.contains(SDNCapability.L1)) {
				// L1 device
				if (implicitHopPtr == null) {
					implicitCircuitPtr = new ArrayList<SDNHop>();
					implicitCircuits.add(implicitCircuitPtr);
				}
				implicitCircuitPtr.add(h);
				implicitHopPtr = h;
			} else {
				// non L1 devices
				implicitHopPtr = null;
				explicitHops.add(h);
			}
		}

		ISDNConnectorResponse response;
		// Setup implicit connections
		for (List<SDNHop> implicitCircuit : implicitCircuits) {
			SDNHop startHop = implicitCircuit.get(0);
			SDNHop endHop = implicitCircuit.get(implicitCircuit.size() - 1);
			response = setupImplicitHop(startHop, endHop, circuitID);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}

		// Setup regular connections
		for (SDNHop h : explicitHops) {
			// Check for capabilities
			if (h.getCapabilities().contains(SDNCapability.L2) && rule != null) {
				if (!h.isEntryHop() && !h.isExitHop())
					response = setupL2Bypass(h, circuitID, rule);
				else
					response = setupL2Hop(h, circuitID, rule);
				if (response != ISDNConnectorResponse.SUCCESS)
					return response;
			} else {
				response = setupL1Hop(h, circuitID);
				if (response != ISDNConnectorResponse.SUCCESS)
					return response;
			}
		}

		return ISDNConnectorResponse.SUCCESS;
	}

	private ISDNConnectorResponse setupImplicitHop(SDNHop src, SDNHop dst,
			String circuitID) throws Exception {

		log.debug("start setupImplicitHop");

		String[] srcTribInfo = src.getSrcLink().split("/");
		String[] dstTribInfo = src.getDstLink().split("/");

		if (srcTribInfo.length < 2 || dstTribInfo.length < 2)
			throw new Exception("Invalid trib info in URN");

		String dl_src = srcTribInfo[0].replaceAll(".", ":"); // dst trib AID
		String dl_dst = dstTribInfo[0].replaceAll(".", ":"); // dst trib AID
		String nw_src = srcTribInfo[1]; // src router ID
		String nw_dst = dstTribInfo[1]; // dst router ID

		if (!dl_src.matches(OFRule.MAC_REGEX)
				|| !dl_dst.matches(OFRule.MAC_REGEX)
				|| !nw_src.matches(OFRule.IP_REGEX)
				|| !nw_dst.matches(OFRule.IP_REGEX))
			throw new Exception("Invalid trib format in URN");

		OFRule rule = new OFRule();

		rule.put("in_port", src.getSrcPort());
		rule.put("output", dst.getDstPort());
		rule.put("dl_src", dl_src);
		rule.put("dl_dst", dl_dst);
		rule.put("nw_src", nw_src);
		rule.put("nw_dst", nw_dst);

		String entryID = src.hashCode() + ".gmpls." + dst.hashCode();
		rule.put("name", entryID);

		ISDNConnectorResponse response;

		response = installEntry(src.getNode(), rule);
		if (response != ISDNConnectorResponse.SUCCESS)
			return response;

		log.debug("end setupImplicitHop");
		return ISDNConnectorResponse.SUCCESS;
	}

	private ISDNConnectorResponse teardownImplicitHop(SDNHop src, SDNHop dst,
			String circuitID) throws Exception {
		OFRule rule = new OFRule();

		String entryID = src.hashCode() + ".gmpls." + dst.hashCode();
		rule.put("name", entryID);

		ISDNConnectorResponse response;
		response = deleteEntry(src.getNode(), rule);
		if (response != ISDNConnectorResponse.SUCCESS)
			return response;

		return ISDNConnectorResponse.SUCCESS;
	}

	
    /**
	 * teardown circuit previously setupCircuitImplicitly
	 * 
	 * WARNING: this is not supposed to be done in practice! GMPLS provisioning
	 *          is orthogonal to SDN provisioning. The path that GMPLS provision
	 *          could be different from the path that OSCARS compute. This method
	 *          is here just to demonstrate the functionality, but it is not supposed
	 *          to be used in practice.
	 */
	@Override
	public ISDNConnectorResponse teardownCircuitImplicitly(List<SDNHop> hops,
			String circuitID) throws Exception {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}

		// Don't reorder hops, they will be setup sequentially
		// Collections.sort(hops, new CircuitSetupOrder());

		// The following code builds two lists, one with circuits supposed
		// to be setup implicitly using GMPL and another with regular hops
		// (explicitHops).
		List<List<SDNHop>> implicitCircuits = new ArrayList<List<SDNHop>>();
		List<SDNHop> explicitHops = new ArrayList<SDNHop>();

		SDNHop implicitHopPtr = null;
		List<SDNHop> implicitCircuitPtr = null;

		for (SDNHop h : hops) {
			List<SDNCapability> capabilities = h.getCapabilities();
			if (capabilities.size() == 1
					&& capabilities.contains(SDNCapability.L1)) {
				// L1 device
				if (implicitHopPtr == null) {
					implicitCircuitPtr = new ArrayList<SDNHop>();
					implicitCircuits.add(implicitCircuitPtr);
				}
				implicitCircuitPtr.add(h);
				implicitHopPtr = h;
			} else {
				// non L1 devices
				implicitHopPtr = null;
				explicitHops.add(h);
			}
		}

		
		ISDNConnectorResponse response;
		// Setup implicit connections
		for (List<SDNHop> implicitCircuit : implicitCircuits) {
			SDNHop startHop = implicitCircuit.get(0);
			SDNHop endHop = implicitCircuit.get(implicitCircuit.size() - 1);
			response = teardownImplicitHop(startHop, endHop, circuitID);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}

		// Setup regular connections
		for (SDNHop h : explicitHops) {
			response = teardownL1Hop(h, circuitID);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;

			response = teardownL2Hop(h, circuitID);
			if (response != ISDNConnectorResponse.SUCCESS)
				return response;
		}

		return ISDNConnectorResponse.SUCCESS;
		
	}

}
