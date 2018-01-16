package com.cloud.api.command.admin.resource;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.CustomCertificateResponse;
import com.cloud.event.EventTypes;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "uploadCustomCertificate", group = "Certificate",
        responseObject = CustomCertificateResponse.class,
        description = "Uploads a custom certificate for the console proxy VMs to use for SSL. Can be used to upload a single certificate signed by a known CA. Can also be used, " +
                "through multiple calls, to upload a chain of certificates from CA to the custom certificate itself.",
        requestHasSensitiveInfo = true, responseHasSensitiveInfo = false)
public class UploadCustomCertificateCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UploadCustomCertificateCmd.class.getName());

    private static final String s_name = "uploadcustomcertificateresponse";

    @Parameter(name = ApiConstants.CERTIFICATE, type = CommandType.STRING, required = true, description = "The certificate to be uploaded.", length = 65535)
    private String certificate;

    @Parameter(name = ApiConstants.ID,
            type = CommandType.INTEGER,
            required = false,
            description = "An integer providing the location in a chain that the certificate will hold. Usually, this can be left empty. When creating a chain, the top level " +
                    "certificate should have an ID of 1, with each step in the chain incrementing by one. Example, CA with id = 1, Intermediate CA with id = 2, Site certificate " +
                    "with ID = 3")
    private Integer index;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = false, description = "A name / alias for the certificate.")
    private String alias;

    @Parameter(name = ApiConstants.PRIVATE_KEY,
            type = CommandType.STRING,
            required = false,
            description = "The private key for the attached certificate.",
            length = 65535)
    private String privateKey;

    @Parameter(name = ApiConstants.DOMAIN_SUFFIX, type = CommandType.STRING, required = true, description = "DNS domain suffix that the certificate is granted for.")
    private String domainSuffix;

    public static String getResultObjectName() {
        return "certificate";
    }

    public String getCertificate() {
        return certificate;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getDomainSuffix() {
        return domainSuffix;
    }

    public Integer getCertIndex() {
        return index;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_UPLOAD_CUSTOM_CERTIFICATE;
    }

    @Override
    public String getEventDescription() {
        return ("Uploading custom certificate to the db, and applying it to all the cpvms in the system");
    }

    @Override
    public void execute() {
        final String result = _mgr.uploadCertificate(this);
        if (result != null) {
            final CustomCertificateResponse response = new CustomCertificateResponse();
            response.setResponseName(getCommandName());
            response.setResultMessage(result);
            response.setObjectName("customcertificate");
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to upload custom certificate");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are tracked
    }
}
