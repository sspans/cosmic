package com.cloud.api.command.user.securitygroup;

import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListTaggedResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.SecurityGroupResponse;
import com.cloud.api.response.UserVmResponse;
import com.cloud.network.security.SecurityGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listSecurityGroups", group = "Security Group", description = "Lists security groups", responseObject = SecurityGroupResponse.class, entityType = {SecurityGroup.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListSecurityGroupsCmd extends BaseListTaggedResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListSecurityGroupsCmd.class.getName());

    private static final String s_name = "listsecuritygroupsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.SECURITY_GROUP_NAME, type = CommandType.STRING, description = "lists security groups by name")
    private String securityGroupName;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID,
            type = CommandType.UUID,
            description = "lists security groups by virtual machine id",
            entityType = UserVmResponse.class)
    private Long virtualMachineId;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, description = "list the security group by the id provided", entityType = SecurityGroupResponse.class)
    private Long id;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    public String getSecurityGroupName() {
        return securityGroupName;
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    public Long getId() {
        return id;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final ListResponse<SecurityGroupResponse> response = _queryService.searchForSecurityGroups(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.SecurityGroup;
    }
}
