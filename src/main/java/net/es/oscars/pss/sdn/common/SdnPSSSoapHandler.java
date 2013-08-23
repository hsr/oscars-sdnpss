package net.es.oscars.pss.sdn.common;

import java.util.List;
import java.util.Map;

import net.es.oscars.common.soap.gen.OSCARSFaultReport;
import net.es.oscars.logging.ModuleName;
import net.es.oscars.logging.OSCARSNetLogger;
import net.es.oscars.pss.beans.PSSAction;
import net.es.oscars.pss.beans.PSSException;
import net.es.oscars.pss.beans.PSSRequest;
import net.es.oscars.pss.beans.config.CircuitServiceConfig;
import net.es.oscars.pss.config.ConfigHolder;
import net.es.oscars.pss.enums.ActionStatus;
import net.es.oscars.pss.enums.ActionType;
import net.es.oscars.pss.notify.CoordNotifier;
import net.es.oscars.pss.sdn.connector.FloodlightSDNConnector;
import net.es.oscars.pss.sdn.connector.ISDNConnector.ISDNConnectorResponse;
import net.es.oscars.pss.soap.gen.ModifyReqContent;
import net.es.oscars.pss.soap.gen.PSSPortType;
import net.es.oscars.pss.soap.gen.SetupReqContent;
import net.es.oscars.pss.soap.gen.StatusReqContent;
import net.es.oscars.pss.soap.gen.TeardownReqContent;
import net.es.oscars.topoBridge.sdn.BaseSDNTopologyService;
import net.es.oscars.topoBridge.sdn.SDNHop;
import net.es.oscars.utils.sharedConstants.ErrorCodes;
import net.es.oscars.utils.soap.ErrorReport;
import net.es.oscars.utils.svc.ServiceNames;

import org.apache.log4j.Logger;

/**
 * main entry point for PSS
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 * 
 */
@javax.jws.WebService(serviceName = ServiceNames.SVC_PSS, targetNamespace = "http://oscars.es.net/OSCARS/pss", portName = "PSSPort", endpointInterface = "net.es.oscars.pss.soap.gen.PSSPortType")
@javax.xml.ws.BindingType(value = "http://www.w3.org/2003/05/soap/bindings/HTTP/")
public class SdnPSSSoapHandler implements PSSPortType {

	private static final Logger log = Logger.getLogger(SdnPSSSoapHandler.class
			.getName());
	private static final String moduleName = ModuleName.PSS;
	private static final FloodlightSDNConnector sdnConnector = new FloodlightSDNConnector();

	public void setup(SetupReqContent setupReq) {
		String event = "setup";
		OSCARSNetLogger netLogger = OSCARSNetLogger.getTlogger();
		netLogger.init(moduleName, setupReq.getTransactionId());
		String gri = setupReq.getReservation().getGlobalReservationId();
		netLogger.setGRI(gri);
		List<SDNHop> hops = null;

		log.info(netLogger.start(event));

		try {
			hops = BaseSDNTopologyService.extractSDNHops(setupReq.getReservation()
					.getReservedConstraint().getPathInfo().getPath().getHop());
		} catch (Exception e) {
			log.info("Couldn't get path: " + e.getMessage());
		}

		CircuitServiceConfig circuitServiceConfig = ConfigHolder.getInstance()
				.getBaseConfig().getCircuitService();
		Map<String, String> circuitServiceParams = circuitServiceConfig
				.getParams();
		
		// TODO: here we use the description field of the reservation
		// to specify a OFMatch. The correct way to do it is to add a field 
		// in the WBUI to specify the OFMatch instead of reading it from the description.
		String description = setupReq.getReservation().getDescription();
		
		try {
			if (circuitServiceParams.containsKey("controller")) {
				sdnConnector.setConnectionAddress(circuitServiceParams
						.get("controller"));
				
				if ((hops != null) && (hops.size() > 0) && 
						(sdnConnector.setupCircuit(hops, gri, description) == 
						ISDNConnectorResponse.SUCCESS)) {
					
					notifyCoordinator(setupReq.getTransactionId(), 
							ActionType.SETUP, setupReq, ActionStatus.SUCCESS);

			        log.info(netLogger.end(event));
					return;
				}

			}
		}
		catch (Exception e) {
			log.error("Couldn't setup circuit: " + e.getMessage());
		}
		
		notifyCoordinator(setupReq.getTransactionId(), 
				ActionType.SETUP, setupReq, ActionStatus.FAIL);
	
		log.info(netLogger.end(event));
	}

	public void teardown(TeardownReqContent teardownReq) {
		OSCARSNetLogger netLogger = OSCARSNetLogger.getTlogger();
		netLogger.init(moduleName, teardownReq.getTransactionId());
		String gri = teardownReq.getReservation().getGlobalReservationId();
		netLogger.setGRI(gri);
		List<SDNHop> hops = null;

        log.info(netLogger.start("teardown"));

		try {
			hops = BaseSDNTopologyService.extractSDNHops(teardownReq.getReservation()
					.getReservedConstraint().getPathInfo().getPath().getHop());
		} catch (Exception e) {
			log.info("Couldn't get path: " + e.getMessage());
		}

		CircuitServiceConfig circuitServiceConfig = ConfigHolder.getInstance()
				.getBaseConfig().getCircuitService();
		Map<String, String> circuitServiceParams = circuitServiceConfig
				.getParams();

		try {
			if (circuitServiceParams.containsKey("controller")) {
				sdnConnector.setConnectionAddress(circuitServiceParams
						.get("controller"));
				
				if ((hops != null) && (hops.size() > 0) && (
					sdnConnector.teardownCircuit(hops, gri) ==
						ISDNConnectorResponse.SUCCESS)) {
					
						notifyCoordinator(teardownReq.getTransactionId(), 
							ActionType.TEARDOWN, teardownReq, ActionStatus.SUCCESS);

				        log.info(netLogger.end("teardown"));
						return;
				}
			}
		}
		catch (Exception e) {
			log.error("Couldn't teardown circuit: " + e.getMessage());
		}
		
		notifyCoordinator(teardownReq.getTransactionId(), 
				ActionType.TEARDOWN, teardownReq, ActionStatus.FAIL);

		log.info(netLogger.end("teardown"));
	}

	public void modify(ModifyReqContent modifyReq) {
//		String event = "modify";
//		OSCARSNetLogger netLogger = OSCARSNetLogger.getTlogger();
//		netLogger.init(moduleName, modifyReq.getTransactionId());
//		String gri = modifyReq.getReservation().getGlobalReservationId();
//		netLogger.setGRI(gri);
//		log.info(netLogger.start(event));
//
//		PSSAction act = new PSSAction();
//		CoordNotifier coordNotify = new CoordNotifier();
//		try {
//			PSSRequest req = new PSSRequest();
//			req.setModifyReq(modifyReq);
//			req.setRequestType(PSSRequest.PSSRequestTypes.MODIFY);
//
//			act.setRequest(req);
//			act.setActionType(net.es.oscars.pss.enums.ActionType.MODIFY);
//			OSCARSFaultReport faultReport = new OSCARSFaultReport();
//			faultReport.setErrorMsg("Modify not supported");
//			faultReport.setErrorType(ErrorReport.SYSTEM);
//			faultReport.setErrorCode(ErrorCodes.NOT_IMPLEMENTED);
//			act.setFaultReport(faultReport);
//			act.setStatus(ActionStatus.FAIL);
//			coordNotify.process(act);
//		} catch (PSSException e) {
//			log.error(netLogger.error(event, ErrSev.MAJOR,
//					"caught PSSException " + e.getMessage()));
//		}
//		log.info(netLogger.end(event));
		
		notifyCoordinator(modifyReq.getTransactionId(), ActionType.MODIFY, 
				modifyReq, ActionStatus.SUCCESS);

		return;
	}

	public void status(StatusReqContent statusReq) {
//		String event = "status";
//		OSCARSNetLogger netLogger = OSCARSNetLogger.getTlogger();
//		netLogger.init(moduleName, statusReq.getTransactionId());
//		String gri = statusReq.getReservation().getGlobalReservationId();
//		netLogger.setGRI(gri);
//		log.info(netLogger.start(event));
//
//		PSSAction act = new PSSAction();
//		CoordNotifier coordNotify = new CoordNotifier();
//		try {
//			PSSRequest req = new PSSRequest();
//			req.setStatusReq(statusReq);
//			req.setRequestType(PSSRequest.PSSRequestTypes.STATUS);
//
//			act.setRequest(req);
//			act.setActionType(net.es.oscars.pss.enums.ActionType.STATUS);
//			OSCARSFaultReport faultReport = new OSCARSFaultReport();
//			faultReport.setErrorMsg("Status not supported");
//			faultReport.setErrorType(ErrorReport.SYSTEM);
//			faultReport.setErrorCode(ErrorCodes.NOT_IMPLEMENTED);
//			act.setFaultReport(faultReport);
//			act.setStatus(ActionStatus.FAIL);
//			coordNotify.process(act);
//		} catch (PSSException e) {
//			log.error(netLogger.error(event, ErrSev.MAJOR,
//					"caught PSSException " + e.getMessage()));
//		}
//		log.info(netLogger.end(event));
		notifyCoordinator(statusReq.getTransactionId(), ActionType.STATUS, 
			statusReq, ActionStatus.SUCCESS);
		
		return;
	}

	private void notifyCoordinator(String transactionId, 
			ActionType type, Object reqContent, ActionStatus status) {
		PSSAction act = new PSSAction();
		CoordNotifier coordNotify = new CoordNotifier();
		PSSRequest req = new PSSRequest();
		
		switch (type) {
		case SETUP:
			req.setSetupReq((SetupReqContent) reqContent);
			req.setRequestType(PSSRequest.PSSRequestTypes.SETUP);
			break;
		case TEARDOWN:
			req.setTeardownReq((TeardownReqContent) reqContent);
			req.setRequestType(PSSRequest.PSSRequestTypes.TEARDOWN);
			break;
		case MODIFY:
			req.setModifyReq((ModifyReqContent) reqContent);
			req.setRequestType(PSSRequest.PSSRequestTypes.MODIFY);
		case STATUS:
			req.setStatusReq((StatusReqContent) reqContent);
			req.setRequestType(PSSRequest.PSSRequestTypes.STATUS);
		default:
			break;
		}

		act.setRequest(req);
		act.setActionType(type);
		act.setStatus(status);
		if (status == ActionStatus.FAIL) {
			OSCARSFaultReport faultReport = new OSCARSFaultReport();
			faultReport.setErrorMsg("PSS error");
			faultReport.setErrorType(ErrorReport.SYSTEM);
			switch (type) {
			case SETUP:
				faultReport.setErrorCode(ErrorCodes.PATH_SETUP_FAILED);
				break;
			case TEARDOWN:
				faultReport.setErrorCode(ErrorCodes.PATH_TEARDOWN_FAILED);
			default:
				break;
			}
			faultReport.setModuleName("SdnPSS");
			act.setFaultReport(faultReport);
		}

		try {			
			coordNotify.process(act);
		}
		catch (PSSException e) {
			log.error("Could not teardown circuit, caught PSSException: " 
				+ e.getMessage());
		}
		catch (Exception e) {
			log.debug("Could not teardown circuit: " + e.getMessage());
		}
	}
}
