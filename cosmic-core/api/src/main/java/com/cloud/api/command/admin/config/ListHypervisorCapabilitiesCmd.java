package com.cloud.api.command.admin.config;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.HypervisorCapabilitiesResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.hypervisor.HypervisorCapabilities;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listHypervisorCapabilities", group = "Hypervisor",
        description = "Lists all hypervisor capabilities.",
        responseObject = HypervisorCapabilitiesResponse.class,
        since = "3.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false)
public class ListHypervisorCapabilitiesCmd extends BaseListCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListHypervisorCapabilitiesCmd.class.getName());

    private static final String s_name = "listhypervisorcapabilitiesresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = HypervisorCapabilitiesResponse.class, description = "ID of the hypervisor capability")
    private Long id;

    @Parameter(name = ApiConstants.HYPERVISOR, type = CommandType.STRING, description = "the hypervisor for which to restrict the search")
    private String hypervisor;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final Pair<List<? extends HypervisorCapabilities>, Integer> hpvCapabilities =
                _mgr.listHypervisorCapabilities(getId(), getHypervisor(), getKeyword(), this.getStartIndex(), this.getPageSizeVal());
        final ListResponse<HypervisorCapabilitiesResponse> response = new ListResponse<>();
        final List<HypervisorCapabilitiesResponse> hpvCapabilitiesResponses = new ArrayList<>();
        for (final HypervisorCapabilities capability : hpvCapabilities.first()) {
            final HypervisorCapabilitiesResponse hpvCapabilityResponse = _responseGenerator.createHypervisorCapabilitiesResponse(capability);
            hpvCapabilityResponse.setObjectName("hypervisorCapabilities");
            hpvCapabilitiesResponses.add(hpvCapabilityResponse);
        }

        response.setResponses(hpvCapabilitiesResponses, hpvCapabilities.second());
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    public Long getId() {
        return id;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public HypervisorType getHypervisor() {
        if (hypervisor != null) {
            return HypervisorType.getType(hypervisor);
        } else {
            return null;
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
