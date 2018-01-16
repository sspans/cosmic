package com.cloud.api.command.user.resource;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.command.admin.router.UpgradeRouterCmd;
import com.cloud.api.response.HypervisorResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.user.Account;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listHypervisors", group = "Hypervisor", description = "List hypervisors", responseObject = HypervisorResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListHypervisorsCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpgradeRouterCmd.class.getName());
    private static final String s_name = "listhypervisorsresponse";
    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "the zone id for listing hypervisors.")
    private Long zoneId;

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Override
    public void execute() {
        final List<String> result = _mgr.getHypervisors(getZoneId());
        final ListResponse<HypervisorResponse> response = new ListResponse<>();
        final ArrayList<HypervisorResponse> responses = new ArrayList<>();
        if (result != null) {
            for (final String hypervisor : result) {
                final HypervisorResponse hypervisorResponse = new HypervisorResponse();
                hypervisorResponse.setName(hypervisor);
                hypervisorResponse.setObjectName("hypervisor");
                responses.add(hypervisorResponse);
            }
        }
        response.setResponses(responses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////
    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    public Long getZoneId() {
        return this.zoneId;
    }
}
