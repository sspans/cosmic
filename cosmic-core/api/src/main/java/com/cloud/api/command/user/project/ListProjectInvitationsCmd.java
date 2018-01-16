package com.cloud.api.command.user.project;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListAccountResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.ProjectInvitationResponse;
import com.cloud.api.response.ProjectResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listProjectInvitations", group = "Project",
        description = "Lists project invitations and provides detailed information for listed invitations",
        responseObject = ProjectInvitationResponse.class,
        since = "3.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false)
public class ListProjectInvitationsCmd extends BaseListAccountResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListProjectInvitationsCmd.class.getName());
    private static final String s_name = "listprojectinvitationsresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////
    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "list by project id")
    private Long projectId;

    @Parameter(name = ApiConstants.ACTIVE_ONLY,
            type = CommandType.BOOLEAN,
            description = "if true, list only active invitations - having Pending state and ones that are not timed out yet")
    private boolean activeOnly;

    @Parameter(name = ApiConstants.STATE, type = CommandType.STRING, description = "list invitations by state")
    private String state;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = ProjectInvitationResponse.class, description = "list invitations by id")
    private Long id;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////
    public Long getProjectId() {
        return projectId;
    }

    public boolean isActiveOnly() {
        return activeOnly;
    }

    public String getState() {
        return state;
    }

    public Long getId() {
        return id;
    }

    @Override
    public void execute() {
        final ListResponse<ProjectInvitationResponse> response = _queryService.listProjectInvitations(this);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }
}
