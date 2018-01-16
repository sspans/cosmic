package com.cloud.api.command.admin.network;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.NetworkOfferingResponse;
import com.cloud.offering.NetworkOffering;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateNetworkOffering", group = "Network Offering", description = "Updates a network offering.", responseObject = NetworkOfferingResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateNetworkOfferingCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateNetworkOfferingCmd.class.getName());
    private static final String s_name = "updatenetworkofferingresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = NetworkOfferingResponse.class, description = "the id of the network offering")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "the name of the network offering")
    private String networkOfferingName;

    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, description = "the display text of the network offering")
    private String displayText;

    @Parameter(name = ApiConstants.AVAILABILITY, type = CommandType.STRING, description = "the availability of network offering."
            + " Default value is Required for Guest Virtual network offering; Optional for Guest Direct network offering")
    private String availability;

    @Parameter(name = ApiConstants.SORT_KEY, type = CommandType.INTEGER, description = "sort key of the network offering, integer")
    private Integer sortKey;

    @Parameter(name = ApiConstants.STATE, type = CommandType.STRING, description = "update state for the network offering")
    private String state;

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

    public String getNetworkOfferingName() {
        return networkOfferingName;
    }

    public String getDisplayText() {
        return displayText;
    }

    public Long getId() {
        return id;
    }

    public String getAvailability() {
        return availability;
    }

    public String getState() {
        return state;
    }

    public Integer getSortKey() {
        return sortKey;
    }

    public Integer getMaxconnections() {
        return maxConnections;
    }

    public Boolean getKeepAliveEnabled() {
        return keepAliveEnabled;
    }

    @Override
    public void execute() {
        final NetworkOffering result = _configService.updateNetworkOffering(this);
        if (result != null) {
            final NetworkOfferingResponse response = _responseGenerator.createNetworkOfferingResponse(result);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update network offering");
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
