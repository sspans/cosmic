package com.cloud.api.command.user.template;

import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.ExtractResponse;
import com.cloud.api.response.TemplateResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.InternalErrorException;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "extractTemplate", group = "Template", description = "Extracts a template", responseObject = ExtractResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ExtractTemplateCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ExtractTemplateCmd.class.getName());

    private static final String s_name = "extracttemplateresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = TemplateResponse.class, required = true, description = "the ID of the template")
    private Long id;

    @Parameter(name = ApiConstants.URL, type = CommandType.STRING, required = false, length = 2048, description = "the url to which the ISO would be extracted")
    private String url;

    @Parameter(name = ApiConstants.ZONE_ID,
            type = CommandType.UUID,
            entityType = ZoneResponse.class,
            required = false,
            description = "the ID of the zone where the ISO is originally located")
    private Long zoneId;

    @Parameter(name = ApiConstants.MODE, type = CommandType.STRING, required = true, description = "the mode of extraction - HTTP_DOWNLOAD or FTP_UPLOAD")
    private String mode;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public static String getStaticName() {
        return s_name;
    }

    public String getUrl() {
        return url;
    }

    public String getMode() {
        return mode;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_TEMPLATE_EXTRACT;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getEventDescription() {
        return "extracting template: " + getId() + " from zone: " + getZoneId();
    }

    @Override
    public Long getInstanceId() {
        return getId();
    }

    public Long getId() {
        return id;
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.Template;
    }

    @Override
    public void execute() {
        try {
            CallContext.current().setEventDetails(getEventDescription());
            final String uploadUrl = _templateService.extract(this);
            if (uploadUrl != null) {
                final ExtractResponse response = _responseGenerator.createExtractResponse(id, zoneId, getEntityOwnerId(), mode, uploadUrl);
                response.setResponseName(getCommandName());
                this.setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to extract template");
            }
        } catch (final InternalErrorException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }

    public Long getZoneId() {
        return zoneId;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final VirtualMachineTemplate template = _entityMgr.findById(VirtualMachineTemplate.class, getId());
        if (template != null) {
            return template.getAccountId();
        }

        // invalid id, parent this command to SYSTEM so ERROR events are tracked
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
