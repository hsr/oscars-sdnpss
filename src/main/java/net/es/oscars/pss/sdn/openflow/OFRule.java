package net.es.oscars.pss.sdn.openflow;

import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import net.es.oscars.topoBridge.sdn.SDNHop;

/**
 * OFRule represents an entry in an OpenFlow table. It is basically a map of
 * OpenFlow key,value pairs that get inserted in an OpenFlow switch table. It is
 * used by sdnPSS to parse OF match input and OF actions and to generate the
 * final Floodlight request.
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 * 
 */
public class OFRule extends HashMap<String,String> {
	private static final long serialVersionUID = 5955370395586986465L;

	private static String IP_REGEX = "" + "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
			+ "\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])(\\/(\\d|[1-2]\\d|3[0-2]))$";

	private static String MAC_REGEX = "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$";
	private static String INT_REGEX = "^[1-9]\\d*$";
	private static String HEX_REGEX = "^(0x|)[0-9A-Fa-f]*$";
	private static String ANY_REGEX = ".*";

	private static Map<String, String> OFACTIONS_REGEX = new HashMap<String, String>() {
		private static final long serialVersionUID = -4517039142009168646L;
		{
		// actions
		put("output", INT_REGEX);
		put("set_vlan", INT_REGEX);
		put("srip_vlan", ANY_REGEX);
		}
	};
	
	private static Map<String, String> OFRULE_REGEX = 
		new HashMap<String, String>() {
		private static final long serialVersionUID = 4335160907174232213L;
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
			// actions
			put("output", INT_REGEX);
			put("set_vlan", INT_REGEX);
			put("srip_vlan", ANY_REGEX);
		}
	};
	
	private static Map<String, String> OFRULE_REVERSE = 
			new HashMap<String, String>() {
			private static final long serialVersionUID = -8409540222829712936L;
			{
				put("in_port", "output");
				put("output", "in_port");
				put("dl_dst", "dl_src");
				put("dl_src", "dl_dst");
				put("dl_vlan", "set_vlan");
				put("set_vlan", "dl_vlan");
				
				put("tp_dst", "tp_src");
				put("tp_src", "tp_dst");
				put("nw_tos", "nw_tos");
				put("nw_dst", "nw_src");
				put("dl_type", "dl_type");
				put("nw_proto", "nw_proto");
				put("srip_vlan", "");
				put("dl_vlan_pcp", "dl_vlan_pcp");
			}
		};

	private static Map<String, String> OFRULE_TO_FLOODLIGHT = 
		new HashMap<String, String>() {
		private static final long serialVersionUID = 4335160907174232213L;
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
	
	public Map<String, String> actions = null;
	
	
	public OFRule() {
		super();
	}


	public OFRule(String match) throws Exception {
		super();
		this.actions = new HashMap<String,String> ();
		
		if (!OFRule.isValidOFRuleString(match)) {
			throw new Exception("Invalid match");
		}
		this.putAll(OFRule.parseOFRule(match));
	}
	
	public OFRule(String match, String action) throws Exception {
		super();
		this.actions = new HashMap<String,String> ();
		
		if (!OFRule.isValidOFRuleString(match) 
			|| !OFRule.isValidOFRuleString(action)) {
			throw new Exception("Invalid match and/or action");
		}
		this.putAll(OFRule.parseOFRule(match));
		this.actions.putAll(OFRule.parseOFRule(match));
	}

	public OFRule(Map<String, String> m) 
			throws Exception {
		this.actions = new HashMap<String,String> ();
		
		if (!OFRule.isValidOFRule((Map<String, String>) m)) {
			throw new Exception("Invalid match");
		}
		this.putAll(m);
	}

	
	@Override
	public String put(String key, String value) {
		if (OFACTIONS_REGEX.containsKey(key))
			return this.actions.put(key, value);
		return super.put(key, value);
	}

	/**
	 * 
	 */
	@Override
	public void putAll(Map<? extends String, ? extends String> m) {
		for (Map.Entry<? extends String, ? extends String> e 
				: m.entrySet()) {
			String key = e.getKey();
			if (OFACTIONS_REGEX.containsKey(key)) {
				String value = e.getValue();
				this.actions.put(key, value);
				m.remove(key);
			}
		}
		super.putAll(m);
	}

	/**
	 * @param A OF-formatted match or action key
	 * @return Floodlight staticflowpusher formatted value 
	 * 
	 * This method updates the "action" key value before 
	 * returning all the pairs.
	 */
	@Override
	public String get(Object obj) {
		if (obj.getClass().equals(String.class)) {
			String key = (String) obj;
			if (OFACTIONS_REGEX.containsKey(key))
				return actions.get(key);

			if (key.equals("action")) {
				try {
					rebuildOFActionString();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return super.get(obj);
	}
	
	/**
	 * @return Floodlight staticflowpusher formatted key,value pairs
	 * Updates the "action" key value before returning all the pairs. 
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
			r.put(OFRULE_TO_FLOODLIGHT.get(entry.getKey()),
					entry.getValue());
		}
		
		return r.entrySet();
	}
	/**
	 * @return all key, value pairs in this OFRule, including
	 * action key,value pairs
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
	 * @return ordered action string
	 * @throws Exception
	 */
	private void rebuildOFActionString() throws Exception {
		String set_vlan = "", strip_vlan = "", output = "";

		if (!this.actions.containsKey("set_vlan")
				|| !this.actions.containsKey("output"))
			throw new Exception(
					"OSCARS require at least SET_VLAN and OUTPUT actions");

		try {
			for (Map.Entry<String, String> entry : this.actions.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();

				if (key == "dl_vlan")
					set_vlan = String.format("%s=%s,", key, value);
				else if (key == "strip_vlan")
					strip_vlan = String.format("%s,", key);
				else if (key == "output")
					output = String.format("%s=%s", key, value);
			}
		} catch (Exception e) {
			throw new Exception(String.format("Error processing actions"));
		}

		if (set_vlan.length() == 0 || output.length() == 0)
			throw new Exception(
					"OSCARS require at least SET_VLAN and OUTPUT actions");
		
		if (strip_vlan.length() > 0)
			this.put("action", strip_vlan + set_vlan + output);
		else
			this.put("action", set_vlan + output);

	}
	
	public static Map<String, String> translateOFRule(String match) 
			throws Exception {
		OFRule rule = new OFRule(match);
		Map<String, String> r = new HashMap<String, String>();
		
		for (Map.Entry<String, String> entry : rule.floodlightEntrySet())
			r.put(entry.getKey(), entry.getValue());

		return r;
	}
	
	public static Map<String, String> parseOFRule(String rule)
			throws Exception {
		Map<String, String> matchMap = new HashMap<String, String>();

		try {
			String[] tokens = rule.split(",");
	
			for (String token : tokens) {
				String[] values = token.toLowerCase().split("=");
	
				if (OFRULE_REGEX.containsKey(values[0])) {
					if (values[1].matches(OFRULE_REGEX.get(values[0])))
						matchMap.put(values[0], values[1]);
					else
						throw new Exception(String.format("Invalid match %s=%s",
								values[0], values[1]));
				}
			}
		}
		catch (Exception e) {
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
				}
				else {
					return false;
				}
			}
		}
		catch (Exception e) {
			return false;
		}
		return true;
	}
	
	public OFRule reverse() {
		OFRule rule = new OFRule();
		
		for (Map.Entry<String, String> entry : this.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			String translatedKey = OFRULE_REVERSE.get(key);
			if (translatedKey.length() > 0)
				rule.put(translatedKey, value);
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
				}
				else {
					return false;
				}
			}
		}
		catch (Exception e) {
			return false;
		}
		return true;
	}
}
