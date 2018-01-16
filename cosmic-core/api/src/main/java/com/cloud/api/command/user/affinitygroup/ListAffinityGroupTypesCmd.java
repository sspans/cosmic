package com.cloud.api.command.user.affinitygroup;

import com.cloud.affinity.AffinityGroupTypeResponse;
import com.cloud.api.APICommand;
import com.cloud.api.BaseListCmd;
import com.cloud.api.response.ListResponse;
import com.cloud.user.Account;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listAffinityGroupTypes", group = "Affinity", description = "Lists affinity group types available", responseObject = AffinityGroupTypeResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListAffinityGroupTypesCmd extends BaseListCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListAffinityGroupTypesCmd.class.getName());

    private static final String s_name = "listaffinitygrouptypesresponse";

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() {
        final List<String> result = _affinityGroupService.listAffinityGroupTypes();
        final ListResponse<AffinityGroupTypeResponse> response = new ListResponse<>();
        final ArrayList<AffinityGroupTypeResponse> responses = new ArrayList<>();
        if (result != null) {
            for (final String type : result) {
                final AffinityGroupTypeResponse affinityTypeResponse = new AffinityGroupTypeResponse();
                affinityTypeResponse.setType(type);
                affinityTypeResponse.setObjectName("affinityGroupType");
                responses.add(affinityTypeResponse);
            }
        }
        response.setResponses(responses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
