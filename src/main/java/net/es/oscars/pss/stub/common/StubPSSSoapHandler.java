package net.es.oscars.pss.stub.common;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import net.es.oscars.logging.ErrSev;
import net.es.oscars.logging.ModuleName;
import net.es.oscars.logging.OSCARSNetLogger;
import net.es.oscars.pss.beans.PSSAction;
import net.es.oscars.pss.beans.PSSException;
import net.es.oscars.pss.beans.PSSRequest;
import net.es.oscars.pss.enums.ActionStatus;
import net.es.oscars.pss.notify.CoordNotifier;
import net.es.oscars.pss.soap.gen.ModifyReqContent;
import net.es.oscars.pss.soap.gen.PSSPortType;
import net.es.oscars.pss.soap.gen.SetupReqContent;
import net.es.oscars.common.soap.gen.OSCARSFaultReport;
import net.es.oscars.pss.soap.gen.StatusReqContent;
import net.es.oscars.pss.soap.gen.TeardownReqContent;
import net.es.oscars.utils.sharedConstants.ErrorCodes;
import net.es.oscars.utils.soap.ErrorReport;
import net.es.oscars.utils.svc.ServiceNames;

import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneHopContent;

import net.es.oscars.utils.topology.NMWGParserUtil;


final class NetDeviceLink {
	public String srcNetDevice;
	public String dstNetDevice;
	public String srcPort;
	public String dstPort;
	
	public NetDeviceLink(String srcURN, String dstURN) {
		this.srcNetDevice = NMWGParserUtil.getURNPart(srcURN, NMWGParserUtil.NODE_TYPE);
		this.dstNetDevice = NMWGParserUtil.getURNPart(dstURN, NMWGParserUtil.NODE_TYPE);
		this.srcPort      = NMWGParserUtil.getURNPart(srcURN, NMWGParserUtil.PORT_TYPE);
		this.dstPort      = NMWGParserUtil.getURNPart(dstURN, NMWGParserUtil.PORT_TYPE);
	}
}

/**
 * main entry point for PSS
 *
 * @author haniotak,mrt
 *
 */
@javax.jws.WebService(
        serviceName = ServiceNames.SVC_PSS,
        targetNamespace = "http://oscars.es.net/OSCARS/pss",
        portName = "PSSPort",
        endpointInterface = "net.es.oscars.pss.soap.gen.PSSPortType")
@javax.xml.ws.BindingType(value = "http://www.w3.org/2003/05/soap/bindings/HTTP/")

public class StubPSSSoapHandler implements PSSPortType {

    private static final Logger log = Logger.getLogger(StubPSSSoapHandler.class.getName());
    private static final String moduleName = ModuleName.PSS;

    private List<NetDeviceLink> getNetDeviceLinks(List<CtrlPlaneHopContent> hops) {
    	List<NetDeviceLink> netDevices = new ArrayList<NetDeviceLink>();
    	String src = null;
    	
    	log.info(StubPSSSoapHandler.class.getName());
    	
    	int i = 1;
        try {
        	for (CtrlPlaneHopContent hop : hops) {
	        	String dst  = hop.getLink().getId();

	        	if (src == null) {
	        		src = dst;
	        		continue;
	        	}
	        	
	        	if (NMWGParserUtil.compareURNPart(src, dst, NMWGParserUtil.NODE_TYPE)) {
	        		netDevices.add(new NetDeviceLink(src, dst));
	        	}
	        	
	        	src = dst;
	        	
	        	log.info(i + ") hop id:" + hop.getId());
	        	log.info(" linkIdId:"  + hop.getLink().getId());
	        	i++;
	        }
        	if (i % 2 == 0) {
        		log.info("Odd number of hops. Are you sure this path connect two external links?");
        	}
        }
        catch (Exception e) {
        	log.info("Error Computing Topology Links: " + e.getMessage());
        }
        
		return netDevices;
    }

    private boolean setupFloodlightCircuit(List<NetDeviceLink> netDeviceLinks, String circuitName) {
    	
    	log.info("circuit setup 1");
    	
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

        /* curl -s -d '{
         * 	"switch": "00:00:00:00:00:00:00:07",
         * 	"name":"00:00:00:00:00:00:00:07.test.f",
         * 	"ether-type":"0x800",
         * 	"cookie":"0",
         * 	"priority":"32768",
         * 	"ingress-port":"1",
         * 	"active":"true",
         * 	"actions":"output=3"
         * }'
         * http://student6.es.net:8080/wm/staticflowentrypusher/json
         */
    	String controllerURL = "http://student6.es.net:8080/wm/staticflowentrypusher/json";
    	String request;
    	
    	for (NetDeviceLink l : netDeviceLinks) {
    		String node = l.srcNetDevice.replaceAll("\\.", ":");
     		request = baseJSONRequest
    					.replaceAll("\\{switch\\}", node)
    					.replaceAll("\\{name\\}", circuitName + node + ".ipf")
    					.replaceAll("\\{ethtype\\}","0x800")
    					.replaceAll("\\{srcPort\\}", l.srcPort)
    					.replaceAll("\\{dstPort\\}", l.dstPort);
    		
    		log.info("Setting up 0x0800 link " + l.srcNetDevice + " " + l.srcPort + " -> " + l.dstPort);
			sendFloodlightNewFlowRequest(request, controllerURL);
    		
    		request = baseJSONRequest
						.replaceAll("\\{switch\\}", node)
						.replaceAll("\\{name\\}", circuitName + node + ".ipr")
						.replaceAll("\\{ethtype\\}","0x800")
						.replaceAll("\\{srcPort\\}", l.dstPort)
						.replaceAll("\\{dstPort\\}", l.srcPort);
		
			log.info("Setting up 0x0800 link " + l.srcNetDevice + " " + l.dstPort + " -> " + l.srcPort);
			sendFloodlightNewFlowRequest(request, controllerURL);

    		request = baseJSONRequest
						.replaceAll("\\{switch\\}", node)
						.replaceAll("\\{name\\}", circuitName + node + ".arpf")
						.replaceAll("\\{ethtype\\}","0x806")
						.replaceAll("\\{srcPort\\}", l.srcPort)
						.replaceAll("\\{dstPort\\}", l.dstPort);
		
			log.info("Setting up 0x0806 link " + l.srcNetDevice + " " + l.srcPort + " -> " + l.dstPort);
			sendFloodlightNewFlowRequest(request, controllerURL);
			
    		request = baseJSONRequest
						.replaceAll("\\{switch\\}", node)
						.replaceAll("\\{name\\}", circuitName + node + ".arpr")
						.replaceAll("\\{ethtype\\}","0x806")
						.replaceAll("\\{srcPort\\}", l.dstPort)
						.replaceAll("\\{dstPort\\}", l.srcPort);
		
			log.info("Setting up 0x0806 link " + l.srcNetDevice + " " + l.dstPort + " -> " + l.srcPort);
			sendFloodlightNewFlowRequest(request, controllerURL);

    	}
    	return true;
    }

    private boolean teardownFloodlightCircuit(List<NetDeviceLink> netDeviceLinks, String circuitName) {
    	
    	log.info("circuit teardown 1");
    	
    	String baseJSONRequest = "{"
    			+ "\"name\":\"{name}\","
    			+ "\"switch\":\"{switch}\""
    			+ "}";
    	
        
        /* curl -X DELETE -d '{
         * 	"switch": "00:00:00:00:00:00:00:07",
         * 	"name":"00:00:00:00:00:00:00:07.test.f",
         * }'
         * http://student6.es.net:8080/wm/staticflowentrypusher/json
         */
    	
    	String controllerURL = "http://student6.es.net:8080/wm/staticflowentrypusher/json";
    	String request;
    	
    	for (NetDeviceLink l : netDeviceLinks) {
    		String node = l.srcNetDevice.replaceAll("\\.", ":");
 			request = baseJSONRequest
 					.replaceAll("\\{switch\\}",  node)
 					.replaceAll("\\{name\\}",    circuitName + node + ".ipf");
    		log.info("Removing link 0x800 " + l.srcNetDevice + " " + l.srcPort + " -> " + l.dstPort);
    		sendFloodlightDelFlowRequest(request, controllerURL);

 			request = baseJSONRequest
 					.replaceAll("\\{switch\\}", node)
 					.replaceAll("\\{name\\}",   circuitName + node + ".ipr");
    		log.info("Removing link 0x800 " + l.srcNetDevice + " " + l.dstPort + " -> " + l.srcPort);
    		sendFloodlightDelFlowRequest(request, controllerURL);
    		
 			request = baseJSONRequest
 					.replaceAll("\\{switch\\}", node)
 					.replaceAll("\\{name\\}",   circuitName + node + ".arpf");
    		log.info("Removing link 0x806 " + l.srcNetDevice + " " + l.srcPort + " -> " + l.dstPort);
    		sendFloodlightDelFlowRequest(request, controllerURL);
    		
 			request = baseJSONRequest
 					.replaceAll("\\{switch\\}", node)
 					.replaceAll("\\{name\\}",   circuitName + node + ".arpr");
    		log.info("Removing link 0x806 " + l.srcNetDevice + " " + l.dstPort + " -> " + l.srcPort);
    		sendFloodlightDelFlowRequest(request, controllerURL);
    	}
    	
    	return true;
    }
    
    private void sendFloodlightNewFlowRequest(String jsonObject, String controller) {
    	exec("curl -s -d " + jsonObject + " " + controller);
    }

    private void sendFloodlightDelFlowRequest(String jsonObject, String controller) {
    	exec("curl -X DELETE -d " + jsonObject + " " + controller);
    }

    
    private void exec(String request) {
    	Runtime rt = Runtime.getRuntime();
    	try {
    		log.info("Sending command: " + request);
    		rt.exec(request);
    	}
    	catch (Exception e) {
    		log.info("Problem with floodlight request " + request + "\n  " + e.getMessage());
    	}
	}

    
	public void setup(SetupReqContent setupReq) {
        String event = "setup";
        OSCARSNetLogger netLogger = OSCARSNetLogger.getTlogger();
        netLogger.init(moduleName,setupReq.getTransactionId());
        String gri = setupReq.getReservation().getGlobalReservationId();
        List<NetDeviceLink> netDevices = null;
        
        netLogger.setGRI(gri);
        log.info(netLogger.start(event));
 
        String reservationID = setupReq.getReservation().getGlobalReservationId();
        
     	log.info("Setting up reservation: " + reservationID);
        try {
        	netDevices = getNetDeviceLinks(setupReq.getReservation().
        									getReservedConstraint().
        									getPathInfo().
        									getPath().
        									getHop());
        }
        catch (Exception e) {
        	log.info("Couldn't get path: " + e.getMessage());
        }


        PSSAction act = new PSSAction();
        CoordNotifier coordNotify = new CoordNotifier();
        try {
            PSSRequest req = new PSSRequest();
            req.setSetupReq(setupReq);
            req.setRequestType(PSSRequest.PSSRequestTypes.SETUP);

            act.setRequest(req);
            act.setActionType(net.es.oscars.pss.enums.ActionType.SETUP);
            
            if ((netDevices != null) &&
            	(netDevices.size() > 0) &&
            	setupFloodlightCircuit(netDevices, reservationID)) {
            	act.setStatus(ActionStatus.SUCCESS);
            } else {
                OSCARSFaultReport faultReport = new OSCARSFaultReport ();
                faultReport.setErrorMsg("Floodlight setup failed error");
                faultReport.setErrorType(ErrorReport.SYSTEM);
                faultReport.setErrorCode(ErrorCodes.PATH_SETUP_FAILED);
                faultReport.setModuleName("StubPSS");

                act.setFaultReport(faultReport);
                act.setStatus(ActionStatus.FAIL);
            }
            log.debug(netLogger.getMsg(event,"calling coordNotify.process"));
            coordNotify.process(act);
        } catch (PSSException e) {
            log.error(netLogger.error(event,ErrSev.MAJOR,"caught PSSException " + e.getMessage()));
        }
        log.info(netLogger.end(event));
    }

    public void teardown(TeardownReqContent teardownReq) {
        String event = "teardown";
        OSCARSNetLogger netLogger = OSCARSNetLogger.getTlogger();
        netLogger.init(moduleName,teardownReq.getTransactionId());
        String gri = teardownReq.getReservation().getGlobalReservationId();
        List<NetDeviceLink> netDevices = null;
        
        netLogger.setGRI(gri);
        log.info(netLogger.start(event));
        
        String reservationID = teardownReq.getReservation().getGlobalReservationId();
        
     	log.info("Setting up reservation: " + reservationID);
        try {
        	netDevices = getNetDeviceLinks(teardownReq.getReservation().
        									getReservedConstraint().
        									getPathInfo().
        									getPath().
        									getHop());
        }
        catch (Exception e) {
        	log.info("Couldn't get path: " + e.getMessage());
        }

        
        PSSAction act = new PSSAction();
        CoordNotifier coordNotify = new CoordNotifier();
        try {
            PSSRequest req = new PSSRequest();
            req.setTeardownReq(teardownReq);
            req.setRequestType(PSSRequest.PSSRequestTypes.TEARDOWN);

            act.setRequest(req);
            act.setActionType(net.es.oscars.pss.enums.ActionType.TEARDOWN);
            
            if (teardownFloodlightCircuit(netDevices, reservationID)) {
            	act.setStatus(ActionStatus.SUCCESS);
            } else {
                OSCARSFaultReport faultReport = new OSCARSFaultReport ();
                faultReport.setErrorMsg("simulated PSS error");
                faultReport.setErrorType(ErrorReport.SYSTEM);
                faultReport.setErrorCode(ErrorCodes.PATH_TEARDOWN_FAILED);
                faultReport.setModuleName("StubPSS");
                act.setFaultReport(faultReport);
                act.setStatus(ActionStatus.FAIL);
            }

            
            coordNotify.process(act);
        } catch (PSSException e) {
            log.error(netLogger.error(event,ErrSev.MAJOR,"caught PSSException " + e.getMessage()));
        }
        log.info(netLogger.end(event));
    }


    public  void modify(ModifyReqContent modifyReq)  {
        String event = "modify";
        OSCARSNetLogger netLogger = OSCARSNetLogger.getTlogger();
        netLogger.init(moduleName,modifyReq.getTransactionId());
        String gri = modifyReq.getReservation().getGlobalReservationId();
        netLogger.setGRI(gri);
        log.info(netLogger.start(event));
        
        PSSAction act = new PSSAction();
        CoordNotifier coordNotify = new CoordNotifier();
        try {
            PSSRequest req = new PSSRequest();
            req.setModifyReq(modifyReq);
            req.setRequestType(PSSRequest.PSSRequestTypes.MODIFY);

            act.setRequest(req);
            act.setActionType(net.es.oscars.pss.enums.ActionType.MODIFY);
            OSCARSFaultReport faultReport = new OSCARSFaultReport ();
            faultReport.setErrorMsg("Modify not supported");
            faultReport.setErrorType(ErrorReport.SYSTEM);
            faultReport.setErrorCode(ErrorCodes.NOT_IMPLEMENTED);
            act.setFaultReport(faultReport);
            act.setStatus(ActionStatus.FAIL);
            coordNotify.process(act);
        } catch (PSSException e) {
            log.error(netLogger.error(event,ErrSev.MAJOR,"caught PSSException " + e.getMessage()));
        }
        log.info(netLogger.end(event));
    }

    public void status(StatusReqContent statusReq) {
        String event = "status";
        OSCARSNetLogger netLogger = OSCARSNetLogger.getTlogger();
        netLogger.init(moduleName,statusReq.getTransactionId());
        String gri = statusReq.getReservation().getGlobalReservationId();
        netLogger.setGRI(gri);
        log.info(netLogger.start(event));
 
        PSSAction act = new PSSAction();
        CoordNotifier coordNotify = new CoordNotifier();
        try {
            PSSRequest req = new PSSRequest();
            req.setStatusReq(statusReq);
            req.setRequestType(PSSRequest.PSSRequestTypes.STATUS);

            act.setRequest(req);
            act.setActionType(net.es.oscars.pss.enums.ActionType.STATUS);
            OSCARSFaultReport faultReport = new OSCARSFaultReport ();
            faultReport.setErrorMsg("Status not supported");
            faultReport.setErrorType(ErrorReport.SYSTEM);
            faultReport.setErrorCode(ErrorCodes.NOT_IMPLEMENTED);
            act.setFaultReport(faultReport);
            act.setStatus(ActionStatus.FAIL);
            coordNotify.process(act);
        } catch (PSSException e) {
            log.error(netLogger.error(event,ErrSev.MAJOR,"caught PSSException " + e.getMessage()));
        }
        log.info(netLogger.end(event));
    }
}
