package com.cloud.api.command.admin.network;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.NetworkOfferingResponse;
import com.cloud.api.response.ServiceOfferingResponse;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Service;
import com.cloud.offering.NetworkOffering;
import com.cloud.offering.NetworkOffering.Availability;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createNetworkOffering", group = "Network Offering", description = "Creates a network offering.", responseObject = NetworkOfferingResponse.class, since = "3.0.0",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateNetworkOfferingCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateNetworkOfferingCmd.class.getName());
    private static final String s_name = "createnetworkofferingresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.DETAILS, type = CommandType.MAP, since = "4.2.0", description = "Network offering details in key/value pairs."
            + " Supported keys are publiclbprovider with service provider as a value")
    protected Map details;
    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the network offering")
    private String networkOfferingName;
    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, required = true, description = "the display text of the network offering")
    private String displayText;
    @Parameter(name = ApiConstants.TRAFFIC_TYPE,
            type = CommandType.STRING,
            required = true,
            description = "the traffic type for the network offering. Supported type in current release is GUEST only")
    private String traffictype;
    @Parameter(name = ApiConstants.TAGS, type = CommandType.STRING, description = "the tags for the network offering.", length = 4096)
    private String tags;
    @Parameter(name = ApiConstants.SPECIFY_VLAN, type = CommandType.BOOLEAN, description = "true if network offering supports vlans")
    private Boolean specifyVlan;
    @Parameter(name = ApiConstants.AVAILABILITY, type = CommandType.STRING, description = "the availability of network offering. Default value is Optional")
    private String availability;
    @Parameter(name = ApiConstants.NETWORKRATE, type = CommandType.INTEGER, description = "data transfer rate in megabits per second allowed")
    private Integer networkRate;
    @Parameter(name = ApiConstants.CONSERVE_MODE, type = CommandType.BOOLEAN, description = "true if the network offering is IP conserve mode enabled")
    private Boolean conserveMode;
    @Parameter(name = ApiConstants.SERVICE_OFFERING_ID,
            type = CommandType.UUID,
            entityType = ServiceOfferingResponse.class,
            description = "the service offering ID used by virtual router provider")
    private Long serviceOfferingId;
    @Parameter(name = ApiConstants.SECONDARY_SERVICE_OFFERING_ID,
            type = CommandType.UUID,
            entityType = ServiceOfferingResponse.class,
            description = "the ID of the service offering for the second VPC router appliance (in case of redundancy)")
    private Long secondaryServiceOfferingId;
    @Parameter(name = ApiConstants.GUEST_IP_TYPE, type = CommandType.STRING, required = true, description = "guest type of the network offering: Shared or Isolated")
    private String guestIptype;
    @Parameter(name = ApiConstants.SUPPORTED_SERVICES,
            type = CommandType.LIST,
            required = true,
            collectionType = CommandType.STRING,
            description = "services supported by the network offering")
    private List<String> supportedServices;
    @Parameter(name = ApiConstants.SERVICE_PROVIDER_LIST,
            type = CommandType.MAP,
            description = "provider to service mapping. If not specified, the provider for the service will be mapped to the default provider on the physical network")
    private Map serviceProviderList;
    @Parameter(name = ApiConstants.SERVICE_CAPABILITY_LIST, type = CommandType.MAP, description = "desired service capabilities as part of network offering")
    private Map serviceCapabilitystList;
    @Parameter(name = ApiConstants.SPECIFY_IP_RANGES,
            type = CommandType.BOOLEAN,
            description = "true if network offering supports specifying ip ranges; defaulted to false if not specified")
    private Boolean specifyIpRanges;
    @Parameter(name = ApiConstants.IS_PERSISTENT,
            type = CommandType.BOOLEAN,
            description = "true if network offering supports persistent networks; defaulted to false if not specified")
    private Boolean isPersistent;
    @Parameter(name = ApiConstants.EGRESS_DEFAULT_POLICY,
            type = CommandType.BOOLEAN,
            description = "true if guest network default egress policy is allow; false if default egress policy is deny")
    private Boolean egressDefaultPolicy;

    @Parameter(name = ApiConstants.KEEPALIVE_ENABLED,
            type = CommandType.BOOLEAN,
            required = false,
            description = "if true keepalive will be turned on in the loadbalancer. At the time of writing this has only an effect on haproxy; the mode http and httpclose " +
                    "options are unset in the haproxy conf file.")
    private Boolean keepAliveEnabled;

    @Parameter(name = ApiConstants.MAX_CONNECTIONS,
            type = CommandType.INTEGER,
            description = "maximum number of concurrent connections supported by the network offering")
    private Integer maxConnections;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public static String getName() {
        return s_name;
    }

    public String getNetworkOfferingName() {
        return networkOfferingName;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getTags() {
        return tags;
    }

    public String getTraffictype() {
        return traffictype;
    }

    public Boolean getSpecifyVlan() {
        return specifyVlan == null ? false : specifyVlan;
    }

    public String getAvailability() {
        return availability == null ? Availability.Optional.toString() : availability;
    }

    public Integer getNetworkRate() {
        return networkRate;
    }

    public Long getServiceOfferingId() {
        return serviceOfferingId;
    }

    public Long getSecondaryServiceOfferingId() {
        return secondaryServiceOfferingId;
    }

    public List<String> getSupportedServices() {
        return supportedServices;
    }

    public String getGuestIpType() {
        return guestIptype;
    }

    public Boolean getSpecifyIpRanges() {
        return specifyIpRanges == null ? false : specifyIpRanges;
    }

    public Boolean getConserveMode() {
        if (conserveMode == null) {
            return true;
        }
        return conserveMode;
    }

    public Boolean getIsPersistent() {
        return isPersistent == null ? false : isPersistent;
    }

    public Boolean getEgressDefaultPolicy() {
        if (egressDefaultPolicy == null) {
            return true;
        }
        return egressDefaultPolicy;
    }

    public Boolean getKeepAliveEnabled() {
        return keepAliveEnabled;
    }

    public Integer getMaxconnections() {
        return maxConnections;
    }

    public Map<String, List<String>> getServiceProviders() {
        Map<String, List<String>> serviceProviderMap = null;
        if (serviceProviderList != null && !serviceProviderList.isEmpty()) {
            serviceProviderMap = new HashMap<>();
            final Collection servicesCollection = serviceProviderList.values();
            final Iterator iter = servicesCollection.iterator();
            while (iter.hasNext()) {
                final HashMap<String, String> services = (HashMap<String, String>) iter.next();
                final String service = services.get("service");
                final String provider = services.get("provider");
                List<String> providerList = null;
                if (serviceProviderMap.containsKey(service)) {
                    providerList = serviceProviderMap.get(service);
                } else {
                    providerList = new ArrayList<>();
                }
                providerList.add(provider);
                serviceProviderMap.put(service, providerList);
            }
        }

        return serviceProviderMap;
    }

    public Map<Capability, String> getServiceCapabilities(final Service service) {
        Map<Capability, String> capabilityMap = null;

        if (serviceCapabilitystList != null && !serviceCapabilitystList.isEmpty()) {
            capabilityMap = new HashMap<>();
            final Collection serviceCapabilityCollection = serviceCapabilitystList.values();
            final Iterator iter = serviceCapabilityCollection.iterator();
            while (iter.hasNext()) {
                final HashMap<String, String> svcCapabilityMap = (HashMap<String, String>) iter.next();
                Capability capability = null;
                final String svc = svcCapabilityMap.get("service");
                final String capabilityName = svcCapabilityMap.get("capabilitytype");
                final String capabilityValue = svcCapabilityMap.get("capabilityvalue");

                if (capabilityName != null) {
                    capability = Capability.getCapability(capabilityName);
                }

                if ((capability == null) || (capabilityName == null) || (capabilityValue == null)) {
                    throw new InvalidParameterValueException("Invalid capability:" + capabilityName + " capability value:" + capabilityValue);
                }

                if (svc.equalsIgnoreCase(service.getName())) {
                    capabilityMap.put(capability, capabilityValue);
                } else {
                    //throw new InvalidParameterValueException("Service is not equal ")
                }
            }
        }

        return capabilityMap;
    }

    public Map<String, String> getDetails() {
        if (details == null || details.isEmpty()) {
            return null;
        }

        final Collection paramsCollection = details.values();
        final Map<String, String> params = (Map<String, String>) (paramsCollection.toArray())[0];
        return params;
    }

    @Override
    public void execute() {
        final NetworkOffering result = _configService.createNetworkOffering(this);
        if (result != null) {
            final NetworkOfferingResponse response = _responseGenerator.createNetworkOfferingResponse(result);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create network offering");
        }
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
