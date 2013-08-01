package net.es.oscars.pss.sdn.common;

import java.util.ArrayList;
import java.util.List;

import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneHopContent;

import net.es.oscars.utils.topology.NMWGParserUtil;

public class SDNLink {
	private SDNNode node;
	
	// TODO: add getters and setters	
	public String srcNode;
	public String dstNode;
	public String srcPort;
	public String dstPort;
	public String srcLink;
	public String dstLink; 
	
	public SDNNode getNode() {
		return node;
	}

	public void setNode(SDNNode node) {
		this.node = node;
	}

	public SDNLink(String srcURN, String dstURN) {
		this.srcNode = NMWGParserUtil.getURNPart(srcURN, NMWGParserUtil.NODE_TYPE);
		this.dstNode = NMWGParserUtil.getURNPart(dstURN, NMWGParserUtil.NODE_TYPE);
		this.srcPort = NMWGParserUtil.getURNPart(srcURN, NMWGParserUtil.PORT_TYPE);
		this.dstPort = NMWGParserUtil.getURNPart(dstURN, NMWGParserUtil.PORT_TYPE);
		this.srcLink = NMWGParserUtil.getURNPart(srcURN, NMWGParserUtil.LINK_TYPE);
		this.dstLink = NMWGParserUtil.getURNPart(dstURN, NMWGParserUtil.LINK_TYPE);
	}
	
	/**
	 * Extract links from a CtrlPlaneHopContent object and returns them as a list of SDNLinks
	 * 
	 * @param hops
	 * @return list of SDNLinks
	 */
    public static List<SDNLink> extractSDNLinks(List<CtrlPlaneHopContent> hops) {
    	List<SDNLink> links = new ArrayList<SDNLink>();
    	String src = null;
   	
        try {
        	for (CtrlPlaneHopContent hop : hops) {
	        	String dst  = hop.getLink().getId();

	        	if (src == null) {
	        		src = dst;
	        		continue;
	        	}
	        	
	        	if (NMWGParserUtil.compareURNPart(src, dst, NMWGParserUtil.NODE_TYPE)) {
	        		SDNLink l = new SDNLink(src, dst);

	        		// TODO: check for capabilities
	        		// TODO: change the design to avoid having multiple objects representing the same node
	        		l.setNode(new SDNNode(l.srcNode.replaceAll("\\.", ":"))); 
	        		links.add(l);
	        	}
	        	
	        	src = dst;
	        	

	        }

        }
        catch (Exception e) {
        	return null; 
        }
        
		return links;
    }
	
}
