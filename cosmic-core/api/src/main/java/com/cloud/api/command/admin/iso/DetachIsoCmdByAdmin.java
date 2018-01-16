package com.cloud.api.command.admin.iso;

import com.cloud.api.APICommand;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.command.user.iso.DetachIsoCmd;
import com.cloud.api.command.user.vm.DeployVMCmd;
import com.cloud.api.response.UserVmResponse;
import com.cloud.uservm.UserVm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "detachIso", group = "ISO", description = "Detaches any ISO file (if any) currently attached to a virtual machine.", responseObject = UserVmResponse.class, responseView =
        ResponseView.Full,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = true)
public class DetachIsoCmdByAdmin extends DetachIsoCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(DetachIsoCmdByAdmin.class.getName());

    @Override
    public void execute() {
        final boolean result = _templateService.detachIso(virtualMachineId);
        if (result) {
            final UserVm userVm = _entityMgr.findById(UserVm.class, virtualMachineId);
            final UserVmResponse response = _responseGenerator.createUserVmResponse(ResponseView.Full, "virtualmachine", userVm).get(0);
            response.setResponseName(DeployVMCmd.getResultObjectName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to detach iso");
        }
    }
}
