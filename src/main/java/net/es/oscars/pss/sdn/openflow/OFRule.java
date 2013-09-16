package net.es.oscars.pss.sdn.openflow;

import java.util.Map;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

/**
 * OFRule represents an entry in an OpenFlow table. It is basically a map of
 * OpenFlow key,value pairs that get inserted in an OpenFlow switch table. It is
 * used by sdnPSS to parse OF match input and OF actions and to generate the
 * final Floodlight request.
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 * 
 *         TODO: create a new child class FloodlightOFRule that extends this,
 *         but that override the entrySet() method to return Floodlight specific
 *         tokens using Floodlight's specific translation.
 */
public class OFRule extends HashMap<String, String> {
	private static final long serialVersionUID = 5955370395586986465L;

	public static String IP_REGEX = "" + "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])(\\/(\\d|[1-2]\\d|3[0-2]))$";

	public static String MAC_REGEX = "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$";
	private static String INT_REGEX = "^[1-9]\\d*$";
	private static String HEX_REGEX = "^(0x|)[0-9A-Fa-f]*$";
	private static String EMPTY_REGEX = "^$";
	private static String ANY_REGEX = "^.*$";

	private static String OF_STR_ACTIONS = "actions";
	private static String OF_STR_SET_VLAN = "set-vlan-id";
	private static String OF_STR_STRIP_VLAN = "strip-vlan";
	private static String OF_STR_DL_VLAN = "dl_vlan";
	private static String OF_STR_OUTPUT = "output";
	private static String OF_STR_NW_DST = "nw_dst";
	private static String OF_STR_NW_SRC = "nw_src";

	// TODO: replace the strings on the maps below by class static strings
	private static Map<String, String> OFACTIONS_REGEX = new HashMap<String, String>() {
		private static final long serialVersionUID = -4517039142009168646L;
		{
			// actions
			put(OF_STR_OUTPUT, INT_REGEX);
			put(OF_STR_SET_VLAN, INT_REGEX);
			put(OF_STR_STRIP_VLAN, EMPTY_REGEX);
		}
	};

	private static Map<String, String> OFRULE_REGEX = new HashMap<String, String>() {
		private static final long serialVersionUID = 4335160907174232213L;
		{
			put("in_port", INT_REGEX);
			put("dl_dst", MAC_REGEX);
			put("dl_src", MAC_REGEX);
			put("dl_type", HEX_REGEX);
			put(OF_STR_DL_VLAN, INT_REGEX);
			put("dl_vlan_pcp", INT_REGEX);
			put(OF_STR_NW_DST, IP_REGEX);
			put(OF_STR_NW_SRC, IP_REGEX);
			put("nw_proto", INT_REGEX);
			put("nw_tos", INT_REGEX);
			put("tp_dst", INT_REGEX);
			put("tp_src", INT_REGEX);
			// actions
			put(OF_STR_OUTPUT, INT_REGEX);
			put(OF_STR_SET_VLAN, INT_REGEX);
			put(OF_STR_STRIP_VLAN, EMPTY_REGEX);
			// name
			put("name", ANY_REGEX);
			put(OF_STR_ACTIONS, ANY_REGEX);
		}
	};

	private static Map<String, String> OFRULE_REVERSE = new HashMap<String, String>() {
		private static final long serialVersionUID = -8409540222829712936L;

		{
			put("in_port", OF_STR_OUTPUT);
			put(OF_STR_OUTPUT, "in_port");
			put("dl_dst", "dl_src");
			put("dl_src", "dl_dst");
			put(OF_STR_DL_VLAN, OF_STR_SET_VLAN);
			put(OF_STR_SET_VLAN, OF_STR_DL_VLAN);

			put("tp_dst", "tp_src");
			put("tp_src", "tp_dst");
			put("nw_tos", "nw_tos");
			put(OF_STR_NW_DST, OF_STR_NW_SRC);
			put(OF_STR_NW_SRC, OF_STR_NW_DST);
			put("dl_type", "dl_type");
			put("nw_proto", "nw_proto");
			put(OF_STR_STRIP_VLAN, "");
			put("dl_vlan_pcp", "dl_vlan_pcp");
		}
	};

	private static Map<String, String> OFRULE_TO_FLOODLIGHT = new HashMap<String, String>() {
		private static final long serialVersionUID = -8776965333516612787L;

		{
			put("in_port", "ingress-port");
			put("dl_dst", "dst-mac");
			put("dl_src", "src-mac");
			put("dl_type", "ether-type");
			put(OF_STR_DL_VLAN, "vlan-id");
			put("dl_vlan_pcp", "vlan-priority");
			put(OF_STR_NW_DST, "dst-ip");
			put(OF_STR_NW_SRC, "src-ip");
			put("nw_proto", "protocol");
			put("nw_tos", "tos-bits");
			put("tp_dst", "dst-port");
			put("tp_src", "src-port");
			put("name", "name");
			put(OF_STR_ACTIONS, OF_STR_ACTIONS);
		}
	};

	public Map<String, String> actions = null;

	public OFRule() {
		super();
		this.actions = new HashMap<String, String>();
	}

	public OFRule(String match) throws Exception {
		super();
		this.actions = new HashMap<String, String>();

		if (!OFRule.isValidOFRuleString(match)) {
			throw new Exception("Invalid match");
		}
		this.putAll(OFRule.parseOFRule(match));
	}

	public OFRule(String match, String action) throws Exception {
		super();
		this.actions = new HashMap<String, String>();

		if (!OFRule.isValidOFRuleString(match)
				|| !OFRule.isValidOFRuleString(action)) {
			throw new Exception("Invalid match and/or action");
		}
		this.putAll(OFRule.parseOFRule(match));
		this.actions.putAll(OFRule.parseOFRule(match));
	}

	public OFRule(Map<String, String> m) throws Exception {
		this.actions = new HashMap<String, String>();

		if (!OFRule.isValidOFRule((Map<String, String>) m)) {
			throw new Exception("Invalid match");
		}
		this.putAll(m);
	}

	@Override
	public String put(String key, String value) {
		if (!OFRULE_REGEX.containsKey(key))
			return null;
		if (!value.matches(OFRULE_REGEX.get(key)))
			return null;

		if (OFACTIONS_REGEX.containsKey(key))
			return this.actions.put(key, value);
		return super.put(key, value);
	}

	/**
	 * 
	 */
	@Override
	public void putAll(Map<? extends String, ? extends String> m) {
		for (Map.Entry<? extends String, ? extends String> e : m.entrySet()) {
			String key = e.getKey();
			String value = e.getValue();

			if (!OFRULE_REGEX.containsKey(key)
					|| !value.matches(OFRULE_REGEX.get(key))) {
				m.remove(key);
				continue;
			}

			if (OFACTIONS_REGEX.containsKey(key)) {
				this.actions.put(key, value);
				m.remove(key);
			}
		}
		super.putAll(m);
	}

	/**
	 * @param A
	 *            OF-formatted match or action key
	 * @return Floodlight staticflowpusher formatted value
	 * 
	 *         This method updates the "actions" key value before returning all
	 *         the pairs.
	 */
	@Override
	public String get(Object obj) {
		if (obj.getClass().equals(String.class)) {
			String key = (String) obj;
			if (OFACTIONS_REGEX.containsKey(key))
				return actions.get(key);

			if (key.equals(OF_STR_ACTIONS)) {
				try {
					rebuildOFActionString();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return super.get(obj);
	}

	public int ruleSize() {
		try {
			rebuildOFActionString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return super.size();
	}

	public int matchSize() {
		int entries = super.size();
		if (this.containsKey(OF_STR_ACTIONS))
			entries -= 1;
		if (this.containsKey("name"))
			entries -= 1;
		return entries;
	}

	/**
	 * @return all key, value pairs in this OFRule, including action key,value
	 *         pairs
	 */
	@Override
	public Set<Entry<String, String>> entrySet() {

		// TODO: There might be a better/more efficient way to do this
		Map<String, String> r = new HashMap<String, String>();
		for (Map.Entry<String, String> entry : super.entrySet()) {
			r.put(entry.getKey(), entry.getValue());
		}

		for (Map.Entry<String, String> entry : this.actions.entrySet()) {
			r.put(entry.getKey(), entry.getValue());
		}

		return r.entrySet();
	}

	/**
	 * @return Floodlight staticflowpusher formatted key,value pairs.
	 * 
	 *         This method updates the "actions" key value before returning all
	 *         the pairs.
	 */
	public Set<Entry<String, String>> floodlightEntrySet() {
		try {
			rebuildOFActionString();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// There might be a better way/efficient to do this
		// but I coded this way because it is faster
		Map<String, String> r = new HashMap<String, String>();
		for (Map.Entry<String, String> entry : super.entrySet()) {
			try {
				String key = entry.getKey();
				
				if (OFRULE_TO_FLOODLIGHT.containsKey(key)) {
					String value = entry.getValue();
					// Add netmask if not present
					if ((key.equals(OF_STR_NW_DST) || key.equals(OF_STR_NW_SRC))
						&& !value.matches(".*\\/.*")) {
						value += "/32";
					} 
					r.put(OFRULE_TO_FLOODLIGHT.get(key), value);
				} else {
					r.put(key, entry.getValue());
					System.out
							.println("WARNING: Couldn't find translation for: "
									+ key);
				}

			} catch (Exception e) {
				System.out.println("WARNING: Error translating key: "
						+ entry.getKey());
				e.printStackTrace();
			}
		}

		return r.entrySet();
	}

	/**
	 * @return ordered action string
	 * @throws Exception
	 */
	private void rebuildOFActionString() throws Exception {
		String set_vlan = "", strip_vlan = "", output = "";

		if (!this.actions.containsKey(OF_STR_OUTPUT))
			throw new Exception("OSCARS require at least an OUTPUT action");

		try {
			for (Map.Entry<String, String> entry : this.actions.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();

				if (key == OF_STR_SET_VLAN)
					set_vlan = String.format("%s=%s,", key, value);
				else if (key == OF_STR_STRIP_VLAN)
					strip_vlan = String.format("%s,", key);
				else if (key == OF_STR_OUTPUT)
					output = String.format("%s=%s", key, value);
			}
		} catch (Exception e) {
			throw new Exception(String.format("Error processing actions"));
		}

		if (output.length() == 0)
			throw new Exception("OSCARS require at least an OUTPUT action");

		if (strip_vlan.length() > 0)
			this.put("actions", strip_vlan + set_vlan + output);
		else
			this.put("actions", set_vlan + output);

	}

	public static Map<String, String> translateOFRule(String match)
			throws Exception {
		OFRule rule = new OFRule(match);
		Map<String, String> r = new HashMap<String, String>();

		for (Map.Entry<String, String> entry : rule.floodlightEntrySet())
			r.put(entry.getKey(), entry.getValue());

		return r;
	}

	public static Map<String, String> parseOFRule(String rule) throws Exception {
		Map<String, String> matchMap = new HashMap<String, String>();

		try {
			String[] tokens = rule.split(",");

			for (String token : tokens) {
				String[] values = token.toLowerCase().split("=");

				if (OFRULE_REGEX.containsKey(values[0])) {
					if (values[1].matches(OFRULE_REGEX.get(values[0])))
						matchMap.put(values[0], values[1]);
					else
						throw new Exception(String.format(
								"Invalid match %s=%s", values[0], values[1]));
				}
			}
		} catch (Exception e) {
			throw new Exception(String.format("Invalid match %s", rule));
		}

		if (matchMap.size() > 0) {
			if (matchMap.containsKey("tp_dst")
					|| matchMap.containsKey("tp_src")
					|| matchMap.containsKey("nw_proto")) {
				if (!matchMap.containsKey("nw_proto"))
					// if no L4 specified, assume TCP
					matchMap.put("nw_proto", "6");
				if (!matchMap.containsKey("dl_type"))
					// if no L3 specified, assume IP
					matchMap.put("dl_type", "0x800");
			}
			return matchMap;
		}
		return null;
	}

	public static boolean isValidOFRule(Map<String, String> rule) {

		try {
			for (Map.Entry<String, String> entry : rule.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();

				if (OFRULE_REGEX.containsKey(key)) {
					if (!value.matches(OFRULE_REGEX.get(key)))
						return false;
				} else {
					return false;
				}
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public OFRule reverse() {
		OFRule rule = new OFRule();

		for (Map.Entry<String, String> entry : this.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			try {
				String reverseKey = OFRULE_REVERSE.get(key);
				if (reverseKey.length() > 0)
					rule.put(reverseKey, value);
			} catch (Exception e) {
				System.out.println("Couldn't find reverse for key " + key);
				e.printStackTrace();
			}
		}
		return rule;
	}

	public static boolean isValidOFRuleString(String rule) {

		String[] tokens = rule.split(",");

		try {
			for (String token : tokens) {
				String[] entry = token.toLowerCase().split("=");
				String key = entry[0];
				String value = entry[1];

				if (OFRULE_REGEX.containsKey(key)) {
					if (!value.matches(OFRULE_REGEX.get(key)))
						return false;
				} else {
					return false;
				}
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Insert OFRule entries based on reservation's VLAN specifications
	 * 
	 * @param srcIsTagged
	 * @param srcVlan
	 * @param dstIsTagged
	 * @param dstVlan
	 */
	public void putVlan(boolean srcIsTagged, String srcVlan,
			boolean dstIsTagged, String dstVlan) {

		if (srcIsTagged) {
			this.put(OF_STR_DL_VLAN, srcVlan);
		}
		if (dstIsTagged) {
			this.put(OF_STR_SET_VLAN, dstVlan);
		}
		if (srcIsTagged && !dstIsTagged) {
			this.put(OF_STR_STRIP_VLAN, "");
		}
	}

	@Override
	public String toString() {
		String str = "";
		for (Map.Entry<String, String> entry : this.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			str += String.format("\"%s\": \"%s\"\n", key, value);
		}
		return str;
	}

}
