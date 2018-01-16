package com.cloud.api.command.admin.template;

import com.cloud.acl.SecurityChecker.AccessType;
import com.cloud.api.ACL;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.StoragePoolResponse;
import com.cloud.api.response.TemplateResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "prepareTemplate", group = "Template", responseObject = TemplateResponse.class, description = "load template into primary storage", entityType = {VirtualMachineTemplate.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class PrepareTemplateCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(PrepareTemplateCmd.class.getName());

    private static final String s_name = "preparetemplateresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ZONE_ID,
            type = CommandType.UUID,
            entityType = ZoneResponse.class,
            required = true,
            description = "zone ID of the template to be prepared in primary storage(s).")
    private Long zoneId;

    @ACL(accessType = AccessType.OperateEntry)
    @Parameter(name = ApiConstants.TEMPLATE_ID,
            type = CommandType.UUID,
            entityType = TemplateResponse.class,
            required = true,
            description = "template ID of the template to be prepared in primary storage(s).")
    private Long templateId;

    @ACL(accessType = AccessType.OperateEntry)
    @Parameter(name = ApiConstants.STORAGE_ID,
            type = CommandType.UUID,
            entityType = StoragePoolResponse.class,
            required = false,
            description = "storage pool ID of the primary storage pool to which the template should be prepared. If it is not provided the template" +
                    " is prepared on all the available primary storage pools.")
    private Long storageId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getZoneId() {
        return zoneId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public Long getStorageId() {
        return storageId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final ListResponse<TemplateResponse> response = new ListResponse<>();

        final VirtualMachineTemplate vmTemplate = _templateService.prepareTemplate(templateId, zoneId, storageId);
        final List<TemplateResponse> templateResponses = _responseGenerator.createTemplateResponses(ResponseView.Full, vmTemplate, zoneId, true);
        response.setResponses(templateResponses);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
