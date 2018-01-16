package com.cloud.api.command.admin.template;

import com.cloud.api.APICommand;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.command.user.template.CopyTemplateCmd;
import com.cloud.api.response.TemplateResponse;
import com.cloud.context.CallContext;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.template.VirtualMachineTemplate;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "copyTemplate", group = "Template", description = "Copies a template from one zone to another.", responseObject = TemplateResponse.class, responseView = ResponseView.Full,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CopyTemplateCmdByAdmin extends CopyTemplateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CopyTemplateCmdByAdmin.class.getName());

    @Override
    public void execute() throws ResourceAllocationException {
        try {
            CallContext.current().setEventDetails(getEventDescription());
            final VirtualMachineTemplate template = _templateService.copyTemplate(this);

            if (template != null) {
                final List<TemplateResponse> listResponse = _responseGenerator.createTemplateResponses(ResponseView.Full, template, getDestinationZoneId(), false);
                TemplateResponse response = new TemplateResponse();
                if (listResponse != null && !listResponse.isEmpty()) {
                    response = listResponse.get(0);
                }

                response.setResponseName(getCommandName());
                setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to copy template");
            }
        } catch (final StorageUnavailableException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
        }
    }
}

