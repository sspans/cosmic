package com.cloud.api.command.user.vm;

import com.cloud.acl.SecurityChecker.AccessType;
import com.cloud.api.ACL;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.TemplateResponse;
import com.cloud.api.response.UserVmResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.Account;
import com.cloud.uservm.UserVm;
import com.cloud.vm.VirtualMachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "restoreVirtualMachine", group = "Virtual Machine", description = "Restore a VM to original template/ISO or new template/ISO", responseObject = UserVmResponse.class, since = "3.0.0",
        responseView = ResponseView.Restricted, entityType = {VirtualMachine.class},
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = true)
public class RestoreVMCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(RestoreVMCmd.class);
    private static final String s_name = "restorevmresponse";

    @ACL(accessType = AccessType.OperateEntry)
    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID, type = CommandType.UUID, entityType = UserVmResponse.class,
            required = true, description = "Virtual Machine ID")
    private Long vmId;

    @Parameter(name = ApiConstants.TEMPLATE_ID,
            type = CommandType.UUID,
            entityType = TemplateResponse.class,
            description = "an optional template Id to restore vm from the new template. This can be an ISO id in case of restore vm deployed using ISO")
    private Long templateId;

    @Override
    public String getEventType() {
        return EventTypes.EVENT_VM_RESTORE;
    }

    @Override
    public String getEventDescription() {
        return "Restore a VM to orignal template or specific snapshot";
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
            ResourceAllocationException {
        final UserVm result;
        CallContext.current().setEventDetails("Vm Id: " + getVmId());
        result = _userVmService.restoreVM(this);
        if (result != null) {
            final UserVmResponse response = _responseGenerator.createUserVmResponse(ResponseView.Restricted, "virtualmachine", result).get(0);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to restore vm " + getVmId());
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final UserVm vm = _responseGenerator.findUserVmById(getVmId());
        if (vm == null) {
            return Account.ACCOUNT_ID_SYSTEM; // bad id given, parent this command to SYSTEM so ERROR events are tracked
        }
        return vm.getAccountId();
    }

    public long getVmId() {
        return vmId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    // TODO - Remove vmid param and make it "id" in 5.0 so that we dont have two getters
    public Long getId() {
        return getVmId();
    }
}
