package com.cloud.api.command.admin.network;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.GuestVlanRangeResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.PhysicalNetworkResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.network.GuestVlan;
import com.cloud.user.Account;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listDedicatedGuestVlanRanges", group = "VLAN", description = "Lists dedicated guest vlan ranges", responseObject = GuestVlanRangeResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListDedicatedGuestVlanRangesCmd extends BaseListCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListDedicatedGuestVlanRangesCmd.class.getName());

    private static final String s_name = "listdedicatedguestvlanrangesresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = GuestVlanRangeResponse.class, description = "list dedicated guest vlan ranges by id")
    private Long id;

    @Parameter(name = ApiConstants.ACCOUNT,
            type = CommandType.STRING,
            description = "the account with which the guest VLAN range is associated. Must be used with the domainId parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "project who will own the guest VLAN range")
    private Long projectId;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "the domain ID with which the guest VLAN range is associated.  If used with the account parameter, returns all guest VLAN ranges for that account in " +
                    "the specified domain.")
    private Long domainId;

    @Parameter(name = ApiConstants.GUEST_VLAN_RANGE, type = CommandType.STRING, description = "the dedicated guest vlan range")
    private String guestVlanRange;

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID,
            type = CommandType.UUID,
            entityType = PhysicalNetworkResponse.class,
            description = "physical network id of the guest VLAN range")
    private Long physicalNetworkId;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "zone of the guest VLAN range")
    private Long zoneId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public String getAccountName() {
        return accountName;
    }

    public Long getDomainId() {
        return domainId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getGuestVlanRange() {
        return guestVlanRange;
    }

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() {
        final Pair<List<? extends GuestVlan>, Integer> vlans = _networkService.listDedicatedGuestVlanRanges(this);
        final ListResponse<GuestVlanRangeResponse> response = new ListResponse<>();
        final List<GuestVlanRangeResponse> guestVlanResponses = new ArrayList<>();
        for (final GuestVlan vlan : vlans.first()) {
            final GuestVlanRangeResponse guestVlanResponse = _responseGenerator.createDedicatedGuestVlanRangeResponse(vlan);
            guestVlanResponse.setObjectName("dedicatedguestvlanrange");
            guestVlanResponses.add(guestVlanResponse);
        }

        response.setResponses(guestVlanResponses, vlans.second());
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
