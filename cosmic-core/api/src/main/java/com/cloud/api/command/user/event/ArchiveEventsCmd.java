package com.cloud.api.command.user.event;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.EventResponse;
import com.cloud.api.response.SuccessResponse;
import com.cloud.context.CallContext;
import com.cloud.event.Event;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "archiveEvents", group = "Event", description = "Archive one or more events.", responseObject = SuccessResponse.class, entityType = {Event.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ArchiveEventsCmd extends BaseCmd {

    public static final Logger s_logger = LoggerFactory.getLogger(ArchiveEventsCmd.class.getName());

    private static final String s_name = "archiveeventsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.IDS,
            type = CommandType.LIST,
            collectionType = CommandType.UUID,
            entityType = EventResponse.class,
            description = "the IDs of the events")
    private List<Long> ids;

    @Parameter(name = ApiConstants.END_DATE, type = CommandType.DATE, description = "end date range to archive events"
            + " (including) this date (use format \"yyyy-MM-dd\" or the new format \"yyyy-MM-ddThh:mm:ss\")")
    private Date endDate;

    @Parameter(name = ApiConstants.START_DATE, type = CommandType.DATE, description = "start date range to archive events"
            + " (including) this date (use format \"yyyy-MM-dd\" or the new format \"yyyy-MM-ddThh:mm:ss\")")
    private Date startDate;

    @Parameter(name = ApiConstants.TYPE, type = CommandType.STRING, description = "archive by event type")
    private String type;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public List<Long> getIds() {
        return ids;
    }

    public Date getEndDate() {
        return endDate;
    }

    public Date getStartDate() {
        return startDate;
    }

    public String getType() {
        return type;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        if (ids == null && type == null && endDate == null) {
            throw new InvalidParameterValueException("either ids, type or enddate must be specified");
        } else if (startDate != null && endDate == null) {
            throw new InvalidParameterValueException("enddate must be specified with startdate parameter");
        }
        final boolean result = _mgr.archiveEvents(this);
        if (result) {
            final SuccessResponse response = new SuccessResponse(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Unable to archive Events, one or more parameters has invalid values");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Account account = CallContext.current().getCallingAccount();
        if (account != null) {
            return account.getId();
        }
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
