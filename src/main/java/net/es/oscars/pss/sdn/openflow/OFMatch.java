package net.es.oscars.pss.sdn.openflow;

import java.util.Map;
import java.util.HashMap;

/**
 * OFMatch parser. Used by sdnPSS to parse OpenFlow input
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 * 
 */
public abstract class OFMatch {
	private static String IP_REGEX = "" + "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])(\\/(\\d|[1-2]\\d|3[0-2]))$";

	private static String MAC_REGEX = "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$";
	private static String INT_REGEX = "^[1-9]\\d*$";
	private static String HEX_REGEX = "^(0x|)[0-9A-Fa-f]*$";

	private static Map<String, String> OFMatchRegex = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;
		{
			put("in_port", INT_REGEX);
			put("dl_dst", MAC_REGEX);
			put("dl_src", MAC_REGEX);
			put("dl_type", HEX_REGEX);
			put("dl_vlan", INT_REGEX);
			put("dl_vlan_pcp", INT_REGEX);
			put("nw_dst", IP_REGEX);
			put("nw_src", IP_REGEX);
			put("nw_proto", INT_REGEX);
			put("nw_tos", INT_REGEX);
			put("tp_dst", INT_REGEX);
			put("tp_src", INT_REGEX);
		}
	};

	private static Map<String, String> OFMatch2Floodlight = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;
		{
			put("in_port", "ingress-port");
			put("dl_dst", "dst-mac");
			put("dl_src", "src-mac");
			put("dl_type", "ether-type");
			put("dl_vlan", "vlan-id");
			put("dl_vlan_pcp", "vlan-priority");
			put("nw_dst", "dst-ip");
			put("nw_src", "src-ip");
			put("nw_proto", "protocol");
			put("nw_tos", "tos-bits");
			put("tp_dst", "dst-port");
			put("tp_src", "src-port");
		}
	};

	public static Map<String, String> parseOFMatch(String match)
			throws Exception {
		Map<String, String> matchMap = new HashMap<String, String>();

		try {
			String[] tokens = match.split(",");
	
			for (String token : tokens) {
				String[] values = token.toLowerCase().split("=");
	
				if (OFMatchRegex.containsKey(values[0])) {
					if (values[1].matches(OFMatchRegex.get(values[0])))
						matchMap.put(OFMatch2Floodlight.get(values[0]), values[1]);
					else
						throw new Exception(String.format("Invalid match %s=%s",
								values[0], values[1]));
				}
			}
		}
		catch (Exception e) {
			throw new Exception(String.format("Invalid match %s", match));
		}
		
		if (matchMap.size() > 0) {
			if (matchMap.containsKey("dst-port") || matchMap.containsKey("src-port")) {
				if (!matchMap.containsKey("protocol"))
					matchMap.put("protocol", "6"); // if no L4 specified, assume TCP
				if (!matchMap.containsKey("ether-type"))
					matchMap.put("ether-type", "0x800"); // if no L3 specified, assume IP
			}
			return matchMap;
		}
		return null;
	}
}
