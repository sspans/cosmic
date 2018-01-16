package com.cloud.api.auth;

import com.cloud.api.APICommand;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.ApiResponseSerializer;
import com.cloud.api.response.LogoutCmdResponse;
import com.cloud.user.Account;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "logout", group = "Authentication", description = "Logs out the user", responseObject = LogoutCmdResponse.class, entityType = {})
public class DefaultLogoutAPIAuthenticatorCmd extends BaseCmd implements APIAuthenticator {

    public static final Logger s_logger = LoggerFactory.getLogger(DefaultLoginAPIAuthenticatorCmd.class.getName());
    private static final String s_name = "logoutresponse";

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ServerApiException {
        // We should never reach here
        throw new ServerApiException(ApiErrorCode.METHOD_NOT_ALLOWED, "This is an authentication api, cannot be used directly");
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_TYPE_NORMAL;
    }

    @Override
    public String authenticate(final String command, final Map<String, Object[]> params, final HttpSession session, final InetAddress remoteAddress, final String responseType,
                               final StringBuilder auditTrailSb,
                               final HttpServletRequest req, final HttpServletResponse resp) throws ServerApiException {
        auditTrailSb.append("=== Logging out ===");
        final LogoutCmdResponse response = new LogoutCmdResponse();
        response.setDescription("success");
        response.setResponseName(getCommandName());
        return ApiResponseSerializer.toSerializedString(response, responseType);
    }

    @Override
    public APIAuthenticationType getAPIType() {
        return APIAuthenticationType.LOGOUT_API;
    }

    @Override
    public void setAuthenticators(final List<PluggableAPIAuthenticator> authenticators) {
    }
}
