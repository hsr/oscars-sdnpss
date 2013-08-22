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
import net.es.oscars.topoBridge.sdn.SDNLink;
import net.es.oscars.topoBridge.sdn.SDNNode;
import net.es.oscars.topoBridge.sdn.SDNObject;

/**
 * Implements the Floodlight SDN connector: the interface that OSCARS use to
 * talk to Floodlight
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 * 
 */
public class FloodlightSDNConnector implements ISDNConnector {
	private String controller = null;
	private static Map<SDNLink, Integer> linkRefCount = null;
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
		linkRefCount = new HashMap<SDNLink, Integer>();
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
	public ISDNConnectorResponse setupCircuit(List<SDNLink> links,
			String circuitID) throws IOException {
		if (controller == null) {
			return ISDNConnectorResponse.CONTROLLER_NOT_SET;
		}

		// Get hop setup order.
		Collections.sort(links, new LinkSetupOrder());

		ISDNConnectorResponse response;

		for (SDNLink l : links) {
			if (linkRefCount.containsKey(l)) {
				linkRefCount.put(l, new Integer(linkRefCount.get(l) + 1));
				continue;
			}
			linkRefCount.put(l, new Integer(1));

			HashMap<String, Object> forwardEntry = new HashMap<String, Object>(),
									reverseEntry = new HashMap<String, Object>();

			forwardEntry.put("src", l.getSrcPort());
			forwardEntry.put("dst", l.getDstPort());

			reverseEntry.put("src", l.getDstPort());
			reverseEntry.put("dst", l.getSrcPort());

			for (FLCircuitProto p : FLCircuitProto.values()) {
				String entryID = circuitID + "." + p.toString() + "."
						+ l.getNode().getId();

				forwardEntry.put("id", entryID + ".F");
				forwardEntry.put("proto", p.value);

				reverseEntry.put("id", entryID + ".R");
				reverseEntry.put("proto", p.value);

				response = installEntry(l.getNode(), forwardEntry);
				if (response != ISDNConnectorResponse.SUCCESS)
					return ISDNConnectorResponse.FAILURE;

				response = installEntry(l.getNode(), reverseEntry);
				if (response != ISDNConnectorResponse.SUCCESS)
					return ISDNConnectorResponse.FAILURE;
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

		ISDNConnectorResponse response;

		for (SDNLink l : links) {
			HashMap<String, Object> entry = new HashMap<String, Object>();

			if (!linkRefCount.containsKey(l)) {
				log.warn("FloodlightSDNConnector: where this link came from?");
			} else if (linkRefCount.get(l) > 1) {

				linkRefCount.put(l, new Integer(linkRefCount.get(l) - 1));
				continue;
			} else {
				linkRefCount.remove(l);
			}

			entry.put("node", l.getSrcNode().replaceAll("\\.", ":"));

			for (FLCircuitProto p : FLCircuitProto.values()) {
				String entryID = circuitID + "." + p.toString() + "."
						+ l.getNode().getId();

				entry.put("id", entryID + ".F");
				response = deleteEntry(l.getNode(), entry);
				if (response != ISDNConnectorResponse.SUCCESS)
					return ISDNConnectorResponse.FAILURE;

				entry.put("id", entryID + ".R");
				response = deleteEntry(l.getNode(), entry);
				if (response != ISDNConnectorResponse.SUCCESS)
					return ISDNConnectorResponse.FAILURE;
			}
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
	 		store(request);
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
    	
    	try {
	 		String request = baseJSONRequest
	 					.replaceAll("\\{switch\\}", node.getId())
						.replaceAll("\\{name\\}",   (String) entryParams.get("id"));
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
		try {
			ClientResource cr = new ClientResource(controller
					+ "/wm/staticflowentrypusher/json/store");
			cr.post(request);
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	private void delete(String request) throws IOException {
		ClientResource cr = new ClientResource(controller
				+ "/wm/staticflowentrypusher/json/delete");

		try {
			cr.post(request);
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}
}
