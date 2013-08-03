# SDN Path Setup Subsystem for OSCARS

In the summer of 2013, I worked with the integration of [OSCARS](http://www.es.net/services/virtual-circuits-oscars/ OSCARS) and [Floodlight](http://www.projectfloodlight.org/floodlight/). In the Software Defined Network (SDN) context, OSCARS is an application that controls the [ESnet](http://es.net/) transport network and Floodlight is a SDN Controller that communicates with network devices using [OpenFlow](http://www.openflow.org/). SDNPSS implements the interface between OSCARS and SDN Controllers. 

At this time, the only SDN controller supported by SDNPSS is Floodlight, but it is easy to extend it for other SDN Controllers. Floodlight support leverages its StaticFlowEntryPusher module to exchange commands using REST+json objects.

## Usage

[This version of OSCARS](https://github.com/hsr/oscars) has SDNPSS configured as a git submodule. You can start from that:

    # git clone --recursive https://github.com/hsr/oscars

Change the default PSS used by OSCARS in the maven Project Object Model (pom.xml) file and the default PSS Choice in the OSCARS config.yaml file:

    # cd oscars
    # sed -i 's/stubPSS/sdnPSS/g' pom.xml
    # sed -i 's/STUB/SDN/g'       utils/config/config.yaml

Now you can continue with the regular OSCARS installation as described at https://github.com/hsr/oscars.
    



