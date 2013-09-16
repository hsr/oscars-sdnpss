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

After configuring your controller IP address and creating a topology file for your network (as described below), you can continue with the regular OSCARS installation as described at [https://github.com/hsr/oscars](https://github.com/hsr/oscars).
    
### Configuration

#### SDN Controller

To use SDNPSS, you need to configure your SDN controller network address. The PSS will read this information from the file `config/config.SSL.yaml` or `config/config.HTTP.yaml`. Here is an example of how you can set a controller running at `http://sdncontroller.es.net`, port `8080`:

    ...
    circuitService:
        id:                 'sdn'
        logRequest:         true
        logResponse:        true
        params:
            controller: "http://sdncontroller.es.net:8080"
    ... 


#### Topology

SDNPSS uses datapath ID (DPID) numbers to identify network devices. If you are creating your own NMWG file with the topology of your network, you need to encode DPIDs on the node part of your URNs. SDN Controllers usually export 64bit DPIDs as a colon separated list of hex numbers each representing one byte (similar to a MAC address). However, as colons are used to identify sections of a URN, you need to replace them with dots. Here is an example of the representation of the DPID `00:00:00:00:00:00:00:04` on a link URN:

    urn:ogf:network:domain=mydomain:node=00.00.00.00.00.00.00.04:port=1:link=1  

**NOTE** that the highest 16bits of a DPID is used by SDNPSS to identify device capabilities. To specify that a node is a L0/1 device, you need to configure the first bits of its DPID as 0x1111.

**Implicit Mode Provisioning:** To enable implicit mode for your L0/1 devices, you need to encode the trib AID and router IP address into the link URN of your devices. This gives the SDNPSS the necessary information to request a circuit creation implicitly. The trib AID is a dot separated 6-byte number in hex and the router IP is the default IP address representation. Here is an example of a L0/1 device (DPID=0x1111000000000006) port (2) with AID and router IP address encoded in a URN:

    urn:ogf:network:domain=testdomain-1:node=11.11.00.00.00.00.00.06:port=2:link=aa.aa.00.00.03.06/10.0.0.6


