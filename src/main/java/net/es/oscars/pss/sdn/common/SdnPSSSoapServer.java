package net.es.oscars.pss.sdn.common;

import net.es.oscars.logging.ModuleName;
import net.es.oscars.logging.OSCARSNetLoggerize;
import net.es.oscars.pss.soap.gen.PSSPortType;
import net.es.oscars.pss.soap.gen.PSSService;
import net.es.oscars.utils.config.ConfigDefaults;
import net.es.oscars.utils.soap.OSCARSService;
import net.es.oscars.utils.soap.OSCARSServiceException;
import net.es.oscars.utils.soap.OSCARSSoapService;
import net.es.oscars.utils.svc.ServiceNames;

@OSCARSNetLoggerize( moduleName = ModuleName.PSS)
@OSCARSService (
        implementor = "net.es.oscars.pss.sdn.common.SdnPSSSoapHandler",
        serviceName = ServiceNames.SVC_PSS,
        config = ConfigDefaults.CONFIG
)
public class SdnPSSSoapServer extends OSCARSSoapService<PSSService, PSSPortType> {
    private static SdnPSSSoapServer instance;

    public static SdnPSSSoapServer getInstance() throws OSCARSServiceException {
        if (instance == null) {
            instance = new SdnPSSSoapServer();
        }
        return instance;
    }

    private SdnPSSSoapServer() throws OSCARSServiceException {
        super(ServiceNames.SVC_PSS);
    }
}
