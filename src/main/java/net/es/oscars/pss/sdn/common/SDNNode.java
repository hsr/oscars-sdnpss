package net.es.oscars.pss.sdn.common;

import java.util.List;

public class SDNNode { // Should this extend o.o.s.n.t.c.CtrlPlaneNodeContent ?
	/**
	 * Enumeration of all capabilities of a SDNNode
	 * 
	 * VLAN: can differentiate traffic using VLAN
	 * MPLS: can differentiate traffic using MPLS labels
	 * IP: 	 can differentiate traffic based on packet IP addresses
	 * L2:   can differentiate traffic based on MAC addresses
	 * L1:   can take forwarding decisions based on in/out port mappings 
	 * 
	 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
	 *
	 */
	public enum SDNNodeCapability {
        VLAN,
        MPLS,
        IP,
        L2,
        L1
	}
	
	private List<SDNNodeCapability> capabilities;
	private List<SDNLink> links = null;
	
	private String id;
	
	public SDNNode(String id) {
		this.id = id;
		this.capabilities = null;
		return;
	}

	public String getId() {
		return id;
	}

	public List<SDNNodeCapability> getCapabilities() {
		return capabilities;
	}

	public void addCapability(SDNNodeCapability c) {
		this.capabilities.add(c);
	}
	
	// TODO: change the design to use links from nodes
	public List<SDNLink> getLinks() {
		return links;
	}

	public void setLinks(List<SDNLink> links) {
		this.links = links;
	}


}
